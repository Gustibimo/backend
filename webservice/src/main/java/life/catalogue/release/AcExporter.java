package life.catalogue.release;

import com.google.common.io.Files;
import freemarker.template.*;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Language;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.img.ImgConfig;
import life.catalogue.postgres.PgCopyUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AcExporter {
  private static final Logger LOG = LoggerFactory.getLogger(AcExporter.class);
  private static final String EXPORT_SQL = "/exporter/ac-export.sql";
  private static final String CLEANUP_SQL = "exporter/ac-export-cleanup.sql";
  private static final String COPY_WITH = "CSV HEADER NULL '\\N' DELIMITER E'\\t' QUOTE E'\\f' ENCODING 'UTF8' ";
  private static final Pattern COPY_START = Pattern.compile("^\\s*COPY\\s*\\(");
  private static final Pattern COPY_END   = Pattern.compile("^\\s*\\)\\s*TO\\s*'(.+)'");
  private static final Pattern VAR_DATASET_KEY = Pattern.compile("\\{\\{datasetKey}}", Pattern.CASE_INSENSITIVE);
  private static final Version freemarkerVersion = Configuration.VERSION_2_3_28;
  private static final Configuration fmk = new Configuration(freemarkerVersion);
  static {
    fmk.setClassForTemplateLoading(AcExporter.class, "/exporter");
    // see https://freemarker.apache.org/docs/pgui_quickstart_createconfiguration.html
    fmk.setDefaultEncoding("UTF-8");
    fmk.setDateFormat("yyyy-MM-dd");
    fmk.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    fmk.setLogTemplateExceptions(false);
    fmk.setWrapUncheckedExceptions(true);
    // allow the use of java8 dates
    fmk.setObjectWrapper(new LocalDateObjectWrapper(freemarkerVersion));
  }
  private final WsServerConfig cfg;
  private final SqlSessionFactory factory;
  // we only allow a single export to run at a time
  private static boolean LOCK = false;

  public AcExporter(WsServerConfig cfg, SqlSessionFactory factory) {
    this.cfg = cfg;
    this.factory = factory;
  }
  
  /**
   * @return final archive
   */
  public File export(int catalogueKey) throws IOException, SQLException, IllegalStateException {
    File expDir = new File(cfg.normalizer.scratchDir(catalogueKey), "exports");
    if (!aquireLock()) {
      throw new IllegalStateException("There is a running export already");
    }

    try {
      LOG.info("Export catalogue {} to {}", catalogueKey, expDir.getAbsolutePath());
      // create csv files
      try (PgConnection c = cfg.db.connect()) {
        c.setAutoCommit(false);
        executeSql(c, Resources.toString(CLEANUP_SQL));
        setupTables(c);
        InputStream sql = AcExporter.class.getResourceAsStream(EXPORT_SQL);
        executeAcExportSql(catalogueKey, c, new BufferedReader(new InputStreamReader(sql, StandardCharsets.UTF_8)), expDir);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
      // include images
      exportLogos(catalogueKey, expDir);
  
      // export citation.ini
      exportCitations(catalogueKey, expDir);
      
      // zip up archive in download directory
      File arch = new File(cfg.downloadDir, "export-"+catalogueKey+".zip");
      LOG.info("Zip up archive and move to download");
      if (arch.exists()) {
        LOG.debug("Remove previous export file {}", arch.getAbsolutePath());
      }
      LOG.info("Creating final export archive {}", arch.getAbsolutePath());
      CompressionUtil.zipDir(expDir, arch, true);
      // create sym link to point to latest export
      File symlink = new File(cfg.downloadDir, "ac-export.zip");
      if (symlink.exists()) {
        symlink.delete();
      }
      java.nio.file.Files.createSymbolicLink(symlink.toPath(), arch.toPath());
      return arch;
      
    } finally {
      LOG.info("Clean up temp files");
      LOG.debug("Remove temp export directory {}", expDir.getAbsolutePath());
      FileUtils.deleteQuietly(expDir);
      LOG.debug("Remove temp tables & sequences from postgres");
      LOG.info("Clean up tables & sequences from postgres");
      try (PgConnection c = cfg.db.connect()) {
        c.setAutoCommit(false);
        String sql = Resources.toString(CLEANUP_SQL);
        executeSql(c, sql);
      } finally {
        releaseLock();
      }
      LOG.info("Export completed");
    }
  }

  private static synchronized boolean aquireLock(){
    if (!LOCK) {
      LOCK = true;
      return true;
    }
    return false;
  }

  private static synchronized void releaseLock(){
    LOCK = false;
  }

  private void exportCitations(int catalogueKey, File dir) throws IOException {
    LOG.info("Export citations");
    File cf = new File(dir, "credits.ini");
  
    Map<String, Object> data = new HashMap<>();
  
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(catalogueKey);
      if (d.getReleased()==null) {
        // use today as default release date if missing
        d.setReleased(LocalDate.now());
      }
      data.put("d", d);
      
      Template temp = fmk.getTemplate("credits.ftl");
      Writer out = UTF8IoUtils.writerFromFile(cf);
      temp.process(data, out);
    } catch (TemplateException e) {
      LOG.error("Failed to write credits", e);
      throw new RuntimeException(e);
    }
  }
  
  private void exportLogos(int catalogueKey, File expDir) throws IOException {
    LOG.info("Export logos for sources of catalogue " + catalogueKey);
    File logoDir = new File(expDir, "logos");
    logoDir.mkdir();
  
    int counter = 0;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetSearchRequest req = new DatasetSearchRequest();
      req.setContributesTo(catalogueKey);
      List<Dataset> resp = dm.search(req, null, new Page(0,1000));
      LOG.info("Found " +resp.size()+ " source datasets of catalogue " + catalogueKey);
      for (Dataset d : resp) {
        Path p = cfg.img.datasetLogo(d.getKey(), ImgConfig.Scale.MEDIUM);
        if (java.nio.file.Files.exists(p)) {
          File img =  new File(logoDir, (d.getKey()-1000) + ".png");
          Files.copy(p.toFile(), img);
          
          p = cfg.img.datasetLogo(d.getKey(), ImgConfig.Scale.SMALL);
          img =  new File(logoDir, (d.getKey()-1000) + "-sm.png");
          Files.copy(p.toFile(), img);
          counter++;
          
        } else {
          LOG.warn("Missing logo for dataset {}: {}", d.getKey(), d.getTitle());
          LOG.info("Missing logo for dataset " + d.getTitle());
        }
      }
    }
    LOG.info(counter + " logos exported");
  }
  
  private static void setupTables(Connection c) throws SQLException, IOException {
    try (Statement st = c.createStatement()) {
      st.execute("CREATE TABLE __ranks (key rank PRIMARY KEY, marker TEXT)");
      st.execute("CREATE TABLE __country (code text PRIMARY KEY, title TEXT)");
      st.execute("CREATE TABLE __language (code text PRIMARY KEY, title TEXT)");
    }
    try (PreparedStatement pstR = c.prepareStatement("INSERT INTO __ranks (key, marker) values (?::rank, ?)");
         PreparedStatement pstC = c.prepareStatement("INSERT INTO __country (code, title) values (?, ?)");
         PreparedStatement pstL = c.prepareStatement("INSERT INTO __language (code, title) values (?, ?)")
    ) {
      for (Rank r : Rank.values()) {
        // exclude infrasp., see https://github.com/Sp2000/colplus-backend/issues/478
        if (r.isUncomparable()) continue;
        pstR.setString(1, r.name());
        pstR.setString(2, r.getMarker());
        pstR.execute();
      }
      for (Country cn : Country.values()) {
        pstC.setString(1, cn.getIso2LetterCode());
        pstC.setString(2, cn.getTitle());
        pstC.execute();

      }
      for (Language l : Language.values()) {
        // exclude infrasp., see https://github.com/Sp2000/colplus-backend/issues/478
        pstL.setString(1, l.getCode());
        pstL.setString(2, l.getTitle());
        pstL.execute();
      }
      c.commit();
    }
  }
  
  /**
   * @return directory with all CSV dump files
   */
  private void executeAcExportSql(int datasetKey, PgConnection con, BufferedReader sql, File csvDir) throws IOException, SQLException {
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = sql.readLine()) != null) {
      Matcher m = COPY_END.matcher(line);
      if (COPY_START.matcher(line).find()) {
        executeSql(con, sb.toString());
        sb = new StringBuilder();
      
      } else if (m.find()) {
        // copy to file
        File f = new File(csvDir, m.group(1).trim());
        Files.createParentDirs(f);
        LOG.info("Exporting " + f.getName());
        LOG.info("Exporting {}", f.getAbsolutePath());
        PgCopyUtils.dump(con, sb.toString(), f, COPY_WITH);
        sb = new StringBuilder();
      
      } else {
        if (sb.length() > 0) {
          sb.append("\n");
        }
        // substitute datasetKey variable
        sb.append(VAR_DATASET_KEY.matcher(line).replaceAll(String.valueOf(datasetKey)));
      }
    }
    if (sb.length() > 0) {
      executeSql(con, sb.toString());
    }
    con.commit();
  }
  
  private void executeSql(PgConnection con, String sql) throws SQLException, IOException {
    try (Statement stmnt = con.createStatement()) {
      if (sql.startsWith("--")) {
        if (sql.contains("\n")) {
          LOG.info(StringUtils.capitalize(sql.substring(3, sql.indexOf('\n'))));
        }
      } else if (sql.contains(" ")){
        LOG.info("Execute " + sql.substring(0, sql.indexOf(' ')) + " SQL");
      }
      stmnt.execute(sql);
      con.commit();
    }
  }
}

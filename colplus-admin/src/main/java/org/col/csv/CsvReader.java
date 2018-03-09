package org.col.csv;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.univocity.parsers.common.IterableResult;
import com.univocity.parsers.common.ParsingContext;
import com.univocity.parsers.common.ResultIterator;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.col.api.model.TermRecord;
import org.col.api.vocab.VocabularyUtils;
import org.col.util.io.CharsetDetectingStream;
import org.col.util.io.PathUtils;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A reader giving access to a set of delimited text files in a folder
 * by offering verbatim values as standard TermRecords.
 *
 * It forms the basis for reading both DWC and ACEF files.
 */
public class CsvReader {
  private static final Logger LOG = LoggerFactory.getLogger(CsvReader.class);
  protected static final CsvParserSettings CSV = new CsvParserSettings();
  static {
    CSV.detectFormatAutomatically();
    // try with tabs as default if autoconfig fails
    CSV.getFormat().setDelimiter('\t');
    CSV.setSkipEmptyLines(true);
    CSV.trimValues(true);
    CSV.setReadInputOnSeparateThread(false);
    CSV.setNullValue(null);
    CSV.setMaxColumns(256);
    CSV.setMaxCharsPerColumn(1024*16);
  }
  private static final Set<String> SUFFICES = ImmutableSet.of("csv", "tsv", "tab", "txt", "text");
  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL)\\s*$");
  private static final int STREAM_CHARACTERISTICS = Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.IMMUTABLE;

  private static final Joiner LINE_JOIN = Joiner.on('\n');

  protected final Path folder;
  protected final Map<Term, Schema> schemas = Maps.newHashMap();
  private Character[] delimiterCandidates = {'\t', ',', ';', '|'};
  private Character[] quoteCandidates     = {'"', '\''};

  /**
   * @param folder
   */
  protected CsvReader(Path folder, String termPrefix) throws IOException {
    if (!Files.isDirectory(folder)) {
      throw new FileNotFoundException("Folder does not exist: " + folder);
    }
    this.folder = folder;
    discoverSchemas(termPrefix);
  }

  /**
   *
   * @param termPrefix optional preferred term namespace prefix to use when looking up class & property terms
   * @throws IOException
   */
  protected void discoverSchemas(String termPrefix) throws IOException {
    for (Path df : listDataFiles(folder)) {
      putSchema(buildSchema(df, termPrefix));
    }
  }

  protected void putSchema(Schema s) {
    if (s != null) {
      schemas.put(s.rowType, s);
    }
  }

  /**
   * Returns a path within the folder for a given relative file or path.
   * @param filename to resolve
   */
  protected Path resolve(String filename) {
    return folder.resolve(filename);
  }

  /**
   * @param termPrefix optional preferred term namespace prefix to use when looking up class & property terms
   */
  public static CsvReader from(Path folder, String termPrefix) throws IOException {
    return new CsvReader(folder, termPrefix);
  }

  public static CsvReader from(Path folder) throws IOException {
    return from(folder, null);
  }

  protected void require(Term rowType, Term term) {
    if (hasData(rowType) && !hasData(rowType, term)) {
      Schema s = schemas.remove(rowType);
      LOG.warn("Required term {} missing. Ignore file {}!", term, s.file);
    }
  }

  private static Optional<Term> findTerm(String termPrefix, String name, boolean isClassTerm) {
    if (termPrefix != null && !name.contains(":")) {
      name = termPrefix + ":" + name;
    }
    try {
      return Optional.of(VocabularyUtils.TF.findTerm(name, isClassTerm));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Detects the used CSV format by trying all combinations of delimiter and quote
   * and selecting the one with the most columns in a consistent manner
   */
  private CsvParserSettings discoverFormat(List<String> lines) {
    CsvParserSettings best = null;
    int maxCols = 0;
    for (char del : delimiterCandidates) {
      for (char quote : quoteCandidates) {
        CsvParserSettings cfg = CSV.clone();
        cfg.getFormat().setDelimiter(del);
        cfg.setDelimiterDetectionEnabled(false);
        cfg.getFormat().setQuote(quote);
        cfg.setQuoteDetectionEnabled(false);

        CsvParser parser = new CsvParser(cfg);
        int cols = 0;
        for (String[] row : parser.parseAll(new StringReader(LINE_JOIN.join(lines)))) {
          if (isAllNull(row)) continue;

          if (cols == 0) {
            cols = row.length;
          } else if (cols != row.length) {
            // inconsistent column number, stop this one
            cols = -1;
            break;
          }
        }
        if (cols > maxCols) {
          best = cfg;
          maxCols = cols;
        }
      }
    }
    if (best == null) {
      // finally use univocitys autodetection if nothing works
      best = CSV.clone();
      best.setDelimiterDetectionEnabled(true);
      best.setQuoteDetectionEnabled(true);
    }
    return best;
  }

  private Schema buildSchema(Path df, @Nullable String termPrefix) {
    LOG.debug("Detecting schema for file {}", PathUtils.getFilename(df));
    try {
      try (CharsetDetectingStream in = CharsetDetectingStream.create(Files.newInputStream(df))){
        final Charset charset = in.getCharset();
        LOG.debug("Use encoding {} for file {}", charset, PathUtils.getFilename(df));

        List<String> lines = Lists.newArrayList();
        BufferedReader br = CharsetDetectingStream.createReader(in, in.getCharset());
        String line;
        while ((line = br.readLine()) != null && lines.size() < 20) {
          lines.add(line);
        }
        br.close();

        if (lines.isEmpty()) {
          LOG.warn("{} contains no data", PathUtils.getFilename(df));

        } else {
          CsvParserSettings set = discoverFormat(lines);

          CsvParser parser = new CsvParser(set);
          parser.beginParsing(new StringReader(LINE_JOIN.join(lines)));
          String[] header = parser.parseNext();
          parser.stopParsing();

          if (header == null) {
            LOG.warn("{} contains no data", PathUtils.getFilename(df));

          } else {
            List<Schema.Field> columns = Lists.newArrayList();
            int unknownCounter = 0;
            for (String col : header) {
              Optional<Term> termOpt = findTerm(termPrefix, col, false);
              if (termOpt.isPresent()) {
                Term term = termOpt.get();
                columns.add(new Schema.Field(term, columns.size()));
                if (term instanceof UnknownTerm) {
                  unknownCounter++;
                  LOG.debug("Unknown Term {} found in file {}", term.qualifiedName(), PathUtils.getFilename(df));
                }
              } else {
                LOG.debug("Illegal term {} found in file {}", col, PathUtils.getFilename(df));
              }
            }
            if (columns.isEmpty()) {
              LOG.warn("No terms found in header");
              return null;
            }

            int unknownPerc = unknownCounter*100 / columns.size();
            if (unknownPerc > 80 ) {
              LOG.warn("{} percent unknown terms found as header", unknownPerc);
            }
            // ignore header row - needs changed if we parse the settings externally
            set.setNumberOfRowsToSkip(1);

            // we create a tmp dummy schema with wrong rowType for convenience to find the real rowType - it will not survive
            final Optional<Term> rowType = detectRowType(new Schema(df, DwcTerm.Taxon, charset, set, columns), termPrefix);
            if (rowType.isPresent()) {
              LOG.info("CSV {} schema with {} columns found for {} encoded file {}", rowType.get().prefixedName(), columns.size(), charset, PathUtils.getFilename(df));
              return new Schema(df, rowType.get(), charset, set, columns);
            }
            LOG.warn("Failed to identify row type for {}", PathUtils.getFilename(df));
          }
        }
      }


    } catch (RuntimeException | IOException e) {
      LOG.error("Failed to read schema for {}", PathUtils.getFilename(df), e);
    }
    return null;
  }

  protected Optional<Term> detectRowType(Schema schema, String termPrefix) {
    return findTerm(termPrefix, PathUtils.getBasename(schema.file), true);
  }

  private static Iterable<Path> listDataFiles(Path folder) throws IOException {
    return Files.newDirectoryStream(folder, new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path p) throws IOException {
        return Files.isRegularFile(p) && SUFFICES.contains(PathUtils.getFileExtension(p));
      }
    });
  }

  public Set<Term> rowTypes() {
    return schemas.keySet();
  }

  public Collection<Schema> schemas() {
    return schemas.values();
  }

  public boolean hasData(Term rowType) {
    return schemas.containsKey(rowType);
  }

  public boolean hasData(Term rowType, Term term) {
    return schemas.containsKey(rowType) && schemas.get(rowType).hasTerm(term);
  }

  /**
   * @return number of available schemas
   */
  public int size() {
    return schemas.size();
  }

  /**
   * @return true if no schema is mapped
   */
  public boolean isEmpty() {
    return schemas.isEmpty();
  }

  public Optional<Schema> schema(Term rowType) {
    return Optional.ofNullable(schemas.get(rowType));
  }

  public Stream<TermRecord> stream(Term rowType) {
    Preconditions.checkArgument(rowType.isClass(), "RowType "+rowType+" is not a class term");
    if (schemas.containsKey(rowType)) {
      return stream(schemas.get(rowType));
    } else {
      return Stream.empty();
    }
  }

  /**
   * Returns the first content row of the given data file, skipping any header if existing.
   */
  public Optional<TermRecord> readFirstRow(AcefTerm rowType) {
    if (schemas.containsKey(rowType)) {
      return stream(schemas.get(rowType)).findFirst();
    }
    return Optional.empty();
  }

  private class TermRecIterator implements Iterator<TermRecord> {
    private final ResultIterator<String[], ParsingContext> iter;
    private final Schema s;
    private final int maxIdx;
    private final String filename;
    private String[] row;

    TermRecIterator(Schema schema) throws IOException {
      s = schema;
      filename = PathUtils.getFilename(schema.file);
      maxIdx = schema.columns.stream().map(f->f.index).filter(Objects::nonNull).reduce(Integer::max).orElse(0);
      CsvParser parser = new CsvParser(schema.settings);

      IterableResult<String[], ParsingContext> it = parser.iterate(
          CharsetDetectingStream.createReader(Files.newInputStream(schema.file), schema.encoding)
      );
      this.iter = it.iterator();
      nextRow();
    }

    @Override
    public boolean hasNext() {
      return row != null;
    }

    private void nextRow() {
      if (iter.hasNext()) {
        while (iter.hasNext() && isEmpty(row = iter.next(), true));
        // if the last rows were empty we would get the last non empty row again, clear it in that case!
        if (!iter.hasNext() && isEmpty(row, false)) {
          row = null;
        }
      } else {
        row = null;
      }
    }

    private boolean isEmpty(String[] row, boolean log) {
      if (row == null) {
        // ignore this row, dont log
      } else if (row.length < maxIdx+1) {
        if (log) LOG.info("{} skip line {} with too few columns (found {}, expected {})", filename, iter.getContext().currentLine(), row.length, maxIdx+1);
      } else if (isAllNull(row)) {
        if (log) LOG.debug("{} skip line {} with only empty columns", filename, iter.getContext().currentLine());
      } else {
        return false;
      }
      return true;
    }

    @Override
    public TermRecord next() {
      TermRecord tr = new TermRecord(iter.getContext().currentLine()-1, filename, s.rowType);
      for (Schema.Field f : s.columns) {
        if (f != null) {
          String val = null;
          if (f.index != null) {
            if (!Strings.isNullOrEmpty(row[f.index])) {
              val = clean(row[f.index]);
            }
          }
          // use default?
          if (val == null && f.value != null) {
            val = f.value;
          }
          tr.put(f.term, val);
        }
      }
      // load next non empty row
      nextRow();
      return tr;
    }
  }

  private Stream<TermRecord> stream(final Schema s) {
    try {
      return StreamSupport.stream(
          Spliterators.spliteratorUnknownSize(new TermRecIterator(s), STREAM_CHARACTERISTICS), false);

    } catch (IOException | RuntimeException e) {
      LOG.error("Failed to read {}", s.file, e);
      return Stream.empty();
    }
  }

  public static String clean(String x) {
    if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
      return null;
    }
    return Strings.emptyToNull(CharMatcher.javaIsoControl().trimAndCollapseFrom(x, ' ').trim());
  }

  private static boolean isAllNull(String[] row) {
    for (String x : row) {
      if (x != null) return false;
    }
    return true;
  }
}
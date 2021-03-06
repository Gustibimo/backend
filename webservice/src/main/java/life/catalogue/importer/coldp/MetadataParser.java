package life.catalogue.importer.coldp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.api.jackson.FastutilsSerde;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.License;
import life.catalogue.importer.jackson.EnumParserSerde;
import life.catalogue.parser.LicenseParser;
import org.gbif.dwc.terms.TermFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class MetadataParser {
  private static final Logger LOG = LoggerFactory.getLogger(MetadataParser.class);
  private static final List<String> METADATA_FILENAMES = ImmutableList.of("metadata.yaml", "metadata.yml");
  private static final ObjectReader DATASET_READER;
  private static final ObjectMapper OM;
  static {
    OM = new ObjectMapper(new YAMLFactory())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(new JavaTimeModule())
        .registerModule(new ColdpYamlModule());
    DATASET_READER = OM.readerFor(DatasetWithSettings.class);
    
    TermFactory.instance().registerTerm(ColdpInserter.BIBTEX_CLASS_TERM);
    TermFactory.instance().registerTerm(ColdpInserter.CSLJSON_CLASS_TERM);
  }
  private static  class ColdpYamlModule extends SimpleModule {
    public ColdpYamlModule() {
      super("ColdpYaml");
      EnumParserSerde<License> lserde = new EnumParserSerde<License>(LicenseParser.PARSER);
      addDeserializer(License.class, lserde.new Deserializer());
      addDeserializer(IntSet.class, new FastutilsSerde.SetDeserializer());
    }
    
    @Override
    public void setupModule(SetupContext ctxt) {
      // default enum serde
      ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
      ctxt.addDeserializers(new PermissiveEnumSerde.PermissiveEnumDeserializers());
      super.setupModule(ctxt);
    }
  }

  /**
   * Reads the dataset metadata.yaml or metadata.yml from a given folder
   */
  public static Optional<DatasetWithSettings> readMetadata(Path dir) {
    for (String fn : METADATA_FILENAMES) {
      Path metapath = dir.resolve(fn);
      if (Files.exists(metapath)) {
        try {
          return readMetadata(Files.newInputStream(metapath));
        } catch (IOException e) {
          LOG.error("Error reading " + fn, e);
        }
      }
    }
    return Optional.empty();
  }
  
  public static Optional<DatasetWithSettings> readMetadata(InputStream stream) {
    if (stream != null) {
      try {
        DatasetWithSettings d = DATASET_READER.readValue(stream);
        d.setDataFormat(DataFormat.COLDP);
        if (d.getDescription() != null) {
          d.setDescription(d.getDescription().trim());
        }
        // TODO: transform contact ORCIDSs
        return Optional.of(d);
        
      } catch (IOException e) {
        LOG.error("Error reading metadata", e);
      }
    }
    return Optional.empty();
  }

}

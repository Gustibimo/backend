package life.catalogue.dw.jersey.writers;

import com.google.common.base.Throwables;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dw.jersey.MoreMediaTypes;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.stream.Stream;

@Produces({MoreMediaTypes.TEXT_CSV})
@Provider
public class CsvBodyWriter implements MessageBodyWriter<Stream<Object[]>> {
  private static final CsvWriterSettings SETTINGS = new CsvWriterSettings();

  @Override
  public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
    return Stream.class.isAssignableFrom(type);
  }
  
  @Override
  public void writeTo(Stream<Object[]> rows, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
    CsvWriter csv = new CsvWriter(UTF8IoUtils.writerFromStream(out), SETTINGS);
    try {
      rows.forEach(row -> {
        try {
          csv.writeRow(row);
        } catch(Exception e) {
          Throwables.throwIfUnchecked(e);
          throw new RuntimeException(e);
        }
      });
    } finally {
      csv.close();
    }
  }
}
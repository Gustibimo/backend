package life.catalogue.es;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Misc {

  @Test // Apparently String.split does a trim-right, but not a trim-left
  public void testSplit() {
    System.out.println("X: " + (1 << 30));
    String s = "ABCD     ";
    String[] ss = s.split("\\W");
    assertEquals(1, ss.length);
    s = "     ABCD";
    ss = s.split("\\W");
    assertTrue(ss.length > 1);
  }

  @Test
  public void testEscapeJson() {
    String value = "Hello, \"World\"";
    String json = "{\"message\": " + EsModule.escape(value) + "}";
    System.out.println(json);
    assertEquals("{\"message\": \"Hello, \\\"World\\\"\"}", json);
  }

}

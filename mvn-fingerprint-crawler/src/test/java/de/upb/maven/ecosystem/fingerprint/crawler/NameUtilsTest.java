package de.upb.maven.ecosystem.fingerprint.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.upb.maven.ecosystem.fingerprint.crawler.process.NameUtils;
import org.junit.jupiter.api.Test;

public class NameUtilsTest {

  @Test
  public void sanitizeFilename() {

    String illegatName = "java/myFile";

    String sanitied = NameUtils.sanitizeFilename(illegatName);

    assertEquals("java_myFile", sanitied);
  }

  @Test
  public void className() {}

  @Test
  public void testToFileName() {
    String fqnName = "java.lang.Object";
    String s = NameUtils.toFileName(fqnName);
    assertEquals("java/lang/Object.class", s);
  }

  @Test
  public void toFileName2() {
    String fqnName = "java.lang.Object$Ma";
    String s = NameUtils.toFileName(fqnName);
    assertEquals("java/lang/Object$Ma.class", s);
  }

  @Test
  public void toFileName3() {
    String fqnName = "java.lang.Object.myMethod()";
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          String s = NameUtils.toFileName(fqnName);
        });
  }
}

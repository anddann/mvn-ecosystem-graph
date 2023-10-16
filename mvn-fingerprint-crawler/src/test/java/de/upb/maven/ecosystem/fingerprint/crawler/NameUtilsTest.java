package de.upb.maven.ecosystem.fingerprint.crawler;

import static org.junit.Assert.assertEquals;

import de.upb.maven.ecosystem.fingerprint.crawler.process.NameUtils;
import org.junit.Test;

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

  @Test(expected = IllegalArgumentException.class)
  public void toFileName3() {
    String fqnName = "java.lang.Object.myMethod()";
    String s = NameUtils.toFileName(fqnName);
  }
}

package de.upb.maven.ecosystem.crawler;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;
import org.junit.Test;

public class PomFileUtilTest {

  @Test
  public void testReadPom() {
    Path pomfile = Paths.get("src", "test", "resources", "commons-compress-1.9.pom");
    final MavenProject mavenProject = PomFileUtil.readPom(pomfile);
    Assert.assertNotNull(mavenProject);
    System.out.println(mavenProject);
  }
}

package de.upb.maven.ecosystem.fingerprint.crawler;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.fingerprint.crawler.process.ArtifactProcessor;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Gav;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.License;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
import org.junit.Test;

public class LicenseProcessorTest {

  @Test
  public void codacyPlugins() throws IOException {
    ArtifactProcessor artifactProcessor = new ArtifactProcessor(false, 5000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("com.codacy");
    artifactInfo.setArtifactId("codacy-plugins_2.11");
    artifactInfo.setArtifactVersion("12.0.7_play_2.6");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    //
    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    // Comes in via pom.xml
    // https://repo1.maven.org/maven2/com/codacy/codacy-plugins_2.11/12.0.7_play_2.6/codacy-plugins_2.11-12.0.7_play_2.6.pom
    assertNotNull(process);
    assertTrue(containsLicenseByName(process.getGav().getLicences(), "Apache 2"));
  }

  @Test
  public void spring() throws IOException {
    ArtifactProcessor artifactProcessor = new ArtifactProcessor(false, 5000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("org.springframework");
    artifactInfo.setArtifactId("spring-core");
    artifactInfo.setArtifactVersion("5.2.5.RELEASE");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    assertNotNull(process);
    assertTrue(
        containsLicenseByName(process.getGav().getLicences(), "Apache License, Version 2.0"));
  }

  @Test
  public void rebundledTest() throws IOException {
    ArtifactProcessor artifactProcessor = new ArtifactProcessor(false, 5000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("org.ops4j.pax.web");
    artifactInfo.setArtifactId("pax-web-jsp");
    artifactInfo.setArtifactVersion("7.3.6");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    //
    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    assertNotNull(process);
    // Comes in via pom.xml
    // https://repo1.maven.org/maven2/com/codacy/codacy-plugins_2.11/12.0.7_play_2.6/codacy-plugins_2.11-12.0.7_play_2.6.pom
    assertTrue(
        containsEmbeddedLicenseByName(process.getEmbeddedGavs(), "CDDL or GPLv2 with exceptions"));
  }

  private boolean containsEmbeddedLicenseByName(Set<Gav> gav, String s) {
    for (Gav g : gav) {
      if (containsLicenseByName(g.getLicences(), s)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsLicenseByName(Set<License> licences, String s) {
    for (License l : licences) {
      if (l.getName().equals(s)) {
        return true;
      }
    }
    return false;
  }
}

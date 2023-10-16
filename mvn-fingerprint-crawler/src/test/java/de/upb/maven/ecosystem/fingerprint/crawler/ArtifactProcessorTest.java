package de.upb.maven.ecosystem.fingerprint.crawler;

import static org.junit.Assert.*;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.fingerprint.crawler.process.ArtifactProcessor;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.ClassFile;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Test;

public class ArtifactProcessorTest {

  @Test
  public void process() throws IOException {
    ArtifactProcessor artifactProcessor = new ArtifactProcessor(true, 5 * 60 * 1000);

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
    Stopwatch stopwatch = Stopwatch.createStarted();

    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    stopwatch.stop();
    System.out.println("Elapsed: " + stopwatch.elapsed(TimeUnit.SECONDS));
    HashSet<String> foundSHA = new HashSet<>();
    boolean duplicateExistis = false;
    assertNotNull(process);
    assertEquals(470, process.getClassFile().size());
    for (ClassFile classFile : process.getClassFile()) {
      // the plain compuation contains duplicate
      duplicateExistis = duplicateExistis | foundSHA.contains(classFile.getSha256());
      foundSHA.add(classFile.getSha256());
    }

    assertTrue(duplicateExistis);
    assertNotNull(process.getDirectDependencies());
    assertFalse(process.getDirectDependencies().isEmpty());
  }

  @Test
  public void processLog4j() throws IOException {
    ArtifactProcessor artifactProcessor = new ArtifactProcessor(true, 5 * 60 * 1000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("org.apache.logging.log4j");
    artifactInfo.setArtifactId("log4j-core");
    artifactInfo.setArtifactVersion("2.13.2");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    //
    Stopwatch stopwatch = Stopwatch.createStarted();

    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    stopwatch.stop();
    System.out.println("Elapsed: " + stopwatch.elapsed(TimeUnit.SECONDS));
    HashSet<String> foundSHA = new HashSet<>();
    boolean duplicateExistis = false;
    assertNotNull(process);
    assertEquals(1118, process.getClassFile().size());

    assertNotNull(process.getDirectDependencies());
    assertFalse(process.getDirectDependencies().isEmpty());
  }

  @Test
  @Ignore
  public void takesForeverInDocker() throws IOException {
    // https://repo1.maven.org/maven2/uk/ac/open/kmi/iserve/iserve-integrated-engine/2.1.0/iserve-integrated-engine-2.1.0-jar-with-dependencies.jar
    ArtifactProcessor artifactProcessor = new ArtifactProcessor(true, 5 * 60 * 1000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("uk.ac.open.kmi.iserve");
    artifactInfo.setArtifactId("iserve-integrated-engine");
    artifactInfo.setArtifactVersion("2.0.1");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setClassifier("jar-with-dependencies");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    Stopwatch stopwatch = Stopwatch.createStarted();

    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    stopwatch.stop();
    System.out.println("Elapsed: " + stopwatch.elapsed(TimeUnit.SECONDS));
  }

  @Test
  public void licenseSearchTakesForeverInDocker() throws IOException {
    //   Artifact#0 at url
    // https://repo1.maven.org/maven2/com/arpnetworking/metrics/metrics-portal_2.11/0.4.7/metrics-portal_2.11-0.4.7-assets.jar
    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setGroupId("com.arpnetworking.metrics");
    artifactInfo.setArtifactId("metrics-portal_2.11");
    artifactInfo.setArtifactVersion("0.4.7");
    artifactInfo.setClassifier("assets");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    Stopwatch stopwatch = Stopwatch.createStarted();
    ArtifactProcessor artifactProcessor = new ArtifactProcessor(true, 5 * 60 * 1000);

    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    stopwatch.stop();
    System.out.println("Elapsed: " + stopwatch.elapsed(TimeUnit.SECONDS));
  }

  @Test
  public void testGeneralCrawler() throws IOException {

    ArtifactProcessor artifactProcessor = new ArtifactProcessor(true, 5 * 60 * 1000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("com.fasterxml.jackson.core");
    artifactInfo.setArtifactId("jackson-core");
    artifactInfo.setArtifactVersion("2.10.1");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    //
    Stopwatch stopwatch = Stopwatch.createStarted();

    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    stopwatch.stop();
    System.out.println("Elapsed: " + stopwatch.elapsed(TimeUnit.SECONDS));
    assertNotNull(process);
    assertNotNull(process.getClassFile());
    assertEquals(118, process.getClassFile().size());

    Collection<String> tlsh =
        process.getClassFile().stream()
            .map(ClassFile::getTlsh)
            .filter(x -> (x != null && !x.isEmpty()))
            .collect(Collectors.toSet());
    System.out.println("Computed tlhs: " + tlsh.size());

    assertTrue(tlsh.size() >= 108);
    assertTrue(tlsh.size() <= 118);
  }

  @Test
  @Ignore // licnesne takes foreover
  public void testRandArtefat() throws IOException {
    // https://repo1.maven.org/maven2/com/arpnetworking/metrics/metrics-portal_2.11/0.4.7/
    ArtifactProcessor artifactProcessor = new ArtifactProcessor(true, 5 * 60 * 1000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("com.arpnetworking.metrics");
    artifactInfo.setArtifactId("metrics-portal_2.11");
    artifactInfo.setArtifactVersion("0.4.7");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setClassifier("assets");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    //
    Stopwatch stopwatch = Stopwatch.createStarted();

    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    stopwatch.stop();
    System.out.println("Elapsed: " + stopwatch.elapsed(TimeUnit.SECONDS));
  }

  @Test
  @Ignore
  public void testBigArtefact() throws IOException {
    // https://repo1.maven.org/maven2/com/amazonaws/aws-java-sdk-osgi/1.11.732/aws-java-sdk-osgi-1.11.732.jar
    ArtifactProcessor artifactProcessor = new ArtifactProcessor(true, 5 * 60 * 1000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("com.amazonaws");
    artifactInfo.setArtifactId("aws-java-sdk-osgi");
    artifactInfo.setArtifactVersion("1.11.732");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    //
    Stopwatch stopwatch = Stopwatch.createStarted();

    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    stopwatch.stop();
    System.out.println("Elapsed: " + stopwatch.elapsed(TimeUnit.SECONDS));
  }

  @Test
  @Ignore
  public void heapSpaceArtifact() throws IOException {

    ArtifactProcessor artifactProcessor = new ArtifactProcessor(true, 5 * 60 * 1000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("com.wix.openrest");
    artifactInfo.setArtifactId("openrest4j-api");
    artifactInfo.setArtifactVersion("1.10.0");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    //
    Stopwatch stopwatch = Stopwatch.createStarted();

    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);
    stopwatch.stop();
    System.out.println("Elapsed: " + stopwatch.elapsed(TimeUnit.SECONDS));
  }
}

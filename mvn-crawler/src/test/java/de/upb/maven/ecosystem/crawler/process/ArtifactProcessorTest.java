package de.upb.maven.ecosystem.crawler.process;

import static org.junit.Assert.*;

import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.xml.sax.SAXException;

public class ArtifactProcessorTest extends ArtifactProcessorAbstract {

  /**
   * Version of <artifactId>junit-jupiter</artifactId> missing
   *
   * <p>* 08:43:56.332 [pool-1-thread-1] ERROR d.u.m.e.r.Redis2Neo4JDB - Failed to persist *
   * MvnArtifactNode(resolvingLevel=FULL, crawlerVersion=0.5.2, group=com.9ls, artifact=common-util,
   * * version=1.0.6, repoURL=https://repo1.maven.org/maven2/, scmURL=null, classifier=null, *
   * packaging=jar, properties={}),
   *
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   */
  @Test
  @Ignore
  public void test1() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("com.9ls");
    artifactInfo.setArtifactId("common-util");
    artifactInfo.setArtifactVersion("1.0.6");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

    assertNotNull(process);
    assertFalse(process.isEmpty());
    assertEquals(1, process.size());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  /**
   * * 08:43:56.417 [pool-1-thread-1] ERROR d.u.m.e.r.Redis2Neo4JDB - Failed to persist *
   * MvnArtifactNode(resolvingLevel=FULL, crawlerVersion=0.5.2, group=com.4paradigm.openmldb, *
   * artifact=openmldb-spark-connector, version=0.4.3, repoURL=https://repo1.maven.org/maven2/, *
   * scmURL=null, classifier=null, packaging=jar, properties={scala.binary.version=2.12, *
   * spark.dependencyScope=provided, maven.compiler.target=1.8, spark.version=3.0.0, *
   * java.version=1.8, maven.compiler.source=1.8, project.build.sourceEncoding=UTF-8, *
   * default.project.version=0.3.0, scala.version=2.12.8, encoding=UTF-8}),
   *
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   */
  @Test
  public void test2() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("com.4paradigm.openmldb");
    artifactInfo.setArtifactId("openmldb-spark-connector");
    artifactInfo.setArtifactVersion("0.4.3");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

    assertNotNull(process);
    assertFalse(process.isEmpty());
    assertEquals(2, process.size());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  /**
   * 08:43:52.367 [pool-1-thread-1] ERROR d.u.m.e.r.Redis2Neo4JDB - Failed to persist *
   * MvnArtifactNode(resolvingLevel=FULL, crawlerVersion=0.5.2, *
   * group=com.alibaba.lindorm.thirdparty, artifact=avatica-server, version=1.13.4, *
   * repoURL=https://repo1.maven.org/maven2/, scmURL=null, classifier=null, packaging=jar, *
   * properties={top.dir=${project.basedir}/..}), 08:43:52.366 [pool-1-thread-1] ERROR *
   * d.u.m.e.r.Redis2Neo4JDB - Failed to persist MvnArtifactNode(resolvingLevel=FULL, *
   * crawlerVersion=0.5.2, group=com.alibaba.lindorm.thirdparty, artifact=avatica-server, *
   * version=1.1.10, repoURL=https://repo1.maven.org/maven2/, scmURL=null, classifier=null, *
   * packaging=jar, properties={top.dir=${project.basedir}/..})
   *
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   */
  @Test
  public void test3() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("com.alibaba.lindorm.thirdparty");
    artifactInfo.setArtifactId("avatica-server");
    artifactInfo.setArtifactVersion("1.13.4");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

    assertNotNull(process);
    assertFalse(process.isEmpty());
    assertEquals(3, process.size());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  public void testProcess() throws IOException, ParserConfigurationException, SAXException {

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("io.atlasmap");
    artifactInfo.setArtifactId("atlas-csv-service");
    artifactInfo.setArtifactVersion("2.2.0-M.3");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

    assertNotNull(process);
    assertFalse(process.isEmpty());
    assertEquals(5, process.size());
    final List<MvnArtifactNode> collect = new ArrayList<>(process);

    final MvnArtifactNode mvnArtifactNode = collect.get(0);
    assertEquals("io.atlasmap", mvnArtifactNode.getGroup());
    assertEquals("atlas-csv-service", mvnArtifactNode.getArtifact());
    assertEquals("2.2.0-M.3", mvnArtifactNode.getVersion());
    assertTrue(mvnArtifactNode.getParent().isPresent());
    assertEquals(12, mvnArtifactNode.getDependencies().size());
    assertEquals(1, mvnArtifactNode.getProperties().size());

    testDependencies(mvnArtifactNode);

    final MvnArtifactNode p1 = collect.get(1);
    assertEquals("io.atlasmap", p1.getGroup());
    assertEquals("atlas-csv-parent", p1.getArtifact());
    assertEquals("2.2.0-M.3", p1.getVersion());
    assertTrue(p1.getParent().isPresent());
    assertEquals(0, p1.getDependencies().size());
    assertEquals(0, p1.getProperties().size());

    testDependencies(p1);

    final MvnArtifactNode p2 = collect.get(2);
    assertEquals("io.atlasmap", p2.getGroup());
    assertEquals("atlasmap-lib", p2.getArtifact());
    assertEquals("2.2.0-M.3", p2.getVersion());
    assertTrue(p2.getParent().isPresent());
    assertEquals(0, p2.getDependencies().size());
    assertEquals(0, p2.getProperties().size());

    testDependencies(p2);

    final MvnArtifactNode p3 = collect.get(3);
    assertEquals("io.atlasmap", p3.getGroup());
    assertEquals("atlas-parent", p3.getArtifact());
    assertEquals("2.2.0-M.3", p3.getVersion());
    assertTrue(p3.getParent().isPresent());
    assertEquals(0, p3.getDependencies().size());
    assertEquals(85, p3.getDependencyManagement().size());
    assertEquals(56, p3.getProperties().size());

    testDependencies(p3);

    final MvnArtifactNode p4 = collect.get(4);
    assertEquals("io.atlasmap", p4.getGroup());
    assertEquals("atlasmapio", p4.getArtifact());
    assertEquals("2.2.0-M.3", p4.getVersion());
    assertEquals(0, p4.getDependencies().size());
    assertFalse(p4.getParent().isPresent());
    assertEquals(15, p4.getProperties().size());
    testSerialize(process);

    testDependencies(p3);

    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  public void testRecursiveReferences()
      throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("org.apache.wicket");
    artifactInfo.setArtifactId("wicket-objectsizeof-agent");
    artifactInfo.setArtifactVersion("1.5.9");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

    assertNotNull(process);
    assertFalse(process.isEmpty());
    assertEquals(3, process.size());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }

    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  public void testCassandraFeedback()
      throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("org.apache.cassandra");
    artifactInfo.setArtifactId("cassandra-clientutil");
    artifactInfo.setArtifactVersion("1.2.0-beta2");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

    assertNotNull(process);
    assertFalse(process.isEmpty());
    assertEquals(2, process.size());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
  }

  @Test
  public void testCircularReferences()
      throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("org.apache.cassandra");
    artifactInfo.setArtifactId("cassandra-thrift");
    artifactInfo.setArtifactVersion("1.2.0-beta2");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

    assertNotNull(process);
    assertFalse(process.isEmpty());
    assertEquals(2, process.size());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  public void testFailedPoms() throws IOException, ParserConfigurationException, SAXException {

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    // write the node with circular reference first into the DB
    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("io.lighty.core");
    artifactInfo.setArtifactId("lighty-controller-spring-di");
    artifactInfo.setArtifactVersion("12.1.0");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

    assertNotNull(process);
    assertFalse(process.isEmpty());
    assertEquals(51, process.size());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  public void versionBlankError() throws IOException, ParserConfigurationException, SAXException {

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("org.duracloud");
    artifactInfo.setArtifactId("duraboss");
    artifactInfo.setArtifactVersion("3.5.0");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    assertNotNull(process);
    assertFalse(process.isEmpty());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  public void versionPropertiesError2()
      throws IOException, ParserConfigurationException, SAXException {
    // failed on database?
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("org.apache-extras.camel-extra");
    artifactInfo.setArtifactId("camel-esper");
    artifactInfo.setArtifactVersion("2.10.0");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
  }

  @Test
  public void versionBlankError2() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    //  add the parent before to the database, then the test will fail..
    // if the parent is fetched from the database

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    {
      // write the node with circular reference first into the DB
      ArtifactProcessor artifactProcessor =
          new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

      CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
      artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
      artifactInfo.setGroupId("org.apache.syncope");
      artifactInfo.setArtifactId("syncope");
      artifactInfo.setArtifactVersion("1.0.3-incubating");
      artifactInfo.setFileExtension("jar");
      artifactInfo.setPackaging("jar");

      final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

      assertNotNull(process);
      assertFalse(process.isEmpty());
      assertEquals(2, process.size());
      testSerialize(process);

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
      }

      for (MvnArtifactNode node : process) {
        testDependencies(node);
      }
    }

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("org.apache.syncope");
    artifactInfo.setArtifactId("syncope-console");
    artifactInfo.setArtifactVersion("1.0.3-incubating");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
  }

  @Test
  public void propertiesUnresolved()
      throws IOException, ParserConfigurationException, SAXException {

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("org.eclipse.jetty.cdi");
    artifactInfo.setArtifactId("cdi-core");
    artifactInfo.setArtifactVersion("9.3.5.v20151012");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  public void testWithDBAccess() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    {
      // write the node with circular reference first into the DB
      ArtifactProcessor artifactProcessor =
          new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

      CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
      artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
      artifactInfo.setGroupId("org.apache.cassandra");
      artifactInfo.setArtifactId("cassandra-thrift");
      artifactInfo.setArtifactVersion("1.2.0-beta2");
      artifactInfo.setFileExtension("jar");
      artifactInfo.setPackaging("jar");

      final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

      assertNotNull(process);
      assertFalse(process.isEmpty());
      assertEquals(2, process.size());
      testSerialize(process);

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
      }

      for (MvnArtifactNode node : process) {
        testDependencies(node);
      }
    }

    // resolve the next one
    {
      // resolve a node that references the recursive node
      ArtifactProcessor artifactProcessor =
          new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

      CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
      artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
      artifactInfo.setGroupId("org.apache.cassandra");
      artifactInfo.setArtifactId("cassandra-clientutil");
      artifactInfo.setArtifactVersion("1.2.0-beta2");
      artifactInfo.setFileExtension("jar");
      artifactInfo.setPackaging("jar");

      final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
      assertNotNull(process);
      assertFalse(process.isEmpty());
      assertEquals(1, process.size());
      testSerialize(process);

      for (MvnArtifactNode node : process) {
        testDependencies(node);
      }
    }
  }

  @Test
  public void testParentParent() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();
    // Created duplicate parent edge

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    {
      // write the node with circular reference first into the DB
      ArtifactProcessor artifactProcessor =
          new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

      CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
      artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
      artifactInfo.setGroupId("org.fcrepo");
      artifactInfo.setArtifactId("fcrepo4-oaiprovider");
      artifactInfo.setArtifactVersion("4.4.0");
      artifactInfo.setFileExtension("jar");
      artifactInfo.setPackaging("jar");

      final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

      assertNotNull(process);
      assertFalse(process.isEmpty());
      assertEquals(6, process.size());
      testSerialize(process);

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
      }

      for (MvnArtifactNode node : process) {
        testDependencies(node);
      }
    }

    // resolve the next one
    {
      // resolve a node that references the recursive node
      ArtifactProcessor artifactProcessor =
          new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

      CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
      artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
      artifactInfo.setGroupId("org.fcrepo");
      artifactInfo.setArtifactId("fcrepo-webapp");
      artifactInfo.setArtifactVersion("4.4.0");
      artifactInfo.setFileExtension("jar");
      artifactInfo.setPackaging("pom");

      final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
      assertNotNull(process);
      assertFalse(process.isEmpty());
      assertEquals(2, process.size());
      testSerialize(process);

      for (MvnArtifactNode node : process) {
        testDependencies(node);
      }
    }
  }

  @Test
  public void testNotSingleRecord() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();
    // Created duplicate parent edge
    // org.fcrepo:fcrepo:4.4.0

    // org.fcrepo:fcrepo-integration-ldp:4.4.0
    // org.fcrepo:fcrepo-http-commons:4.4.0
    // org.fcrepo:fcrepo-http-api:4.4.0

    // org.fcrepo:fcrepo-connector-file:4.4.0
    // org.fcrepo:fcrepo-client-impl:4.4.0
    // org.fcrepo:fcrepo-client:4.4.0
    // org.fcrepo:fcrepo-build-tools:4.4.0
    //
    ArrayList<String> artifacts = new ArrayList<>();
    artifacts.add("fcrepo-integration-ldp");

    artifacts.add("fcrepo-integration-ldp");
    artifacts.add("fcrepo-http-commons");
    artifacts.add("fcrepo-http-api");
    artifacts.add("fcrepo-client-impl");
    artifacts.add("fcrepo-client");
    artifacts.add("fcrepo-build-tools");

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    {
      // write the node with circular reference first into the DB
      ArtifactProcessor artifactProcessor =
          new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");
      CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

      artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
      artifactInfo.setGroupId("org.fcrepo"); // org.fcrepo:fcrepo-auth-roles-common:4.4.0

      artifactInfo.setArtifactId("fcrepo-jcr-bom");

      artifactInfo.setArtifactVersion("4.4.0");
      artifactInfo.setFileExtension("jar");
      artifactInfo.setPackaging("pom");

      final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

      assertNotNull(process);
      assertFalse(process.isEmpty());
      // assertEquals(6, process.size());
      testSerialize(process);

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
      }

      for (MvnArtifactNode node : process) {
        testDependencies(node);
      }
    }

    // resolve the next one
    {
      // resolve a node that references the recursive node
      ArtifactProcessor artifactProcessor =
          new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

      CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
      artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
      artifactInfo.setGroupId("org.fcrepo"); // org.fcrepo:fcrepo-auth-roles-basic:4.4.0
      artifactInfo.setArtifactId("fcrepo-auth-roles-basic");
      artifactInfo.setArtifactVersion("4.4.0");
      artifactInfo.setFileExtension("jar");
      artifactInfo.setPackaging("jar");

      final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
      assertNotNull(process);
      assertFalse(process.isEmpty());
      //  assertEquals(3, process.size());
      testSerialize(process);

      for (MvnArtifactNode node : process) {
        testDependencies(node);
      }

      for (MvnArtifactNode node : process) {
        DoaMvnArtifactNodeImpl.sanityCheck(node);
      }
    }
  }

  @Test
  public void failedArtifact() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    {
      // write the node with circular reference first into the DB
      ArtifactProcessor artifactProcessor =
          new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

      CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
      artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
      artifactInfo.setGroupId("org.mobicents.protocols.ss7.mtp");
      artifactInfo.setArtifactId("mtp");
      artifactInfo.setArtifactVersion("2.0.0.BETA3");
      artifactInfo.setFileExtension("jar");
      artifactInfo.setPackaging("jar");

      final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

      assertNotNull(process);
      assertFalse(process.isEmpty());
      assertEquals(6, process.size());

      testSerialize(process);

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
      }

      for (MvnArtifactNode node : process) {
        testDependencies(node);
      }
    }
  }

  private String[] splitString(String logOutput) {
    // get rid of everyting after
    final String gav = StringUtils.substring(logOutput, 0, logOutput.indexOf("-null --"));
    return gav.split(":");
  }

  @Test
  // project.parent property
  public void testPropertiesNotResolved()
      throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    // logoutput
    // org.ops4j.pax.web.samples:authentication:2.1.2-null -- Properties not resolved. Invalid State
    String[] gav =
        splitString(
            "org.ops4j.pax.web.samples:authentication:2.1.2-null -- Properties not resolved. Invalid State");

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId(gav[0]);
    artifactInfo.setArtifactId(gav[1]);
    artifactInfo.setArtifactVersion(gav[2]);
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    assertNotNull(process);
    assertFalse(process.isEmpty());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
  }

  @Test
  @Ignore
  public void testFailedArtifactsFromFile() throws IOException {
    Driver driver = createDriver();
    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    Path path = Paths.get("../logs_2022_01_21/failed_artifacts.txt");
    ArrayList<String> newFileLines = new ArrayList<>();
    HashSet<String> seenGroups = new HashSet<>();

    try {
      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      for (String failedGav : lines) {

        String[] gav = splitString(failedGav);
        if (gav.length != 3) {
          System.out.println("not long enough");
          continue;
        }

        ArtifactProcessor artifactProcessor =
            new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

        CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
        artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
        artifactInfo.setGroupId(gav[0]);
        artifactInfo.setArtifactId(gav[1]);
        artifactInfo.setArtifactVersion(gav[2]);
        artifactInfo.setFileExtension("jar");
        artifactInfo.setPackaging("jar");

        if (seenGroups.contains(gav[0])) {
          continue;
        }
        seenGroups.add(gav[0]);

        try {

          final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
        } catch (Exception ex) {
          newFileLines.add(failedGav + " --  " + ex.getMessage());
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    Files.write(
        Paths.get("../logs_2022_01_21/failed_artifacts_new.txt"),
        newFileLines,
        Charset.defaultCharset());
  }

  public void testPropertiesFail() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    // logoutput

    String[] gav = splitString("io.apiman:apiman-test-policies:1.2.0.Beta2-null");

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId(gav[0]);
    artifactInfo.setArtifactId(gav[1]);
    artifactInfo.setArtifactVersion(gav[2]);
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    assertNotNull(process);
    assertFalse(process.isEmpty());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  @Ignore // a property in the dependency management section cannot be resolved ! Unresolvable!!
  public void testPropertiesUnresolvable()
      throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    // logoutput

    String[] gav =
        splitString(
            "org.sakaiproject.delegatedaccess:delegatedaccess-pack:2.1-null -- Properties not resolved. Invalid State");

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId(gav[0]);
    artifactInfo.setArtifactId(gav[1]);
    artifactInfo.setArtifactVersion(gav[2]);
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    assertNotNull(process);
    assertFalse(process.isEmpty());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
  }

  @Test
  public void testPropertiesNotResolved3()
      throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    // logoutput

    String[] gav =
        splitString("net.vvakame:blazdb-sqlite:0.2-null -- Properties not resolved. Invalid State");

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId(gav[0]);
    artifactInfo.setArtifactId(gav[1]);
    artifactInfo.setArtifactVersion(gav[2]);
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    assertNotNull(process);
    assertFalse(process.isEmpty());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
  }

  @Test
  public void testProfileProperties()
      throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    // logoutput

    String[] gav =
        splitString(
            "de.hilling.junit.cdi:cdi-test-jee:0.10.2-null -- Invalid State. Unresolved Property: javax.enterprise:cdi-api:${cdi-api.version}\n");

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId(gav[0]);
    artifactInfo.setArtifactId(gav[1]);
    artifactInfo.setArtifactVersion(gav[2]);
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    assertNotNull(process);
    // TODO add more semantic checks
    assertFalse(process.isEmpty());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
  }

  @Test // the artifact is itself its parent
  public void cycleParentItself() throws IOException {
    //
    // 10:03:51.997 [pool-1-thread-34] INFO  d.u.m.e.c.p.ArtifactManager - Processing Artifact#0 at
    // com.microsoft.msr.malmo:MalmoJavaJar:0.30.0

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("com.microsoft.msr.malmo");
    artifactInfo.setArtifactId("MalmoJavaJar");
    artifactInfo.setArtifactVersion("0.30.0");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    assertNotNull(process);
    // TODO add more semantic checks
    assertFalse(process.isEmpty());
    testSerialize(process);
  }

  @Test
  @Ignore // the artifact has a strange dependency to a pom, that is also in the dependency mgmt
  // section as an import?
  public void freeze() throws IOException {
    //
    // 10:03:51.997 [pool-1-thread-34] INFO  d.u.m.e.c.p.ArtifactManager - Processing Artifact#0 at
    // com.microsoft.msr.malmo:MalmoJavaJar:0.30.0

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("br.com.c8tech.releng");
    artifactInfo.setArtifactId("fpom-itests-paxexam");
    artifactInfo.setArtifactVersion("7.0");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    assertNotNull(process);
    // TODO add more semantic checks
    assertFalse(process.isEmpty());
    testSerialize(process);
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }
}

package de.upb.maven.ecosystem.crawler.process;

import static org.junit.Assert.*;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import de.upb.maven.ecosystem.persistence.redis.RedisSerializerUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.io.fs.FileUtils;
import org.neo4j.kernel.configuration.BoltConnector;
import org.neo4j.kernel.configuration.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtifactProcessorTest {

  public static final String LISTEN_ADDRESS = "localhost:7687";
  public static final String CREDENTIAL = "neo4j";
  private static final boolean runEmbedded = true;
  private static final Logger logger = LoggerFactory.getLogger(ArtifactProcessorTest.class);
  private static Path databasePath;
  private static GraphDatabaseService databaseService;

  @Rule public TestName currentTestName = new TestName();

  @BeforeClass
  public static void setupTestDb() throws IOException {
    if (runEmbedded) {
      Stopwatch sw = Stopwatch.createStarted();
      databasePath = Files.createTempDirectory(CREDENTIAL);
      databaseService = createDB();
      logger.info("Started Neo4j Test instance after {}", sw);
    }
  }

  @AfterClass
  public static void shutdown() throws IOException {
    if (databaseService != null) {
      databaseService.shutdown();
      FileUtils.deleteRecursively(databasePath.toFile());
    }
  }

  private static GraphDatabaseService createDB() {

    logger.info("Creating dbms in {}", databasePath);

    BoltConnector bolt = new BoltConnector("0");

    GraphDatabaseService graphDb =
        new GraphDatabaseFactory()
            .newEmbeddedDatabaseBuilder(databasePath.toFile())
            .setConfig(GraphDatabaseSettings.pagecache_memory, "512M")
            .setConfig(GraphDatabaseSettings.string_block_size, "60")
            .setConfig(GraphDatabaseSettings.array_block_size, "300")
            .setConfig(bolt.enabled, Settings.TRUE)
            .setConfig(bolt.type, "BOLT")
            .setConfig(bolt.listen_address, LISTEN_ADDRESS)
            .newGraphDatabase();

    // Registers a shutdown hook for the Neo4j instance so that it
    // shuts down nicely when the VM exits (even if you "Ctrl-C" the
    // running application).
    Runtime.getRuntime().addShutdownHook(new Thread(() -> graphDb.shutdown()));

    return graphDb;
  }

  @Before
  public void clearDb() {
    try (Session session = createDriver().session()) {
      try (Transaction tx = session.beginTransaction()) {
        Query clear = new Query("MATCH (n) DETACH DELETE (n)");
        tx.run(clear);
        tx.commit();
      }
    }
  }

  private Driver createDriver() {
    return GraphDatabase.driver(
        "bolt://" + LISTEN_ADDRESS, AuthTokens.basic(CREDENTIAL, CREDENTIAL));
  }

  @Test
  public void testProcess() throws IOException {

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

    // TODO test properties

    final MvnArtifactNode mvnArtifactNode = collect.get(0);
    assertEquals("io.atlasmap", mvnArtifactNode.getGroup());
    assertEquals("atlas-csv-service", mvnArtifactNode.getArtifact());
    assertEquals("2.2.0-M.3", mvnArtifactNode.getVersion());
    assertTrue(mvnArtifactNode.getParent().isPresent());
    assertEquals(12, mvnArtifactNode.getDependencies().size());
    assertEquals(1, mvnArtifactNode.getProperties().size());

    final MvnArtifactNode p1 = collect.get(1);
    assertEquals("io.atlasmap", p1.getGroup());
    assertEquals("atlas-csv-parent", p1.getArtifact());
    assertEquals("2.2.0-M.3", p1.getVersion());
    assertTrue(p1.getParent().isPresent());
    assertEquals(0, p1.getDependencies().size());
    assertEquals(0, p1.getProperties().size());

    final MvnArtifactNode p2 = collect.get(2);
    assertEquals("io.atlasmap", p2.getGroup());
    assertEquals("atlasmap-lib", p2.getArtifact());
    assertEquals("2.2.0-M.3", p2.getVersion());
    assertTrue(p2.getParent().isPresent());
    assertEquals(0, p2.getDependencies().size());
    assertEquals(0, p2.getProperties().size());

    final MvnArtifactNode p3 = collect.get(3);
    assertEquals("io.atlasmap", p3.getGroup());
    assertEquals("atlas-parent", p3.getArtifact());
    assertEquals("2.2.0-M.3", p3.getVersion());
    assertTrue(p3.getParent().isPresent());
    assertEquals(0, p3.getDependencies().size());
    assertEquals(85, p3.getDependencyManagement().size());
    assertEquals(56, p3.getProperties().size());

    final MvnArtifactNode p4 = collect.get(4);
    assertEquals("io.atlasmap", p4.getGroup());
    assertEquals("atlasmapio", p4.getArtifact());
    assertEquals("2.2.0-M.3", p4.getVersion());
    assertEquals(0, p4.getDependencies().size());
    assertFalse(p4.getParent().isPresent());
    assertEquals(15, p4.getProperties().size());
  }

  @Test
  public void testRecurisiveReferences() throws IOException {
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
  }

  @Test
  public void testCassandraFeedback() throws IOException {
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
  }

  @Test
  public void testCircularReferences() throws IOException {
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
  }

  @Test
  public void testFailedPoms() throws IOException {

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
    assertEquals(52, process.size());
  }

  @Test
  public void versionBlankError() throws IOException {

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
  }

  @Test
  public void versionPropertiesError2() throws IOException {
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
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  public void versionBlankError2() throws IOException {
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

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
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
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }

  @Test
  public void propertiesUnresolved() throws IOException {

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
  }

  @Test
  public void testWithDBAccess() throws IOException {
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

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
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
    }
  }

  @Test
  public void testParentParent() throws IOException {
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

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
        RedisSerializerUtil.serialize(node);
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
    }
  }

  @Test
  public void testNotSingleRecord() throws IOException {
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

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
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
      for (MvnArtifactNode node : process) {
        RedisSerializerUtil.serialize(node);
      }
    }
  }

  @Test
  public void failedArtifact() throws IOException {
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

      for (MvnArtifactNode node : process) {
        doaMvnArtifactNodeImpl.saveOrMerge(node);
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
  public void testPropertiesNotResolved() throws IOException {
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
  }

  @Test
  @Ignore // a property in the dependency management section cannot be resolved ! Unresolvable!!
  public void testPropertiesNotResolved2() throws IOException {
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
  }

  @Test
  public void testPropertiesNotResolved3() throws IOException {
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
  }
}

package de.upb.maven.ecosystem.crawler.process;

import static org.junit.Assert.*;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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

  private static GraphDatabaseService createDB() throws IOException {

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
    final List<MvnArtifactNode> collect = process.stream().collect(Collectors.toList());

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
}

package de.upb.maven.ecosystem.persistence;

import static org.junit.Assert.*;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.dao.MvnArtifactNodeProxy;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import de.upb.maven.ecosystem.persistence.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
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

public class DoaMvnArtifactNodeImplTest {

  private static final boolean runEmbedded = true;
  private static final Logger logger = LoggerFactory.getLogger(DoaMvnArtifactNodeImplTest.class);

  public static final String LISTEN_ADDRESS = "localhost:7687";
  public static final String CREDENTIAL = "neo4j";

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

  private Driver createDriver() {
    return GraphDatabase.driver(
        "bolt://" + LISTEN_ADDRESS, AuthTokens.basic(CREDENTIAL, CREDENTIAL));
  }

  @Test
  public void saveOrMerge() {
    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setArtifact("a");
    mvnArtifactNode.setGroup("g");
    mvnArtifactNode.setVersion("1.0");

    MvnArtifactNode parent = new MvnArtifactNode();
    parent.setArtifact("parent");
    parent.setGroup("g");
    parent.setVersion("1.0");

    parent.setPackaging("pom");

    mvnArtifactNode.setParent(Optional.of(parent));

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);
  }

  @Test
  public void saveOrMergeSingleNode() {
    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setArtifact("a");
    mvnArtifactNode.setGroup("g");
    mvnArtifactNode.setVersion("1.0");

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);
  }

  @Test
  public void saveDependency() {

    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setArtifact("a");
    mvnArtifactNode.setGroup("g");
    mvnArtifactNode.setVersion("1.0");

    MvnArtifactNode dep = new MvnArtifactNode();
    dep.setArtifact("depA");
    dep.setGroup("depG");
    dep.setVersion("1.0");
    dep.setPackaging("jar");
    DependencyRelation dependencyRelation = new DependencyRelation();
    dependencyRelation.setPosition(0);
    dependencyRelation.setTgtNode(dep);

    mvnArtifactNode.getDependencies().add(dependencyRelation);

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);
  }

  @Test
  public void saveDependencyMgmt() {

    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setArtifact("a");
    mvnArtifactNode.setGroup("g");
    mvnArtifactNode.setVersion("1.0");

    MvnArtifactNode dep = new MvnArtifactNode();
    dep.setArtifact("depA");
    dep.setGroup("depG");
    dep.setVersion("1.0");
    dep.setPackaging("jar");
    DependencyRelation dependencyRelation = new DependencyRelation();
    dependencyRelation.setPosition(0);
    dependencyRelation.setTgtNode(dep);

    mvnArtifactNode.getDependencyManagement().add(dependencyRelation);

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);
  }

  @Test
  public void saveAndGetSingleNode() {
    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setArtifact("a");
    mvnArtifactNode.setGroup("g");
    mvnArtifactNode.setVersion("1.0");

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);

    // get the node
    final Optional<MvnArtifactNode> mvnArtifactNode1 = doaMvnArtifactNodeImpl.get(mvnArtifactNode);
    assertNotNull(mvnArtifactNode1);
    assertTrue(mvnArtifactNode1.isPresent());
  }

  @Test
  public void jsonSerializerTest() {

    // String properties_json -> {tychoVersion=0.20.0, emfVersion=2.10.1}
    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setArtifact("a");
    mvnArtifactNode.setGroup("g");
    mvnArtifactNode.setVersion("1.0");

    HashMap<String, String> properties = new HashMap<>();
    properties.put("tychoVersion", "0.20.0");
    properties.put("emfVersion", "2.10.1");
    mvnArtifactNode.setProperties(properties);

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);

    // get the node
    final Optional<MvnArtifactNode> mvnArtifactNode1 = doaMvnArtifactNodeImpl.get(mvnArtifactNode);
    assertNotNull(mvnArtifactNode1);
    assertTrue(mvnArtifactNode1.isPresent());
    assertNotNull(mvnArtifactNode1.get().getProperties());

    assertEquals(2, mvnArtifactNode1.get().getProperties().size());
  }

  @Test
  public void jsonSerializerTestRelationShip() {

    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setArtifact("a");
    mvnArtifactNode.setGroup("g");
    mvnArtifactNode.setVersion("1.0");

    // the dependency
    MvnArtifactNode depNode = new MvnArtifactNode();
    depNode.setArtifact("da");
    depNode.setGroup("dg");
    depNode.setVersion("2.0");

    // the relation
    DependencyRelation dependencyRelation = new DependencyRelation();
    dependencyRelation.setTgtNode(depNode);
    mvnArtifactNode.getDependencies().add(dependencyRelation);

    List<String> exclusions = new ArrayList<>();
    exclusions.add("a:a");
    exclusions.add("b:b");

    dependencyRelation.setExclusions(exclusions);

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);

    // get the node and the relationship

    final Optional<DependencyRelation> mvnArtifactNode1 =
        doaMvnArtifactNodeImpl.getRelationship(mvnArtifactNode, depNode);
    assertNotNull(mvnArtifactNode1);
    assertTrue(mvnArtifactNode1.isPresent());

    assertNotNull(mvnArtifactNode1.get());
    assertNotNull(mvnArtifactNode1.get().getExclusions());
    assertEquals(2, mvnArtifactNode1.get().getExclusions().size());
  }

  @Test
  public void testProxObject() {

    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setArtifact("a");
    mvnArtifactNode.setGroup("g");
    mvnArtifactNode.setVersion("1.0");

    // the dependency
    MvnArtifactNode depNode = new MvnArtifactNode();
    depNode.setArtifact("da");
    depNode.setGroup("dg");
    depNode.setVersion("2.0");

    // the relation
    DependencyRelation dependencyRelation = new DependencyRelation();
    dependencyRelation.setTgtNode(depNode);
    dependencyRelation.setPosition(0);
    mvnArtifactNode.getDependencies().add(dependencyRelation);

    List<String> exclusions = new ArrayList<>();
    exclusions.add("a:a");
    exclusions.add("b:b");

    dependencyRelation.setExclusions(exclusions);

    MvnArtifactNode depNode2 = new MvnArtifactNode();
    depNode2.setArtifact("da2");
    depNode2.setGroup("dg2");
    depNode2.setVersion("4.0");

    // the relation
    DependencyRelation dependencyRelation2 = new DependencyRelation();
    dependencyRelation2.setTgtNode(depNode2);
    dependencyRelation2.setPosition(1);

    mvnArtifactNode.getDependencies().add(dependencyRelation2);

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);

    // get the node and the relationship

    final Optional<MvnArtifactNode> mvnArtifactNode1 = doaMvnArtifactNodeImpl.get(mvnArtifactNode);
    assertNotNull(mvnArtifactNode1);
    assertTrue(mvnArtifactNode1.isPresent());
    final MvnArtifactNode proxyNode = mvnArtifactNode1.get();
    assertTrue(proxyNode instanceof MvnArtifactNodeProxy);

    final List<DependencyRelation> dependencies = proxyNode.getDependencies();
    assertNotNull(dependencies);
    assertEquals(2, dependencies.size());
  }

  @Test
  public void containsNodeWithVersionGQ() {

    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setArtifact("a");
    mvnArtifactNode.setGroup("g");
    mvnArtifactNode.setVersion("1.0");

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);

    final boolean b =
        doaMvnArtifactNodeImpl.containsNodeWithVersionGQ(
            "g",
            "a",
            "1.0",
            null,
            mvnArtifactNode.getPackaging(),
            Neo4JConnector.getCrawlerVersion());
    assertTrue(b);
  }

  // TODO getParent Test

  // TODO get DepMgmt Test
}

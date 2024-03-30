package de.upb.maven.ecosystem.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.base.Optional;
import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.persistence.graph.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.graph.dao.MvnArtifactNodeProxy;
import de.upb.maven.ecosystem.persistence.graph.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import de.upb.maven.ecosystem.persistence.redis.RedisSerializerUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DoaMvnArtifactNodeImplTest {

  public static final String LISTEN_ADDRESS = "localhost:7687";
  public static final String CREDENTIAL = "neo4j";
  private static final Logger logger = LoggerFactory.getLogger(DoaMvnArtifactNodeImplTest.class);

  private static Neo4j embeddedDatabaseServer;

  @BeforeAll
  static void initializeNeo4j() {

    embeddedDatabaseServer =
        Neo4jBuilders.newInProcessBuilder()
            // Don't need Neos HTTP server
            .build();
  }

  @AfterAll
  static void stopNeo4j() {
    embeddedDatabaseServer.close();
  }

  @BeforeEach
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
    RedisSerializerUtil.serialize(mvnArtifactNode);
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

    RedisSerializerUtil.serialize(mvnArtifactNode);
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
    mvnArtifactNode.setResolvingLevel(MvnArtifactNode.ResolvingLevel.FULL);

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    doaMvnArtifactNodeImpl.saveOrMerge(mvnArtifactNode);

    final boolean b =
        doaMvnArtifactNodeImpl.containsNodeWithVersionGQ(
            "g", "a", "1.0", null, AbstractCrawler.getCrawlerVersion());
    assertTrue(b);
  }

  // TODO getParent Test

  // TODO get DepMgmt Test

  @Test
  public void getGraphTest() {
    String query =
        "MATCH (n:MvnArtifact)-[r:DEPENDS_ON]->(m:MvnArtifact) where m.group=\"org.jeesl\" and m.artifact=\"jeesl-test\" and m.version=\"0.2.9\" and m.classifier=\"null\" RETURN *";

    Driver driver = createDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

    final DefaultDirectedGraph<MvnArtifactNode, DependencyRelation> graph =
        doaMvnArtifactNodeImpl.getGraph(query);

    // TODO get the jgrapht
    System.out.println(graph);
  }
}

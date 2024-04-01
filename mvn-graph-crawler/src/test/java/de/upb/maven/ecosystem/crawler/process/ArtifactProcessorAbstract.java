package de.upb.maven.ecosystem.crawler.process;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import de.upb.maven.ecosystem.persistence.redis.RedisSerializerUtil;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Disabled
public abstract class ArtifactProcessorAbstract {
  public static final String LISTEN_ADDRESS = "localhost:7687";
  public static final String CREDENTIAL = "neo4j";
  protected static final Logger logger = LoggerFactory.getLogger(ArtifactProcessorTest.class);
  private static final boolean runEmbedded = true;
  private static Path databasePath;
  private static GraphDatabaseService databaseService;
  private static final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

  static {
    try {
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
    }
  }

  public static void testSerialize(Collection<MvnArtifactNode> nodes) {
    for (MvnArtifactNode node : nodes) {
      final byte[] serialize = RedisSerializerUtil.serialize(node);
      if (serialize == null || serialize.length == 0) {
        logger.error("Emtpy serialize");
      }
      RedisSerializerUtil.deserialize(serialize);
    }
  }

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

  public Driver createDriver() {
    return GraphDatabase.driver(
        embeddedDatabaseServer.boltURI(), AuthTokens.basic(CREDENTIAL, CREDENTIAL));
  }

  public void testDependencies(MvnArtifactNode artifactNode)
      throws IOException, SAXException, ParserConfigurationException {
    String fileName =
        artifactNode.getGroup()
            + "_"
            + artifactNode.getArtifact()
            + "_"
            + artifactNode.getVersion()
            + ".xml";
    ClassLoader classLoader = getClass().getClassLoader();

    final URL resource = classLoader.getResource(fileName);
    if (resource == null) {
      logger.warn("No file found: {}", fileName);
      // used to create files locally
      // Files.createFile(Paths.get("src/test/resources/" + fileName));
      return;
    }
    final File f = new File(resource.getFile());

    if (!f.exists() || f.length() == 0) {
      logger.error("File is empty {}", fileName);
      return;
    }

    final Set<String> depGAVs =
        artifactNode.getDependencies().stream()
            .map(
                x ->
                    x.getTgtNode().getGroup()
                        + ":"
                        + x.getTgtNode().getArtifact()
                        + ":"
                        + x.getTgtNode().getVersion())
            .collect(Collectors.toSet());

    final Set<String> depMgmTGAVs =
        artifactNode.getDependencyManagement().stream()
            .map(
                x ->
                    x.getTgtNode().getGroup()
                        + ":"
                        + x.getTgtNode().getArtifact()
                        + ":"
                        + x.getTgtNode().getVersion())
            .collect(Collectors.toSet());

    // parse XML file
    DocumentBuilder db = dbf.newDocumentBuilder();

    Document doc = db.parse(f);

    // optional, but recommended
    // http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
    doc.getDocumentElement().normalize();

    boolean checkMgmt = false;
    final NodeList dependenciesNodes = doc.getElementsByTagName("dependencies");
    for (int temp = 0; temp < dependenciesNodes.getLength(); temp++) {
      Node node = dependenciesNodes.item(temp);
      checkMgmt = node.getParentNode().getNodeName().equalsIgnoreCase("dependencyManagement");
      final NodeList dependencyNodes = ((Element) node).getElementsByTagName("dependency");
      for (int depCount = 0; depCount < dependencyNodes.getLength(); depCount++) {
        Node dependencyNode = dependencyNodes.item(depCount);
        // check if it exists in the current mapa
        Element element = (Element) dependencyNode;
        String group =
            element.getElementsByTagName("groupId").item(0).getFirstChild().getNodeValue();
        String artifactId =
            element.getElementsByTagName("artifactId").item(0).getFirstChild().getNodeValue();
        String version =
            element.getElementsByTagName("version").item(0).getFirstChild().getNodeValue();

        String gavToCheck = group + ":" + artifactId + ":" + version;
        if (!checkMgmt) {
          assertTrue(depGAVs.contains(gavToCheck));
        } else {
          assertTrue(depMgmTGAVs.contains(gavToCheck));
        }
      }
    }
  }
}

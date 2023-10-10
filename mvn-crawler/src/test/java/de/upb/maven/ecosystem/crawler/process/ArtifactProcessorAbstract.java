package de.upb.maven.ecosystem.crawler.process;

import static org.junit.Assert.assertTrue;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import de.upb.maven.ecosystem.persistence.redis.RedisSerializerUtil;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Ignore
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

  @Rule public TestName currentTestName = new TestName();

  @BeforeClass
  public static void setupNeo4jDB() throws IOException {
    if (runEmbedded) {
      Stopwatch sw = Stopwatch.createStarted();
      databasePath = Files.createTempDirectory(CREDENTIAL);

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

      databaseService = graphDb;
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

  public static void testSerialize(Collection<MvnArtifactNode> nodes) {
    for (MvnArtifactNode node : nodes) {
      final byte[] serialize = RedisSerializerUtil.serialize(node);
      if (serialize == null || serialize.length == 0) {
        logger.error("Emtpy serialize");
      }
      RedisSerializerUtil.deserialize(serialize);
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

  protected Driver createDriver() {
    return GraphDatabase.driver(
        "bolt://" + LISTEN_ADDRESS, AuthTokens.basic(CREDENTIAL, CREDENTIAL));
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
      checkMgmt =
              node.getParentNode().getNodeName().equalsIgnoreCase("dependencyManagement");
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

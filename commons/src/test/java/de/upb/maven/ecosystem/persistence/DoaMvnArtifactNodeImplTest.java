package de.upb.maven.ecosystem.persistence;

import com.google.common.base.Stopwatch;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DoaMvnArtifactNodeImplTest {


    private static final boolean runEmbedded = true;
    private static final Logger logger = LoggerFactory.getLogger(DoaMvnArtifactNodeImplTest.class);

    public static final String LISTEN_ADDRESS = "localhost:7687";
    public static final String CREDENTIAL = "neo4j";

    private static Path databasePath;
    private static GraphDatabaseService databaseService;

    @Rule
    public TestName currentTestName = new TestName();

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
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread() {
                            @Override
                            public void run() {
                                graphDb.shutdown();
                            }
                        });

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

        mvnArtifactNode.setParent(parent);

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

}
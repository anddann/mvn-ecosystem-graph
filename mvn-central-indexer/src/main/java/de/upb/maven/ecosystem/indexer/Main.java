package de.upb.maven.ecosystem.indexer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.QueueNames;
import de.upb.maven.ecosystem.indexer.producer.ArtifactCrawlDecider;
import de.upb.maven.ecosystem.indexer.producer.MavenIndexProducer;
import de.upb.maven.ecosystem.indexer.producer.MvnFingerprintArtifactDecider;
import de.upb.maven.ecosystem.indexer.producer.MvnGraphArtifactDecider;
import de.upb.maven.ecosystem.persistence.fingerprint.PostgresDBConnector;
import de.upb.maven.ecosystem.persistence.fingerprint.PostgresDBHandler;
import de.upb.maven.ecosystem.persistence.graph.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.graph.dao.Neo4JConnector;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.slf4j.LoggerFactory;

/**
 * Use a downloaded snapshot of the Maven Central index, and push artifacts into RabbitMQ for worker
 * nodes
 *
 * @author adann
 */
public class Main extends AbstractCrawler {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private static final ObjectMapper mapper = new ObjectMapper();
  private String indexerEnv = System.getenv("INDEXER");
  private SessionFactory databaseConnection;;

  public Main() {
    super(QueueNames.MVN_INDEX_QUEUE_NAME);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
  }

  public static void main(String[] args) throws Exception {
    Main main = new Main();
    main.run();
  }

  @Override
  protected void preFlightCheck() {
    try {
      if (StringUtils.equalsIgnoreCase(indexerEnv, "MVNGRAPH")) {
        Neo4JConnector.getDriver();

      } else if (StringUtils.equalsIgnoreCase(indexerEnv, "MVNFINGERPRINT")) {
        databaseConnection = PostgresDBConnector.createDatabaseConnection("artifact-db.cfg.xml");
      }
    } catch (Exception e) {
      LOGGER.error("[Worker] Failed connecting to database", e);
      System.exit(-1);
    }
  }

  @Override
  protected void doWorkerJob(Delivery delivery) {

    LOGGER.info("No Worker registered");
  }

  @Override
  protected void doProducerJob(AMQP.BasicProperties props) throws Exception {
    ArtifactCrawlDecider artifactCrawlDecider = null;
    if (StringUtils.equalsIgnoreCase(indexerEnv, "MVNGRAPH")) {
      LOGGER.error("No INDEXER PROPERTY Given");

      final DoaMvnArtifactNodeImpl doaMvnArtifactNode =
          new DoaMvnArtifactNodeImpl(Neo4JConnector.getDriver());
      artifactCrawlDecider = new MvnGraphArtifactDecider(doaMvnArtifactNode);

    } else if (StringUtils.equalsIgnoreCase(indexerEnv, "MVNFINGERPRINT")) {
      artifactCrawlDecider =
          new MvnFingerprintArtifactDecider(
              PostgresDBHandler.getInstance(databaseConnection, getCrawlerVersion()));
    } else {
      LOGGER.error("No INDEXER PROPERTY Given");
      return;
    }

    final MavenIndexProducer basicUsageExample = new MavenIndexProducer(this, artifactCrawlDecider);
    basicUsageExample.perform(props);
  }

  @Override
  protected void shutdownHook() {
    LOGGER.info("Shutdown");
  }
}

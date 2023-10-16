package de.upb.maven.ecosystem.fingerprint.crawler;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.QueueNames;
import de.upb.maven.ecosystem.fingerprint.crawler.process.ArtifactManager;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.fingerprint.PersistenceHandler;
import de.upb.maven.ecosystem.persistence.fingerprint.PostgresDBConnector;
import java.io.IOException;
import org.slf4j.LoggerFactory;

public class Main extends AbstractCrawler {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private static PersistenceHandler persistenceHandler;
  private static final ObjectMapper mapper = new ObjectMapper();

  public Main() {
    super(QueueNames.MVN_INDEX_QUEUE_NAME);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
  }

  public static String getRedisURLFromEnvironment() {
    String res = System.getenv("REDIS");
    if (Strings.isNullOrEmpty(res)) {
      res = "localhost";
    }
    return res;
  }

  @Override
  protected void preFlightCheck() {
    try {
      PostgresDBConnector.createDatabaseConnection("artifact-db.cfg.xml");
      persistenceHandler =
          PersistenceHandler.createPersistenceHandler(
              getRedisURLFromEnvironment(), getCrawlerVersion());
    } catch (Exception e) {
      LOGGER.error("[Worker] Failed connecting to database", e);
      System.exit(-1);
    }
  }

  public static void main(String[] args) throws Exception {
    Main main = new Main();
    main.run();
  }

  @Override
  protected void doWorkerJob(Delivery delivery) throws IOException {

    CustomArtifactInfo artifactInfo =
        mapper.readValue(delivery.getBody(), CustomArtifactInfo.class);
    try {
      LOGGER.info("[Worker] Received Request");
      ArtifactManager manager =
          new ArtifactManager(true, persistenceHandler, getSootTimeOutFromEnvironment());
      manager.process(artifactInfo, 0);
    } catch (Exception e) {
      LOGGER.error("[Worker] Failed Crawling  with", e);
    }
  }

  @Override
  protected void doProducerJob(AMQP.BasicProperties props) throws Exception {
    LOGGER.info("[Producer] No Producer Defined");
  }

  @Override
  protected void shutdownHook() {
    persistenceHandler.shutdownHook().accept(0);
  }
}

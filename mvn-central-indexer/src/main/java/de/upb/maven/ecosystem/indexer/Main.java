package de.upb.maven.ecosystem.indexer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.QueueNames;
import de.upb.maven.ecosystem.indexer.producer.MavenIndexProducer;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import org.slf4j.LoggerFactory;

public class Main extends AbstractCrawler {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private static final ObjectMapper mapper = new ObjectMapper();

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
      Neo4JConnector.getDriver();
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
    final MavenIndexProducer basicUsageExample =
        new MavenIndexProducer(this, new DoaMvnArtifactNodeImpl(Neo4JConnector.getDriver()));
    basicUsageExample.perform(props);
  }

  @Override
  protected void shutdownHook() {
    LOGGER.info("Shutdown");
  }
}

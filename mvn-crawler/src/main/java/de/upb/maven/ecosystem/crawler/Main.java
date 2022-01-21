package de.upb.maven.ecosystem.crawler;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.QueueNames;
import de.upb.maven.ecosystem.crawler.process.ArtifactManager;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.RedisWriter;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

public class Main extends AbstractCrawler {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Main.class);

  private static final ObjectMapper mapper = new ObjectMapper();
  private RedisWriter useRedis = null;

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

      if (StringUtils.isNotBlank(System.getenv("REDIS"))) {
        LOGGER.info("use redis");
        this.useRedis = RedisWriter.getInstance();
      }

    } catch (Exception e) {
      LOGGER.error("[Worker] Failed connecting to database", e);
      System.exit(-1);
    }
  }

  @Override
  protected void doWorkerJob(Delivery delivery) throws IOException {

    CustomArtifactInfo artifactInfo =
        mapper.readValue(delivery.getBody(), CustomArtifactInfo.class);
    try {
      LOGGER.info("[Worker] Received Request");
      ArtifactManager manager =
          new ArtifactManager(new DoaMvnArtifactNodeImpl(Neo4JConnector.getDriver()));
      manager.process(artifactInfo, 0);
    } catch (Exception e) {
      LOGGER.error("[Worker] Failed Crawling  with", e);
    }
  }

  @Override
  protected void doProducerJob(AMQP.BasicProperties props) {
    LOGGER.info("No Producer registered");
  }

  @Override
  protected void shutdownHook() {
    LOGGER.info("Shutdown");
    if (StringUtils.isNotBlank(System.getenv("REDIS"))) {
      LOGGER.info("use redis");
      RedisWriter.getInstance().shutdownHook();
    }
  }
}

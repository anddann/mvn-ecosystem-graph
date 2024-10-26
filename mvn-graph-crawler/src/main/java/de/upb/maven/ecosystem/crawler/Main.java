package de.upb.maven.ecosystem.crawler;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.ArtifactDownloader;
import de.upb.maven.ecosystem.QueueNames;
import de.upb.maven.ecosystem.crawler.process.ArtifactManager;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.RedisWriter;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.graph.dao.Neo4JConnector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

/**
 * Take artifact from RabbitMQ and process their dependencies Write the results in Redis or in Neo4j
 * directly
 *
 * @author adann
 */
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

      if (StringUtils.isNotBlank(System.getenv("REDIS"))) {
        LOGGER.info("use redis");
        RedisWriter.getInstance();
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
    Path tempDirectory = Files.createTempDirectory(RandomStringUtils.randomAlphabetic(10));

    try {
      ArtifactDownloader artifactDownloader = new ArtifactDownloader(tempDirectory);
      LOGGER.info("[Worker] Received Request");
      ArtifactManager manager =
          new ArtifactManager(
              artifactDownloader, new DaoMvnArtifactNodeImpl(Neo4JConnector.getDriver()));
      manager.process(artifactInfo);
    } catch (Exception e) {
      LOGGER.error("[Worker] Failed Crawling  with", e);
    } finally {
      FileUtils.deleteDirectory(tempDirectory.toFile());
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

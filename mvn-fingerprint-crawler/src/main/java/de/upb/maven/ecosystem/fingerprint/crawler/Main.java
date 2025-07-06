package de.upb.maven.ecosystem.fingerprint.crawler;

import static de.upb.maven.ecosystem.persistence.redis.RedisSerializerUtil.getRedisURLFromEnvironment;

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
import de.upb.maven.ecosystem.persistence.fingerprint.RedisHandler;
import java.io.IOException;
import java.net.Socket;
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

  private static boolean reachable() {
    try (Socket ignored = new Socket(getRedisURLFromEnvironment(), 6379)) {
      return true;
    } catch (IOException ignored) {
      return false;
    }
  }

  @Override
  protected void preFlightCheck() {
    try {

      System.out.println("Check if redis is up and running");
      boolean redisAvialable = false;
      while (!redisAvialable) {
        redisAvialable = reachable();
        if (!redisAvialable) {
          System.out.println("redis is not available waiting for sec: " + 30);
          Thread.sleep(1000 * 30);
        }
      }

      persistenceHandler =
          RedisHandler.getInstance(getRedisURLFromEnvironment(), getCrawlerVersion());
      LOGGER.info("Created RedisHandler with {}", getRedisURLFromEnvironment());

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

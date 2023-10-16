package de.upb.maven.ecosystem;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author adann */
public abstract class AbstractCrawler extends RabbitMQCollective {
  public static final String CRAWLER_VERSION = System.getenv("CRAWLER_VERSION");
  private static final Logger logger = LoggerFactory.getLogger(AbstractCrawler.class);

  /**
   * Loads configuration from environment
   *
   * @param queueName
   */
  public AbstractCrawler(String queueName) {
    this(
        queueName,
        getRabbitMQHostFromEnvironment(),
        getRabbitMQUserFromEnvironment(),
        getRabbitMQPassFromEnvironment(),
        getWorkerNodeFromEnvironment(),
        DEFAULT_RABBITMQ_REPLY_TO,
        getActorLimit());
  }

  public AbstractCrawler(
      String queueName,
      String rabbitmqHost,
      String rabbitmqUser,
      String rabbitmqPass,
      boolean workerNode,
      String replyQueueName,
      int queue_length) {
    super(
        queueName,
        rabbitmqHost,
        rabbitmqUser,
        rabbitmqPass,
        workerNode,
        replyQueueName,
        queue_length);
  }

  public static String getCrawlerVersion() {
    String res = CRAWLER_VERSION;
    if (res == null || res.isEmpty()) {
      res = "0.5.0";
    }
    return res;
  }

  protected static long getSootTimeOutFromEnvironment() {
    String res = System.getenv("SOOT_TIMEOUT_MS");

    long timeoutMs = 5 * 60 * 1000;
    if (!Strings.isNullOrEmpty(res)) {
      timeoutMs = Long.parseLong(res);
    }

    return timeoutMs;
  }

  @Override
  public void run() throws Exception {
    try {
      super.run();
    } finally {
      // in a multi-threaded environment this code is always executed...
      /// shutdown();
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread() {
                public void run() {
                  shutdown();
                }
              });
    }
  }

  @Override
  protected void shutdown() {
    logger.info("Shutdown Hook invoked");
    logger.info("Shutdown Fingerprint Executor Services");
    this.shutdownHook();
  }

  protected abstract void shutdownHook();
}

package de.upb.maven.ecosystem.redis;

import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import de.upb.maven.ecosystem.persistence.redis.RedisSerializerUtil;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class Redis2Neo4JDB {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Redis2Neo4JDB.class);
  private final JedisPool jedisPool;
  private static ScheduledExecutorService exec;
  private static Redis2Neo4JDB moveRedisToNeo4J;

  public static void init(String url, DoaMvnArtifactNodeImpl doaMvnArtifactNode) {
    exec = Executors.newSingleThreadScheduledExecutor();
    LOGGER.info("Initialize Redis2Neo4JDB");
    moveRedisToNeo4J = new Redis2Neo4JDB(url, doaMvnArtifactNode);
    exec.scheduleAtFixedRate(
        new Runnable() {
          @Override
          public void run() {
            LOGGER.info("Triggered Executor for Redis Flush");
            try {

              moveRedisToNeo4J.flush();
            } catch (Exception e) {
              LOGGER.error("Failed flush redis with", e);
            }
          }
        },
        0,
        10,
        TimeUnit.MINUTES);
  }

  private final DoaMvnArtifactNodeImpl postgresDBHandler;
  private static final String db_write_lock_name = "dbWriteLock";
  private Duration redis_write_lock_timeout;
  private String lockName;
  private Long insertCounter;

  private Redis2Neo4JDB(String url, DoaMvnArtifactNodeImpl doaMvnArtifactNode) {
    jedisPool = new JedisPool(url, 6379);
    this.postgresDBHandler = doaMvnArtifactNode;

    clearAllLocks();
  }

  private void clearAllLocks() {
    LOGGER.info("Clear all Locks");
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.del(db_write_lock_name);
    }
  }

  private void clearBuffer() {
    // The code below handles following functionality:
    // 1. extract all keys using score from 0 up to insertCounter
    // 2. delete all records using extracted keys from Redis
    // 3. delete all records by score from 0 up to insertCounter

    try (Jedis jedis = jedisPool.getResource()) {
      List<String> keys = jedis.zrangeByScore("insertBuffer", 0, insertCounter);
      if (keys != null) {
        for (String key : keys) {
          jedis.del(key);
        }
      }
      jedis.zremrangeByScore("insertBuffer", 0, insertCounter);
    }
    LOGGER.info("Cleared Buffer");
  }

  private boolean accquierelock() {
    redis_write_lock_timeout = Duration.ofSeconds(60);
    try (Jedis jedis = jedisPool.getResource()) {
      Long lockAcquired = jedis.setnx(db_write_lock_name, lockName);
      return lockAcquired == 1;
    }
  }

  private void releaseLock() {
    try (Jedis jedis = jedisPool.getResource()) {
      if (lockName.equals(jedis.get(db_write_lock_name))) {
        jedis.del(db_write_lock_name);
        LOGGER.info("Remove Lock");
      }
    }
  }

  public void flush() {
    LOGGER.info("Start Flush TasK");
    lockName = UUID.randomUUID().toString();

    boolean lockAcquired = accquierelock();
    if (!lockAcquired) {
      LOGGER.info("Failed to acquire lock");
      return;
    }
    LOGGER.info("Acquired Log");
    try {
      moveRedisToPSQL();
      clearBuffer();
    } catch (Exception e) {
      LOGGER.error("Failed to write to PSQL", e);
    } finally {
      // if done release lock
      releaseLock();
    }
  }

  private void insertIntoPostgresDB(MvnArtifactNode mavenArtifactMetadata) {

    try {

      postgresDBHandler.saveOrMerge(mavenArtifactMetadata);
    } catch (IllegalArgumentException e) {
      LOGGER.error("Failed to persist {}", mavenArtifactMetadata.toString());
    }
  }

  private void moveRedisToPSQL() {
    try (Jedis jedis = jedisPool.getResource()) {
      String insertCounter = jedis.get("insertCounter");
      if (StringUtils.isBlank(insertCounter)) {
        insertCounter = "0";
      }
      this.insertCounter = Long.valueOf(insertCounter);

      List<String> keys = jedis.zrangeByScore("insertBuffer", 0, this.insertCounter);

      for (String key : keys) {

        byte[] value = jedis.get(key.getBytes(StandardCharsets.UTF_8));
        if (value == null || value.length == 0) {
          LOGGER.error("Value from Cache is blank");
          continue;
        }

        final MvnArtifactNode mavenArtifactMetadata =
            (MvnArtifactNode) RedisSerializerUtil.deserialize(value);
        LOGGER.info("Write to NEO: ");

        insertIntoPostgresDB(mavenArtifactMetadata);

        // FIXME: why not delete key here but in clear buffer?
        jedis.del(key);
      }
    }
  }
}

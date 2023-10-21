package de.upb.maven.ecosystem.fingerprint.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import de.upb.maven.ecosystem.persistence.fingerprint.PostgresDBHandler;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.io.IOException;
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

public class Redis2PSQLTask {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Redis2PSQLTask.class);

  private static final String db_write_lock_name = "dbWriteLock";
  private static ScheduledExecutorService exec;

  private String lockName;
  private Long insertCounter;
  private ObjectMapper mapper;
  private PostgresDBHandler postgresDBHandler;
  private Duration redis_write_lock_timeout;

  private final JedisPool jedisPool;
  private static Redis2PSQLTask moveRedisToPostgres;

  private Redis2PSQLTask(String host, PostgresDBHandler postgresDBHandler) {
    jedisPool = new JedisPool(host, 6379);
    this.postgresDBHandler = postgresDBHandler;

    this.mapper = new ObjectMapper();

    this.mapper.registerModule(new Hibernate5Module());
    clearAllLocks();
  }

  public static void init(String url, PostgresDBHandler postgresDBHandler) {
    exec = Executors.newSingleThreadScheduledExecutor();
    LOGGER.info("Initialize Redis2PSQLTask");
    moveRedisToPostgres = new Redis2PSQLTask(url, postgresDBHandler);
    exec.scheduleAtFixedRate(
        new Runnable() {
          @Override
          public void run() {
            LOGGER.info("Triggered Executor for Redis Flush");
            try {

              moveRedisToPostgres.flush();
            } catch (Exception e) {
              LOGGER.error("Failed flush redis with", e);
            }
          }
        },
        0,
        10,
        TimeUnit.MINUTES);
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
      LOGGER.info("Failed to accquiere lock");
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

  private void insertIntoPostgresDB(MavenArtifactMetadata mavenArtifactMetadata) {
    final long existingId =
        postgresDBHandler.containsDownloadUrl(mavenArtifactMetadata.getDownloadUrl());
    try {

      postgresDBHandler.persist(mavenArtifactMetadata, existingId);
    } catch (IllegalArgumentException e) {
      LOGGER.error("Failed to persist {}", mavenArtifactMetadata.getGav().toString());
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
        String value = jedis.get(key);
        if (StringUtils.isBlank(value)) {
          LOGGER.warn("Value from Cache is blank");
          continue;
        }
        try {
          final MavenArtifactMetadata mavenArtifactMetadata =
              mapper.readValue(value, MavenArtifactMetadata.class);
          LOGGER.info("Write to PSQL: " + mavenArtifactMetadata.getDownloadUrl());
          insertIntoPostgresDB(mavenArtifactMetadata);
        } catch (IOException e) {
          LOGGER.error("failed insert with ", e);
        }

        jedis.del(key);
      }
    }
  }
}

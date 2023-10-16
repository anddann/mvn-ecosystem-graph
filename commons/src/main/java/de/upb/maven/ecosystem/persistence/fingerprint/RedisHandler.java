package de.upb.maven.ecosystem.persistence.fingerprint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Gav;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisHandler implements PersistenceHandler {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(PostgresDBHandler.class);

  private final ObjectMapper mapper;
  private final String crawlerVersion;

  private static RedisHandler instance;

  final JedisPoolConfig poolConfig = buildPoolConfig();
  private final JedisPool jedisPool;

  private JedisPoolConfig buildPoolConfig() {
    final JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(128);
    poolConfig.setMaxIdle(128);
    poolConfig.setMinIdle(16);
    poolConfig.setTestOnBorrow(true);
    poolConfig.setTestOnReturn(true);
    poolConfig.setTestWhileIdle(true);
    poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
    poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
    poolConfig.setNumTestsPerEvictionRun(3);
    poolConfig.setBlockWhenExhausted(true);
    return poolConfig;
  }

  public static RedisHandler getInstance(String host, String crawlerVersion) {

    if (instance == null) {
      instance = new RedisHandler(host, crawlerVersion);
    }
    return instance;
  }

  private RedisHandler(String host, String crawlerVersion) {
    this.crawlerVersion = crawlerVersion;
    jedisPool = new JedisPool(poolConfig, host);

    LOGGER.info("Initialized Redis Connection: " + host);

    this.mapper = new ObjectMapper();
    this.mapper.registerModule(new Hibernate5Module());
  }

  @Override
  public IntConsumer shutdownHook() {
    IntConsumer close = a -> jedisPool.destroy();
    return close;
  }

  @Override
  public long containsDownloadUrl(String url) throws IllegalArgumentException {
    String key = "artifactExists::" + url + ":" + crawlerVersion;
    try (Jedis jedis = jedisPool.getResource()) {
      final Boolean keyExists = jedis.exists(key);
      if (keyExists) {
        // dummy return, to indicate that it is already in the redis cache
        return 1;
      }
      return PersistenceHandler.ARTIFACT_NON_EXISTING;
    }
  }

  @Override
  public void persist(MavenArtifactMetadata crawledMavenArtifactMetaData, long existingID)
      throws IllegalArgumentException {
    // existingID is useless here as the db write is executed someere else

    final Gav gav = crawledMavenArtifactMetaData.getGav();
    String key =
        "artifactInsert::"
            + gav.getGroupId()
            + ":"
            + gav.getArtifactId()
            + ":"
            + gav.getVersionId();
    try (Jedis jedis = jedisPool.getResource()) {

      Long counter = jedis.incr("insertCounter");

      final String s;
      try {
        s = mapper.writeValueAsString(crawledMavenArtifactMetaData);
        jedis.set(key, s);

        Map<String, Double> tuples = new HashMap<>();

        tuples.put(key, counter.doubleValue());
        jedis.zadd("insertBuffer", tuples);

        LOGGER.info("Added to Redis " + key);

      } catch (JsonProcessingException e) {
        LOGGER.error("Failed to serialize to JSON with " + e);
      }
    }
  }
}

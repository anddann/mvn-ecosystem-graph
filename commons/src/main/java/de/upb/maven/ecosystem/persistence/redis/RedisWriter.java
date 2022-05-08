package de.upb.maven.ecosystem.persistence.redis;

import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisWriter {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(RedisWriter.class);
  private static RedisWriter instance;

  private final JedisPool jedisPool;

  private RedisWriter(String host) {
    jedisPool = new JedisPool(host, 6379);

    LOGGER.info("Initialized Redis Connection: " + host);
  }

  public static RedisWriter getInstance() {

    if (instance == null) {
      instance = new RedisWriter(RedisSerializerUtil.getRedisURLFromEnvironment());
    }
    return instance;
  }

  public void shutdownHook() {
    jedisPool.destroy();
  }

  public void persist(MvnArtifactNode mvnArtifactNode) throws IllegalArgumentException {
    // existingID is useless here as the db write is executed somere else

    String key =
        "artifactInsert::"
            + mvnArtifactNode.getGroup()
            + ":"
            + mvnArtifactNode.getArtifact()
            + ":"
            + mvnArtifactNode.getVersion()
            + "-"
            + mvnArtifactNode.getClassifier()
            + ":"
            + mvnArtifactNode.getPackaging();
    try (Jedis jedis = jedisPool.getResource()) {

      Long counter = jedis.incr("insertCounter");

      final byte[] serialize = RedisSerializerUtil.serialize(mvnArtifactNode);
      if (serialize == null || serialize.length == 0) {
        LOGGER.error(
            "Failed to serialize: {}",
            mvnArtifactNode.getGroup()
                + ":"
                + mvnArtifactNode.getArtifact()
                + ":"
                + mvnArtifactNode.getVersion());
      }

      jedis.set(key.getBytes(StandardCharsets.UTF_8), serialize);

      Map<String, Double> tuples = new HashMap<>();

      tuples.put(key, counter.doubleValue());
      jedis.zadd("insertBuffer", tuples);

      LOGGER.info("Added to Redis " + key);
    }
  }
}

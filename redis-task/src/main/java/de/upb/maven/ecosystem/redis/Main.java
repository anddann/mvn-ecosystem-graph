package de.upb.maven.ecosystem.redis;

import de.upb.maven.ecosystem.persistence.RedisSerializerUtil;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;

public class Main {

  public static void main(String[] args) {
    Redis2Neo4JDB.init(
        RedisSerializerUtil.getRedisURLFromEnvironment(),
        new DoaMvnArtifactNodeImpl(Neo4JConnector.getDriver()));
  }
}

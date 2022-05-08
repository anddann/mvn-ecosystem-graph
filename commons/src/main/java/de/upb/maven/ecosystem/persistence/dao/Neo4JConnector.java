package de.upb.maven.ecosystem.persistence.dao;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class Neo4JConnector {
  private static Driver instance;

  public static String getCrawlerVersion() {
    String res = System.getenv("CRAWLER_VERSION");
    if (res == null || res.isEmpty()) {
      res = "0.5.0";
    }
    return res;
  }

  public static String getNeo4jURL() {
    String res = System.getenv("NEO4J_URL");
    if (res == null || res.isEmpty()) {
      res = "bolt://localhost:7687";
    }
    return res;
  }

  public static String getNeo4jUser() {
    String res = System.getenv("NEO4J_USER");
    if (res == null || res.isEmpty()) {
      res = "neo4j";
    }
    return res;
  }

  public static String getNeo4jPASS() {
    String res = System.getenv("NEO4J_PASS");
    if (res == null || res.isEmpty()) {
      res = "test";
    }
    return res;
  }

  public static Driver getDriver() {
    if (instance == null) {
      instance =
          GraphDatabase.driver(getNeo4jURL(), AuthTokens.basic(getNeo4jUser(), getNeo4jPASS()));
      instance.verifyConnectivity();
    }
    return instance;
  }
}

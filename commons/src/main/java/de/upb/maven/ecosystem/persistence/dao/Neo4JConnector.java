package de.upb.maven.ecosystem.persistence.dao;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

/** Initiate connection to a Neo4j database using environment variables */
public class Neo4JConnector {
  public static final String CRAWLER_VERSION = System.getenv("CRAWLER_VERSION");
  public static final String NEO_4_J_URL = System.getenv("NEO4J_URL");
  public static final String NEO_4_J_USER = System.getenv("NEO4J_USER");
  public static final String NEO_4_J_PASS = System.getenv("NEO4J_PASS");
  private static Driver instance;

  public static String getCrawlerVersion() {
    String res = CRAWLER_VERSION;
    if (res == null || res.isEmpty()) {
      res = "0.5.0";
    }
    return res;
  }

  public static String getNeo4jURL() {
    String res = NEO_4_J_URL;
    if (res == null || res.isEmpty()) {
      res = "bolt://localhost:7687";
    }
    return res;
  }

  public static String getNeo4jUser() {
    String res = NEO_4_J_USER;
    if (res == null || res.isEmpty()) {
      res = "neo4j";
    }
    return res;
  }

  public static String getNeo4jPASS() {
    String res = NEO_4_J_PASS;
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

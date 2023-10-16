package de.upb.maven.ecosystem.persistence.fingerprint;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostgresDBConnector {

  private static final Logger logger = LoggerFactory.getLogger(PostgresDBConnector.class);

  public static SessionFactory createDatabaseConnection(
      String dbConfigFileName, String envVariablePrefix) {
    String dbHost = System.getenv(envVariablePrefix + "DB_HOST");
    String dbPort = System.getenv(envVariablePrefix + "DB_PORT");
    String useSSL = System.getenv(envVariablePrefix + "USE_SSL");
    String userName = System.getenv(envVariablePrefix + "DB_USER_NAME");
    String password = System.getenv(envVariablePrefix + "DB_PASS");
    return PostgresDBConnector.createDatabaseConnection(
        dbConfigFileName, dbHost, dbPort, useSSL, userName, password);
  }

  public static SessionFactory createDatabaseConnection(
      String dbConfigFileName,
      String dbHost,
      String dbPort,
      String useSSL,
      String userName,
      String password) {
    if (StringUtils.isBlank(dbHost)
        || StringUtils.isBlank(userName)
        || StringUtils.isBlank(password)) {
      throw new RuntimeException("Could not initialize the database connection.");
    }
    if (StringUtils.isBlank(useSSL)) {
      useSSL = "true";
    }

    if (StringUtils.isBlank(dbPort)) {
      dbPort = "5432";
    }

    Configuration configure = new Configuration().configure(dbConfigFileName);
    String connectionURL = configure.getProperty("hibernate.connection.url");
    // update the connection HOST
    connectionURL = StringUtils.replace(connectionURL, "${DB_HOST}", dbHost);
    // update the port
    connectionURL = StringUtils.replace(connectionURL, "${DB_PORT}", dbPort);
    // append ssl

    configure.setProperty("hibernate.connection.verifyServerCertificate", "false");
    configure.setProperty("hibernate.connection.requireSSL", useSSL);

    configure.setProperty("hibernate.connection.url", connectionURL);
    configure.setProperty("hibernate.connection.username", userName);
    configure.setProperty("hibernate.connection.password", password);

    SessionFactory sessionFactory = configure.buildSessionFactory();
    logger.debug("Initialized database");
    return sessionFactory;
  }

  public static SessionFactory createDatabaseConnection(String dbConfigFileName)
      throws InterruptedException {
    String dbHost = System.getenv("DB_HOST");
    String dbPort = System.getenv("DB_PORT");
    String useSSL = System.getenv("USE_SSL");
    String userName = System.getenv("DB_USER_NAME");
    String password = System.getenv("DB_PASS");
    return PostgresDBConnector.createDatabaseConnection(
        dbConfigFileName, dbHost, dbPort, useSSL, userName, password);
  }
}

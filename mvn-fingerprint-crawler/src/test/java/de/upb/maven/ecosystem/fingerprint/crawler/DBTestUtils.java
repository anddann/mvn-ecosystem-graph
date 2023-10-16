package de.upb.maven.ecosystem.fingerprint.crawler;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class DBTestUtils {

  public static SessionFactory createSessionFactoryInMemDB() {
    final Configuration configure = new Configuration().configure("artifact-db.cfg.xml");
    configure.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    configure.setProperty("hibernate.connection.url", "jdbc:h2:mem:myDb");
    configure.setProperty("hibernate.hbm2ddl.auto", "create");
    System.setProperty("log4j.logger.org.hibernate.SQL", "debug");
    System.setProperty("log4j.logger.org.hibernate.type.descriptor.sql", "trace");
    System.setProperty("hibernate.generate_statistics", "true");
    System.setProperty("org.hibernate.stat", "DEBUG");

    return configure.buildSessionFactory();
  }
}

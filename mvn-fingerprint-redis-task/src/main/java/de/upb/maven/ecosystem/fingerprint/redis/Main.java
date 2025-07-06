package de.upb.maven.ecosystem.fingerprint.redis;

import static de.upb.maven.ecosystem.persistence.redis.RedisSerializerUtil.getRedisURLFromEnvironment;

import de.upb.maven.ecosystem.persistence.fingerprint.PostgresDBConnector;
import de.upb.maven.ecosystem.persistence.fingerprint.PostgresDBHandler;
import java.io.IOException;
import java.net.Socket;
import org.hibernate.SessionFactory;

/**
 * Take MvnArtifactNodes from the redis store, write them into Neo4j and flush redis
 *
 * @author adann
 */
public class Main {

  public static void main(String[] args) throws InterruptedException, IOException {

    System.out.println("Check if redis is up and running");
    boolean redisAvialable = false;
    while (!redisAvialable) {
      redisAvialable = reachable();
      if (!redisAvialable) {
        System.out.println("redis is not available waiting for sec: " + 30);
        Thread.sleep(1000 * 30);
      }
    }

    final SessionFactory databaseConnection =
        PostgresDBConnector.createDatabaseConnection("artifact-db.cfg.xml");
    PostgresDBHandler postgresDBHandler = PostgresDBHandler.getInstance(databaseConnection, "0");

    Redis2PSQLTask.init(getRedisURLFromEnvironment(), postgresDBHandler);
  }

  private static boolean reachable() {
    try (Socket ignored = new Socket(getRedisURLFromEnvironment(), 6379)) {
      return true;
    } catch (IOException ignored) {
      return false;
    }
  }
}

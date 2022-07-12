package de.upb.maven.ecosystem.redis;

import de.upb.maven.ecosystem.persistence.redis.RedisSerializerUtil;

import java.io.IOException;
import java.net.Socket;

public class Main {

  public static void main(String[] args) throws InterruptedException {

    System.out.println("Check if redis is up and running");
    boolean redisAvialable = false;
    while (!redisAvialable) {
      redisAvialable = reachable();
      if (!redisAvialable) {
        System.out.println("redis is not available waiting for sec: " + 30);
        Thread.sleep(1000 * 30);
      }
    }

    Redis2Neo4JDB.init(RedisSerializerUtil.getRedisURLFromEnvironment());
  }

  private static boolean reachable() {
    try (Socket ignored = new Socket(RedisSerializerUtil.getRedisURLFromEnvironment(), 6379)) {
      return true;
    } catch (IOException ignored) {
      return false;
    }
  }
}

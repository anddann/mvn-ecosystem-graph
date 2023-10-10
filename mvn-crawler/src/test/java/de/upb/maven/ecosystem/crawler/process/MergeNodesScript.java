package de.upb.maven.ecosystem.crawler.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;

@Ignore
public class MergeNodesScript {

  @Test
  @Ignore
  public void merge() {
    final Driver driver =
        GraphDatabase.driver(
            "bolt://heap-snapshots.cs.upb.de:7687", AuthTokens.basic("neo4j", "PdBwGaQecqX69M28"));
    Class clazz = MergeNodesScript.class;

    ExecutorService executorService = Executors.newFixedThreadPool(25);

    HashSet<String> groups = new HashSet<>();
    int i = 0;
    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(clazz.getResourceAsStream("/export.csv")))) {
      String line = br.readLine();

      while (line != null) {
        System.out.println("Line: " + i++);

        line = line.trim();
        line = line.replaceAll("[^\\x00-\\x7F]", "");
        final boolean add = groups.add(line);
        if (add) {

          String finalLine = line;
          executorService.submit(
              () -> runQuery(driver, finalLine));
        }
        line = br.readLine();
      }
    } catch (IOException exception) {
      exception.printStackTrace();
    }
    awaitTerminationAfterShutdown(executorService);
  }

  public void awaitTerminationAfterShutdown(ExecutorService threadPool) {
    threadPool.shutdown();
    try {
      if (!threadPool.awaitTermination(60, TimeUnit.DAYS)) {
        threadPool.shutdownNow();
      }
    } catch (InterruptedException ex) {
      threadPool.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public void runQuery(Driver driver, String groupId) {
    System.out.println("Running on group: " + groupId);
    String query =
        String.format(
            "MATCH (n:MvnArtifact {group:\"%s\"})\n"
                + "WITH n.gavpc as gavc, COLLECT(n) AS ns\n"
                + "WHERE size(ns) > 1\n"
                + "CALL apoc.refactor.mergeNodes(ns) YIELD node\n"
                + "RETURN node;",
            groupId);

    String query2 =
        String.format(
            "MATCH (a:MvnArtifact {group:\"%s\"})-[r]->(b:MvnArtifact)\n"
                + "WITH a, type(r) as type, collect(r) as rels, b\n"
                + "WHERE size(rels) > 1\n"
                + "UNWIND tail(rels) as rel\n"
                + "DELETE rel",
            groupId);

    try (Session session = driver.session()) {
      try (Transaction tx = session.beginTransaction()) {

        // add unique constraints
        tx.run(query);
        tx.run(query2);

        tx.commit();
      }
    }
  }
}

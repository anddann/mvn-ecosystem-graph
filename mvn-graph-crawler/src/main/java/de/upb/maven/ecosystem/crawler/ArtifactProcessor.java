package de.upb.maven.ecosystem.crawler;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.crawler.process.mvnresolver.worklist.WorklistArtifactResolver;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.RedisWriter;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArtifactProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactProcessor.class);
  private final DaoMvnArtifactNode doaArtifactNode;
  private RedisWriter instance;
  private WorklistArtifactResolver worklistArtifactResolver;

  public ArtifactProcessor(
      DaoMvnArtifactNode doaArtifactNode)
      throws IOException {
    this.doaArtifactNode = doaArtifactNode;
    Objects.requireNonNull(doaArtifactNode);
    if (StringUtils.isNotBlank(System.getenv("REDIS"))) {
      LOGGER.info("use redis");
      instance = RedisWriter.getInstance();
    }
  }

  public void process(CustomArtifactInfo ai) throws IOException {
    if (StringUtils.isBlank(ai.getRepoURL())) {
      throw new IOException("No Base URL is given");
    }
    Stopwatch stopwatch = Stopwatch.createStarted();

    if (ArtifactUtils.ignoredArtifactType(ai)) {
      LOGGER.info(
          "Ignoring artifact {}:{}:{}-{}",
          ai.getGroupId(),
          ai.getArtifactId(),
          ai.getArtifactVersion(),
          ai.getClassifier());
      return;
    }
    // the producer should check if the artifact is in the db
    boolean existsInDb =
        this.doaArtifactNode.containsNodeWithVersionGQ(
            ai.getGroupId(),
            ai.getArtifactId(),
            ai.getArtifactVersion(),
            ai.getClassifier(),
            AbstractCrawler.getCrawlerVersion());
    if (existsInDb) {
      LOGGER.info(
          "Skipping artifact {}:{}:{}, already present and update in database",
          ai.getGroupId(),
          ai.getArtifactId(),
          ai.getArtifactVersion());
      return;
    }

    LOGGER.debug("[Stats] DB lookup took: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));

    this.worklistArtifactResolver = new WorklistArtifactResolver(
        ai.getRepoURL(), doaArtifactNode);
    WorklistArtifactResolver worklistArtifactResolver = this.worklistArtifactResolver;
    stopwatch.reset();
    LOGGER.info(
        "Processing Artifact: {}:{}:{}",
        ai.getGroupId(),
        ai.getArtifactId(),
        ai.getArtifactVersion());
    try {

      final Collection<MvnArtifactNode> newResolvedNodes =
          worklistArtifactResolver.process(ai);
      if (newResolvedNodes != null) {
        LOGGER.info("Writing nodes: #{} to db", newResolvedNodes.size());

        LOGGER.info(
            "[Stats] Processing Artifact took: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        stopwatch.reset();

        for (MvnArtifactNode node : newResolvedNodes) {
          if (instance != null) {
            instance.persist(node);
          } else {
            doaArtifactNode.saveOrMerge(node);
          }
        }
        LOGGER.info("[Stats] Writing to DB took: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));

      } else {
        LOGGER.warn("No nodes have been resolved");
      }
    } catch (Exception ex) {
      stopwatch.reset();
      LOGGER.error(
          "Crawling of artifact: {} , failed with ",
          ai.getGroupId()
              + ":"
              + ai.getArtifactId()
              + ":"
              + ai.getArtifactVersion()
              + "-"
              + ai.getClassifier(),
          ex);
      try {
        String log =
            ai.getGroupId()
                + ":"
                + ai.getArtifactId()
                + ":"
                + ai.getArtifactVersion()
                + "-"
                + ai.getClassifier()
                + " -- "
                + ex.getMessage()
                + "\n";
        Files.write(
            Paths.get("failed_artifacts.txt"),
            log.getBytes(),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      } catch (IOException e) {
        LOGGER.error("Could not write failed_artifacts file", e);
      }
      LOGGER.info("[Stats] Writing to files took: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    } finally {
      worklistArtifactResolver.cleanup();
    }
  }


}

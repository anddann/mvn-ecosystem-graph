package de.upb.maven.ecosystem.fingerprint.crawler.process;

import de.upb.maven.ecosystem.ArtifactDownloader;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.fingerprint.PersistenceHandler;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

public class ArtifactManager {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ArtifactManager.class);
  private final boolean comTLSH;

  private final PersistenceHandler persistenceHandler;
  private final long sootTimeOutMS;

  public ArtifactManager(
      boolean comTLSH, PersistenceHandler persistenceHandler, long sootTimeOutMS) {
    Objects.requireNonNull(persistenceHandler);
    this.comTLSH = comTLSH;
    this.sootTimeOutMS = sootTimeOutMS;
    this.persistenceHandler = persistenceHandler;
  }

  public void process(CustomArtifactInfo ai, int crawledArtifacts) throws IOException {
    if (StringUtils.isBlank(ai.getRepoURL())) {
      throw new IOException("No Base URL is given");
    }

    URL downloadURL = ArtifactUtils.constructURL(ai);

    long existingID = this.persistenceHandler.containsDownloadUrl(downloadURL.toString());
    if (existingID == PersistenceHandler.ARTIFACT_UP_TO_DATE) {
      LOGGER.info("Skipping url {}, already present and update in database", downloadURL);
      return;
    }

    Path tempDirectory = Files.createTempDirectory(
        RandomStringUtils.randomAlphabetic(10));

    try {
      ArtifactDownloader artifactDownloader = new ArtifactDownloader(tempDirectory);
      final MavenArtifactMetadata mavenArtifactMetadata =
          new ArtifactProcessor(artifactDownloader, this.comTLSH, sootTimeOutMS)
              .process(ai, crawledArtifacts, downloadURL);
      LOGGER.info("Done with: {}", mavenArtifactMetadata.getGav());

      if (mavenArtifactMetadata == null) {
        return;
      }

      persistenceHandler.persist(mavenArtifactMetadata, existingID);
    } catch (IllegalArgumentException e) {
      LOGGER.error(" Write Artifact{} failed with", ai.getArtifactId(), e);
    } finally {
      // 5. Delete temp folder contents
      FileUtils.deleteDirectory(tempDirectory.toFile());
    }
  }
}

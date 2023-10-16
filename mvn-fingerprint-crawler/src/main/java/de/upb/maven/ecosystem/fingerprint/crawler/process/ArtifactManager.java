package de.upb.maven.ecosystem.fingerprint.crawler.process;

import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.fingerprint.PersistenceHandler;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;
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

    final MavenArtifactMetadata mavenArtifactMetadata =
        new ArtifactProcessor(comTLSH, sootTimeOutMS).process(ai, crawledArtifacts, downloadURL);
    if (mavenArtifactMetadata == null) {
      return;
    }

    try {
      persistenceHandler.persist(mavenArtifactMetadata, existingID);
    } catch (IllegalArgumentException e) {
      LOGGER.error(" Write Artifact" + ai.getArtifactId() + " failed with", e);
    }
    LOGGER.info("Done with: " + mavenArtifactMetadata.getGav());
  }
}

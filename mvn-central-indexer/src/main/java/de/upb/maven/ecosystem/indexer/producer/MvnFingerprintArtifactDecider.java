package de.upb.maven.ecosystem.indexer.producer;

import de.upb.maven.ecosystem.persistence.fingerprint.PersistenceHandler;
import de.upb.maven.ecosystem.persistence.fingerprint.PostgresDBHandler;
import java.net.URL;
import org.apache.maven.index.ArtifactInfo;

/** @author adann */
public class MvnFingerprintArtifactDecider implements ArtifactCrawlDecider {

  private final PostgresDBHandler postgresDBHanlder;

  public MvnFingerprintArtifactDecider(PostgresDBHandler postgresDBHandler) {
    this.postgresDBHanlder = postgresDBHandler;
  }

  @Override
  public boolean shouldProcessArtifact(ArtifactInfo artifactInfo, URL url) {
    final long l = postgresDBHanlder.containsDownloadUrl(url.toString());
    return l == PersistenceHandler.ARTIFACT_UP_TO_DATE;
  }
}

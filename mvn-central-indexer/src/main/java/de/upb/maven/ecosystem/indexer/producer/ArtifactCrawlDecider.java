package de.upb.maven.ecosystem.indexer.producer;

import java.net.URL;
import org.apache.maven.index.ArtifactInfo;

public interface ArtifactCrawlDecider {

  public boolean shouldProcessArtifact(ArtifactInfo artifactInfo, URL url);
}

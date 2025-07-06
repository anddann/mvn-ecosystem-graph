package de.upb.maven.ecosystem.indexer.producer;

import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNode;
import java.net.URL;
import org.apache.maven.index.ArtifactInfo;

/** @author adann */
public class MvnGraphArtifactDecider implements ArtifactCrawlDecider {

  private final DaoMvnArtifactNode doaMvnArtifactNode;

  public MvnGraphArtifactDecider(DaoMvnArtifactNode daoMvnArtifactNode) {
    doaMvnArtifactNode = daoMvnArtifactNode;
  }

  @Override
  public boolean shouldProcessArtifact(ArtifactInfo artifactInfo, URL url) {
    final boolean l =
        doaMvnArtifactNode.containsNodeWithVersionGQ(
            artifactInfo.getGroupId(),
            artifactInfo.getArtifactId(),
            artifactInfo.getVersion(),
            artifactInfo.getClassifier(),
            AbstractCrawler.getCrawlerVersion());
    return l;
  }
}

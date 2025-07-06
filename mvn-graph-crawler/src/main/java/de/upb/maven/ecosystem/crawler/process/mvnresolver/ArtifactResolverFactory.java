package de.upb.maven.ecosystem.crawler.process.mvnresolver;

import de.upb.maven.ecosystem.crawler.process.mvnresolver.aether.AetherArtifactResolver;
import de.upb.maven.ecosystem.crawler.process.mvnresolver.worklist.WorklistArtifactResolver;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNode;
import java.io.IOException;

public class ArtifactResolverFactory {

  public enum RESOLVER_TYPE {WORKLIST, AETHER;}

  public static ArtifactResolver create(RESOLVER_TYPE type, DaoMvnArtifactNode artifactNode,
      String repoUrl)
      throws IOException {
    switch (type) {
      case WORKLIST:
        return new WorklistArtifactResolver(repoUrl, artifactNode);

      case AETHER:
        return new AetherArtifactResolver();
      default:
        throw new IllegalStateException("Unexpected value: " + type);
    }
  }

}

package de.upb.maven.ecosystem.crawler.process.mvnresolover.aether;

import de.upb.maven.ecosystem.crawler.process.mvnresolover.ArtifactResolver;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public class AetherArtifactResolver implements ArtifactResolver {

  @Override
  public @Nullable Collection<MvnArtifactNode> process(CustomArtifactInfo mvenartifactinfo)
      throws IOException {
    return List.of();
  }
}

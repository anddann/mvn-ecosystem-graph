package de.upb.maven.ecosystem.crawler.process.mvnresolver;

import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.io.IOException;
import java.util.Collection;
import org.jetbrains.annotations.Nullable;

public interface ArtifactResolver {

  @Nullable
  Collection<MvnArtifactNode> process(CustomArtifactInfo mvenartifactinfo)
      throws IOException;

  void cleanup();
}

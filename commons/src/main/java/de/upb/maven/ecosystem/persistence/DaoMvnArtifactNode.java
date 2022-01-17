package de.upb.maven.ecosystem.persistence;

import java.util.Optional;

public interface DaoMvnArtifactNode extends Dao<MvnArtifactNode> {

  Optional<MvnArtifactNode> getParent(long id);

  Optional<MvnArtifactNode> getParent(MvnArtifactNode instance);

  boolean containsNodeWithVersionGQ(
      String groupId, String artifactId, String version, String classifier, String targetVersion);
}

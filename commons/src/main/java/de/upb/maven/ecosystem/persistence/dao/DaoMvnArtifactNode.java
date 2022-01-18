package de.upb.maven.ecosystem.persistence.dao;

import de.upb.maven.ecosystem.persistence.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;

import java.util.Optional;

public interface DaoMvnArtifactNode extends Dao<MvnArtifactNode> {

  Optional<MvnArtifactNode> getParent(long id);

  Optional<MvnArtifactNode> getParent(MvnArtifactNode instance);

  Optional<DependencyRelation> getRelationship(MvnArtifactNode instance, MvnArtifactNode dependency);

  boolean containsNodeWithVersionGQ(
      String groupId, String artifactId, String version, String classifier, String targetVersion);
}

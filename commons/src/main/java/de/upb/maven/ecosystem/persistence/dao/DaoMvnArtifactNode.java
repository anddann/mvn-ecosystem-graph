package de.upb.maven.ecosystem.persistence.dao;

import com.google.common.base.Optional;
import de.upb.maven.ecosystem.persistence.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.util.List;

public interface DaoMvnArtifactNode extends Dao<MvnArtifactNode> {

  com.google.common.base.Optional<MvnArtifactNode> getParent(long id);

  com.google.common.base.Optional<MvnArtifactNode> getParent(MvnArtifactNode instance);

  Optional<DependencyRelation> getRelationship(
      MvnArtifactNode instance, MvnArtifactNode dependency);

  boolean containsNodeWithVersionGQ(
      String groupId, String artifactId, String version, String classifier, String crawlerVersion);

  List<DependencyRelation> getDependencyManagement(MvnArtifactNode instance);

  List<MvnArtifactNode> getDependents(
      String group, String artifact, String depGroup, String depArtifact, String depVersion);
}

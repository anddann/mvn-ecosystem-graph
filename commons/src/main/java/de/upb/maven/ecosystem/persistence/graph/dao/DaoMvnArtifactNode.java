package de.upb.maven.ecosystem.persistence.graph.dao;

import com.google.common.base.Optional;
import de.upb.maven.ecosystem.persistence.graph.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.util.List;

/**
 * DAO interface for MvnArtifactNode stored in Neo4j
 *
 * @param <T>
 * @author adann
 */
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

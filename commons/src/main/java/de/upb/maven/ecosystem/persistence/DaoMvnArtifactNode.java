package de.upb.maven.ecosystem.persistence;

import java.util.Optional;

public interface DaoMvnArtifactNode extends Dao<MvnArtifactNode> {

  Optional<MvnArtifactNode> getParent(long id);

  Optional<MvnArtifactNode> getParent(MvnArtifactNode instance);
}

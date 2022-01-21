package de.upb.maven.ecosystem.persistence.dao;

import com.google.common.base.Optional;
import de.upb.maven.ecosystem.persistence.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.util.List;

public class MvnArtifactNodeProxy extends MvnArtifactNode {

  public void setDoaMvnArtifactNode(DoaMvnArtifactNodeImpl doaMvnArtifactNode) {
    this.doaMvnArtifactNode = doaMvnArtifactNode;
  }

  private transient DoaMvnArtifactNodeImpl doaMvnArtifactNode;

  private transient boolean depsResolved = false;
  private transient boolean depMgmtResolved = false;
  private transient boolean parentRes = false;

  @Override
  public List<DependencyRelation> getDependencies() {
    if (depsResolved == false) {
      depsResolved = true;

      final List<DependencyRelation> dependencies = doaMvnArtifactNode.getDependencies(this);
      super.setDependencies(dependencies);
    }
    return super.getDependencies();
  }

  @Override
  public List<DependencyRelation> getDependencyManagement() {
    if (depMgmtResolved == false) {
      depMgmtResolved = true;

      final List<DependencyRelation> dependencies =
          doaMvnArtifactNode.getDependencyManagement(this);
      super.setDependencyManagement(dependencies);
    }
    return super.getDependencyManagement();
  }

  @Override
  public Optional<MvnArtifactNode> getParent() {

    if (parentRes == false) {
      final Optional<MvnArtifactNode> parent = doaMvnArtifactNode.getParent(this);
      super.setParent(parent);
      parentRes = true;
    }
    return super.getParent();
  }
}

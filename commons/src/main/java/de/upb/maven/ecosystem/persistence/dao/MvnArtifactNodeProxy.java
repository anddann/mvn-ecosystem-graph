package de.upb.maven.ecosystem.persistence.dao;

import de.upb.maven.ecosystem.persistence.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.util.List;
import java.util.Optional;

public class MvnArtifactNodeProxy extends MvnArtifactNode {

  public void setDoaMvnArtifactNode(DoaMvnArtifactNodeImpl doaMvnArtifactNode) {
    this.doaMvnArtifactNode = doaMvnArtifactNode;
  }

  private DoaMvnArtifactNodeImpl doaMvnArtifactNode;

  private boolean depsResolved = false;
  private boolean depMgmtResolved = false;
  private boolean parentRes = false;

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

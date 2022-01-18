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


    @Override
    public List<DependencyRelation> getDependencies() {
        //add...
        return super.getDependencies();
    }

    @Override
    public List<DependencyRelation> getDependencyManagement() {
        return super.getDependencyManagement();
    }

    @Override
    public Optional<MvnArtifactNode> getParent() {
        return doaMvnArtifactNode.getParent(this);
    }
}

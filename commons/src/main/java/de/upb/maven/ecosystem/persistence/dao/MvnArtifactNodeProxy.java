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
        if (super.getDependencies().isEmpty()) {
            final List<DependencyRelation> dependencies = doaMvnArtifactNode.getDependencies(this);
            super.setDependencies(dependencies);
            return dependencies;
        }
        return super.getDependencies();
    }

    @Override
    public List<DependencyRelation> getDependencyManagement() {
        if (super.getDependencyManagement().isEmpty()) {
            final List<DependencyRelation> dependencies = doaMvnArtifactNode.getDependencyManagement(this);
            super.setDependencyManagement(dependencies);
            return dependencies;
        }
        return super.getDependencyManagement();
    }

    @Override
    public Optional<MvnArtifactNode> getParent() {
        return doaMvnArtifactNode.getParent(this);
    }
}

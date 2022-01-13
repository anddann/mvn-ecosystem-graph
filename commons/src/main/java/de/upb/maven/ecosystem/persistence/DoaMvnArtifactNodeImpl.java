package de.upb.maven.ecosystem.persistence;

import java.util.List;
import java.util.Optional;

public class DoaMvnArtifactNodeImpl implements DaoMvnArtifactNode {


    private MvnArtifactNode constructJavaObject() {
        MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
        // set the fields
        return mvnArtifactNode;
    }

    @Override
    public Optional<MvnArtifactNode> get(long id) {
        throw new UnsupportedOperationException("getAll() not implemented");

    }

    @Override
    public Optional<MvnArtifactNode> get(MvnArtifactNode instance) {
        // match based on gav, classifier
        String query = String.format("MATCH (n:MvnArtifact {g:%s, a:%s, v:%s, c:%s,}) return n", instance.getGroup(), instance.getArtifact(), instance.getVersion(), instance.getClassifier());


        return Optional.empty();
    }

    @Override
    public List<MvnArtifactNode> getAll() {
        throw new UnsupportedOperationException("getAll() not implemented");
    }

    @Override
    public void save(MvnArtifactNode mvnArtifactNode) {
        throw new UnsupportedOperationException("getAll() not implemented");
    }

    @Override
    public void saveOrMerge(MvnArtifactNode instance) {
        //TODO
    }

    @Override
    public void update(MvnArtifactNode mvnArtifactNode, String[] params) {
        throw new UnsupportedOperationException("getAll() not implemented");
    }

    @Override
    public void delete(MvnArtifactNode mvnArtifactNode) {
        throw new UnsupportedOperationException("getAll() not implemented");

    }

    @Override
    public Optional<MvnArtifactNode> getParent(long id) {
        throw new UnsupportedOperationException("getAll() not implemented");

    }

    @Override
    public Optional<MvnArtifactNode> getParent(MvnArtifactNode instance) {
        //TODO
        return Optional.empty();
    }
}

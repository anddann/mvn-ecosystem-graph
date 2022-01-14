package de.upb.maven.ecosystem.crawler.process;

import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.MvnArtifactNode;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class ArtifactProcessorTest extends TestCase {

    public void testProcess() throws IOException {
        DaoMvnArtifactNode daoMvnArtifactNode = new DaoMvnArtifactNode() {
            // the database
            private final HashMap<String, MvnArtifactNode> cache = new HashMap<>();

            @Override
            public Optional<MvnArtifactNode> getParent(long id) {
                return Optional.empty();
            }

            @Override
            public Optional<MvnArtifactNode> getParent(MvnArtifactNode instance) {
                return Optional.empty();
            }

            @Override
            public Optional<MvnArtifactNode> get(long id) {
                return Optional.empty();
            }

            @Override
            public Optional<MvnArtifactNode> get(MvnArtifactNode instance) {


                return Optional.ofNullable(cache.get(instance.getGroup() + instance.getArtifact()));

            }

            @Override
            public List<MvnArtifactNode> getAll() {
                return null;
            }

            @Override
            public void save(MvnArtifactNode mvnArtifactNode) {

            }

            @Override
            public void saveOrMerge(MvnArtifactNode instance) {
                cache.put(instance.getGroup() + instance.getArtifact(), instance);
            }

            @Override
            public void update(MvnArtifactNode mvnArtifactNode, String[] params) {

            }

            @Override
            public void delete(MvnArtifactNode mvnArtifactNode) {

            }
        };

        ArtifactProcessor artifactProcessor = new ArtifactProcessor(daoMvnArtifactNode, "https://repo1.maven.org/maven2/");

        CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
        artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
        artifactInfo.setGroupId("io.atlasmap");
        artifactInfo.setArtifactId("atlas-csv-service");
        artifactInfo.setArtifactVersion("2.2.0-M.3");
        artifactInfo.setFileExtension("jar");

        artifactProcessor.process(artifactInfo);


    }
}
package de.upb.maven.ecosystem.crawler.process;

import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.dao.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Objects;

public class ArtifactManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactManager.class);
    private final DaoMvnArtifactNode doaArtifactNode;

    public ArtifactManager(DaoMvnArtifactNode doaArtifactNode) {
        this.doaArtifactNode = doaArtifactNode;
        Objects.requireNonNull(doaArtifactNode);
    }

    public void process(CustomArtifactInfo ai, int crawledArtifacts) throws IOException {
        if (StringUtils.isBlank(ai.getRepoURL())) {
            throw new IOException("No Base URL is given");
        }

        boolean existsInDb =
                this.doaArtifactNode.containsNodeWithVersionGQ(
                        ai.getGroupId(),
                        ai.getArtifactId(),
                        ai.getArtifactVersion(),
                        ai.getClassifier(),
                        Neo4JConnector.getCrawlerVersion());
        if (existsInDb) {
            LOGGER.info(
                    "Skipping artifact {}:{}:{}, already present and update in database",
                    ai.getGroupId(),
                    ai.getArtifactId(),
                    ai.getArtifactVersion());
            return;
        }

        //TODO -- which classifier we can skip?


        LOGGER.info(
                "Processing Artifact#{} at {}:{}:{}",
                crawledArtifacts++,
                ai.getGroupId(),
                ai.getArtifactId(),
                ai.getArtifactVersion());
        try {

            final Collection<MvnArtifactNode> newResolvedNodes = new ArtifactProcessor(doaArtifactNode, ai.getRepoURL()).process(ai);
            if (newResolvedNodes != null) {
                LOGGER.info("Writing nodes: #{} to db", newResolvedNodes.size());

                for (MvnArtifactNode node : newResolvedNodes) {
                    doaArtifactNode.saveOrMerge(node);
                }
            } else {
                LOGGER.warn("No nodes have been resolved");
            }
        } catch (Exception ex) {
            LOGGER.error("Crawling of artifact:  {} , failed with ", ai.getGroupId() + ":" + ai.getArtifactId() + ":" + ai.getArtifactVersion() + "-" + ai.getClassifier(), ex);
            try {
                String log = ai.getGroupId() + ":" + ai.getArtifactId() + ":" + ai.getArtifactVersion() + "-" + ai.getClassifier() + " -- " + ex.getMessage() + "\n";
                Files.write(Paths.get("failed_artifacts.txt"), log.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOGGER.error("Could not write failed_artifacts file", e);
            }
        }

        LOGGER.info("Done with: " + crawledArtifacts);
    }
}

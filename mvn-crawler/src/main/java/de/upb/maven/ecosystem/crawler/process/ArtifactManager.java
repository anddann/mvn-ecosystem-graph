package de.upb.maven.ecosystem.crawler.process;

import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.Neo4JConnector;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

        boolean existsInDb = this.doaArtifactNode.containsNodeWithVersionGQ(ai.getGroupId(), ai.getArtifactId(), ai.getArtifactVersion(), ai.getClassifier(), Neo4JConnector.getCrawlerVersion());
        if (existsInDb) {
            LOGGER.info("Skipping artifact {}:{}:{}, already present and update in database", ai.getGroupId(), ai.getArtifactId(), ai.getArtifactVersion());
            return;
        }
        LOGGER.info("Processing Artifact#{} at {}:{}:{}", crawledArtifacts++, ai.getGroupId(), ai.getArtifactId(), ai.getArtifactVersion());

        new ArtifactProcessor(doaArtifactNode, ai.getRepoURL()).process(ai);

        LOGGER.info("Done with: " + crawledArtifacts);
    }
}

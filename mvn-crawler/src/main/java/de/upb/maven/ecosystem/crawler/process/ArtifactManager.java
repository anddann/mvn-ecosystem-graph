package de.upb.maven.ecosystem.crawler.process;

import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.DaoMvnArtifactNode;
import java.io.IOException;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    //    URL downloadURL = ArtifactUtils.constructURL(ai);

    //    long existingID = this.daoMvnArtifactNode.containsDownloadUrl(downloadURL.toString());
    //    if (existingID == Neo4JConnector.ARTIFACT_UP_TO_DATE) {
    //      LOGGER.info("Skipping url {}, already present and update in database", downloadURL);
    //      return;
    //    }
    LOGGER.info("Processing Artifact#{} at url {}", crawledArtifacts++);

    new ArtifactProcessor(doaArtifactNode, ai.getRepoURL()).process(ai);

    LOGGER.info("Done with: " + crawledArtifacts);
  }
}

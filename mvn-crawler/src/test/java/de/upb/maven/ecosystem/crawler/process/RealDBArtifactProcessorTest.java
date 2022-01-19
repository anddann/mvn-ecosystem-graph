package de.upb.maven.ecosystem.crawler.process;

import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.io.IOException;
import java.util.Collection;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.driver.Driver;

public class RealDBArtifactProcessorTest {

  @Test
  @Ignore
  public void versionPropertiesError2() throws IOException {
    // failed on database?
    Driver driver = Neo4JConnector.getDriver();

    DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);
    ArtifactProcessor artifactProcessor =
        new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("org.apache-extras.camel-extra");
    artifactInfo.setArtifactId("camel-esper");
    artifactInfo.setArtifactVersion("2.10.0");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);
    for (MvnArtifactNode node : process) {
      DoaMvnArtifactNodeImpl.sanityCheck(node);
    }
  }
}

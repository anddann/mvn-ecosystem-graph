package de.upb.maven.ecosystem.crawler.process.mvnresolver.aether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.upb.maven.ecosystem.ArtifactDownloader;
import de.upb.maven.ecosystem.crawler.process.AbstractArtifactResolverTest;
import de.upb.maven.ecosystem.crawler.process.mvnresolver.ArtifactResolver;
import de.upb.maven.ecosystem.crawler.process.mvnresolver.worklist.WorklistArtifactResolver;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.io.IOException;
import java.util.Collection;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.xml.sax.SAXException;

public class AetherArtifactResolverTest extends AbstractArtifactResolverTest {

  @Test
  public void classifierProperty() throws IOException, ParserConfigurationException, SAXException {
    Driver driver = createDriver();

    ArtifactResolver artifactResolver =
        new AetherArtifactResolver();

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId("com.azure");
    artifactInfo.setArtifactId("azure-core");
    artifactInfo.setArtifactVersion("1.27.0");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> process = artifactResolver.process(artifactInfo);

    assertNotNull(process);
    assertFalse(process.isEmpty());
    assertEquals(3, process.size());
    testSerialize(process);

    for (MvnArtifactNode node : process) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : process) {
      DaoMvnArtifactNodeImpl.sanityCheck(node);
    }
  }
}
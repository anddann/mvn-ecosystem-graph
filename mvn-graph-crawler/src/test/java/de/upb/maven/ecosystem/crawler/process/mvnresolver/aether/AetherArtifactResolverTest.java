package de.upb.maven.ecosystem.crawler.process.mvnresolver.aether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.upb.maven.ecosystem.crawler.process.AbstractArtifactResolverTest;
import de.upb.maven.ecosystem.crawler.process.mvnresolver.ArtifactResolver;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.io.IOException;
import java.util.Collection;
import javax.xml.parsers.ParserConfigurationException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;

import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.xml.sax.SAXException;

// Maven Resolver Dependencies msut be updated to version 4.0.*; however they do not work with the indexer dependencies
public class AetherArtifactResolverTest extends AbstractArtifactResolverTest {

  @Test
  @Disabled
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


  @Test
  @Disabled
  public void plainTest() throws ArtifactDescriptorException {
    try (RepositorySystem system = Booter.newRepositorySystem(Booter.SUPPLIER);
        CloseableSession session =
            Booter.newRepositorySystemSession(system).build()) {
      Artifact artifact = new DefaultArtifact(
          "com.azure:azure-core:1.27.0");

      ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
      descriptorRequest.setArtifact(artifact);
      descriptorRequest.setRepositories(Booter.newRepositories(system, session));

      ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session,
          descriptorRequest);

      for (Dependency dependency : descriptorResult.getDependencies()) {
        System.out.println(dependency);
      }
    }
  }

  @Test
  @Disabled
  public void plainTest2() throws ArtifactDescriptorException {
    try (RepositorySystem system = Booter.newRepositorySystem(Booter.SUPPLIER);
        CloseableSession session =
            Booter.newRepositorySystemSession(system).build()) {
      Artifact artifact = new DefaultArtifact(
          "org.apache.maven.resolver:maven-resolver-impl:1.3.3");

      ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
      descriptorRequest.setArtifact(artifact);
      descriptorRequest.setRepositories(Booter.newRepositories(system, session));

      ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session,
          descriptorRequest);

      for (Dependency dependency : descriptorResult.getDependencies()) {
        System.out.println(dependency);
      }
    }
  }
}
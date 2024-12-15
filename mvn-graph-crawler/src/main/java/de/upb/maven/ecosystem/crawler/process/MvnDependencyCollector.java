package de.upb.maven.ecosystem.crawler.process;

import de.upb.maven.ecosystem.crawler.process.mvnresolover.Booter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

public class MvnDependencyCollector {
  public static void main(String[] args) throws Exception {

    try (RepositorySystem system = Booter.newRepositorySystem(Booter.selectFactory(args));
        CloseableSession session =
            Booter.newRepositorySystemSession(system).build()) {
      Artifact artifact = new DefaultArtifact("org.apache.maven.resolver:maven-resolver-impl:1.3.3");

      ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
      descriptorRequest.setArtifact(artifact);
      descriptorRequest.setRepositories(Booter.newRepositories(system, session));

      ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

      for (Dependency dependency : descriptorResult.getDependencies()) {
        System.out.println(dependency);
      }
    }
  }
}
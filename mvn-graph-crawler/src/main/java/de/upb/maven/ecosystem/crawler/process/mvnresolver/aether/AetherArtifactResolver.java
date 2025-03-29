package de.upb.maven.ecosystem.crawler.process.mvnresolver.aether;

import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.crawler.process.mvnresolver.ArtifactResolver;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.common.DependencyScope;
import de.upb.maven.ecosystem.persistence.graph.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode.ResolvingLevel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.jetbrains.annotations.Nullable;

public class AetherArtifactResolver implements ArtifactResolver {

  private static RepositorySystem repositorySystemInstance;
  private static CloseableSession repositorySystemSession;


  private static CloseableSession getRepositorySystemSession() {
    if (repositorySystemSession == null) {

      repositorySystemSession =
          Booter.newRepositorySystemSession(getRepositorySystemInstance()).build();
    }
    return repositorySystemSession;
  }

  private static RepositorySystem getRepositorySystemInstance() {
    if (repositorySystemInstance == null) {
      repositorySystemInstance = Booter.newRepositorySystem(Booter.SUPPLIER);
    }
    return repositorySystemInstance;
  }

  @Override
  public @Nullable Collection<MvnArtifactNode> process(CustomArtifactInfo mvenartifactinfo)
      throws IOException {
    ArrayList<MvnArtifactNode> generatedNodes = new ArrayList<>();

    MvnArtifactNode rootNode = new MvnArtifactNode();
    rootNode.setGroup(mvenartifactinfo.getGroupId());
    rootNode.setArtifact(mvenartifactinfo.getArtifactId());
    rootNode.setVersion(mvenartifactinfo.getArtifactVersion());
    rootNode.setClassifier(mvenartifactinfo.getClassifier());
    rootNode.setPackaging(mvenartifactinfo.getPackaging());
    rootNode.setCrawlerVersion(AbstractCrawler.getCrawlerVersion());
    rootNode.setResolvingLevel(ResolvingLevel.DANGLING);
    generatedNodes.add(rootNode);

    //"org.apache.maven.resolver:maven-resolver-impl:1.3.3"
    Artifact artifact = new DefaultArtifact(mvenartifactinfo.getGroupId(),
        mvenartifactinfo.getArtifactId(), mvenartifactinfo.getFileExtension(),
        mvenartifactinfo.getArtifactVersion());

    ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
    descriptorRequest.setArtifact(artifact);
    descriptorRequest.setRepositories(
        Booter.newRepositories(getRepositorySystemInstance(), getRepositorySystemSession()));

    ArtifactDescriptorResult descriptorResult = null;
    try {
      descriptorResult = getRepositorySystemInstance().readArtifactDescriptor(
          getRepositorySystemSession(),
          descriptorRequest);

      HashMap<String, String> newPros = new HashMap<>();

      for (Map.Entry<String, Object> entry : descriptorResult.getProperties().entrySet()) {
        newPros.put(entry.getKey().toString(), entry.getValue().toString());
      }
      // add the properties
      rootNode.setProperties(newPros);

      for (int i = 0; i < descriptorResult.getDependencies().size(); i++) {
        Dependency dependency = descriptorResult.getDependencies().get(i);
        System.out.println(dependency);
        MvnArtifactNode depNode = createFrom(dependency);
        generatedNodes.add(depNode);

        DependencyRelation dependencyRelation = new DependencyRelation();
        dependencyRelation.setPosition(i);
        dependencyRelation.setTgtNode(depNode);
        dependencyRelation.setScope(DependencyScope.COMPILE);
      }
    } catch (ArtifactDescriptorException e) {
      throw new RuntimeException(e);
    }
    rootNode.setResolvingLevel(ResolvingLevel.FULL);

    return generatedNodes;
  }

  @Override
  public void cleanup() {

  }

  private MvnArtifactNode createFrom(Dependency dependency) {

    Artifact artifact = dependency.getArtifact();
    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setGroup(artifact.getGroupId());
    mvnArtifactNode.setArtifact(artifact.getArtifactId());
    mvnArtifactNode.setVersion(artifact.getVersion());
    mvnArtifactNode.setClassifier(artifact.getClassifier());
    mvnArtifactNode.setPackaging(artifact.getExtension());
    mvnArtifactNode.setCrawlerVersion(AbstractCrawler.getCrawlerVersion());
    mvnArtifactNode.setResolvingLevel(ResolvingLevel.DANGLING);
    HashMap<String, String> newPros = new HashMap<>();

    for (Map.Entry<String, String> entry : dependency.getArtifact().getProperties().entrySet()) {
      newPros.put(entry.getKey(), entry.getValue());
    }
    return mvnArtifactNode;
  }
}

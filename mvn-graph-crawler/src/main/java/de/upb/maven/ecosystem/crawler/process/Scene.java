package de.upb.maven.ecosystem.crawler.process;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.DownloadUtils;
import de.upb.maven.ecosystem.PomFileUtil;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.graph.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

/** @author adann */
public class Scene {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ArtifactProcessor.class);
  private final HashMap<String, Model> nodeToModel = new HashMap<>();

  private static final int CONNECT_TIMEOUT = 5 * 60000;
  private static final int READ_TIMEOUT = 5 * 60000;

  public final Path TEMP_LOCATION;
  private final String repoUrl;
  private final DaoMvnArtifactNode daoMvnArtifactNode;

  private final HashMap<String, Scene.MvnArtifactNodeReference> nodesInScene = new HashMap<>();

  private final HashMap<Scene.MvnArtifactNodeReference, Scene.MvnArtifactNodeReference>
      childParent = new HashMap<>();

  public Scene(String repoUrl, DaoMvnArtifactNode doaArtifactNode) throws IOException {
    TEMP_LOCATION = Files.createTempDirectory(RandomStringUtils.randomAlphabetic(10));
    this.repoUrl = repoUrl;
    this.daoMvnArtifactNode = doaArtifactNode;
  }

  public DependencyRelation createCopy(DependencyRelation srcDepRelation)
      throws InvocationTargetException, IllegalAccessException {
    final DependencyRelation newRelation = new DependencyRelation();
    final MvnArtifactNode newMvnNode = new MvnArtifactNode();
    BeanUtils.copyProperties(newMvnNode, srcDepRelation.getTgtNode());
    BeanUtils.copyProperties(newRelation, srcDepRelation);
    newRelation.setTgtNode(new MvnArtifactNodeReference(newMvnNode));
    return newRelation;
  }

  public static class MvnArtifactNodeReference extends MvnArtifactNode {

    private MvnArtifactNode node;

    private boolean isResolved;

    public MvnArtifactNodeReference(MvnArtifactNode node) {
      this.node = node;
      this.isResolved = false;
    }

    public boolean isFullyResolved() {
      if (isResolved) {
        return true;
      }
      if (node == null) {
        return false;
      }
      boolean fullyResolved =
          !StringUtils.contains(node.getGroup(), "$")
              && !StringUtils.contains(node.getArtifact(), "$")
              && !StringUtils.contains(node.getVersion(), "$");
      isResolved = true;
      return fullyResolved;
    }

    private void resolve() {
      if (isResolved) {
        return;
      } else {
        // do resolving logic here
      }
    }

    @Override
    public ResolvingLevel getResolvingLevel() {
      return node.getResolvingLevel();
    }

    @Override
    public String getCrawlerVersion() {
      return node.getCrawlerVersion();
    }

    @Override
    public String getGroup() {
      return node.getGroup();
    }

    @Override
    public String getArtifact() {
      return node.getArtifact();
    }

    @Override
    public String getVersion() {
      return node.getVersion();
    }

    @Override
    public String getRepoURL() {
      return node.getRepoURL();
    }

    @Override
    public String getScmURL() {
      return node.getScmURL();
    }

    @Override
    public String getClassifier() {
      return node.getClassifier();
    }

    @Override
    public String getPackaging() {
      return node.getPackaging();
    }

    @Override
    public Map<String, String> getProperties() {
      return node.getProperties();
    }

    @Override
    public Optional<MvnArtifactNode> getParent() {
      return node.getParent();
    }

    @Override
    public List<DependencyRelation> getDependencies() {
      return node.getDependencies();
    }

    @Override
    public List<DependencyRelation> getDependencyManagement() {
      return node.getDependencyManagement();
    }

    @Override
    public void setResolvingLevel(ResolvingLevel resolvingLevel) {
      node.setResolvingLevel(resolvingLevel);
    }

    @Override
    public void setCrawlerVersion(String crawlerVersion) {
      node.setCrawlerVersion(crawlerVersion);
    }

    @Override
    public void setGroup(String group) {
      node.setGroup(group);
    }

    @Override
    public void setArtifact(String artifact) {
      node.setArtifact(artifact);
    }

    @Override
    public void setVersion(String version) {
      node.setVersion(version);
    }

    @Override
    public void setRepoURL(String repoURL) {
      node.setRepoURL(repoURL);
    }

    @Override
    public void setScmURL(String scmURL) {
      node.setScmURL(scmURL);
    }

    @Override
    public void setPackaging(String packaging) {
      node.setPackaging(packaging);
    }

    @Override
    public void setProperties(Map<String, String> properties) {
      node.setProperties(properties);
    }

    @Override
    @JsonIgnore
    public void setDependencies(List<DependencyRelation> dependencies) {
      node.setDependencies(dependencies);
    }

    @Override
    @JsonIgnore
    public void setDependencyManagement(List<DependencyRelation> dependencyManagement) {
      node.setDependencyManagement(dependencyManagement);
    }

    @Override
    public boolean equals(Object o) {
      return node.equals(o);
    }

    @Override
    public int hashCode() {
      return node.hashCode();
    }

    @Override
    public String toString() {
      return node.toString();
    }

    @Override
    @JsonProperty("hashId")
    public String getHashId() {
      return node.getHashId();
    }

    @Override
    public void setParent(Optional<MvnArtifactNode> parent) {
      node.setParent(parent);
    }

    @Override
    public void setClassifier(String classifier) {
      node.setClassifier(classifier);
    }
  }

  public static String genId(MvnArtifactNode node) {
    return Scene.genId(
        node.getGroup(),
        node.getArtifact(),
        node.getVersion(),
        node.getClassifier(),
        node.getPackaging());
  }

  public static String genId(
      String groupId, String artifact, String version, String classifier, String packaging) {
    String identifier =
        groupId + ":" + artifact + ":" + version + "-" + classifier + "-" + packaging;
    return identifier;
  }

  @NotNull
  public CustomArtifactInfo getCustomArtifactInfo(MvnArtifactNode mvnArtifactNode) {
    // Derive pom.xml from info
    CustomArtifactInfo pomInfo = new CustomArtifactInfo();
    pomInfo.setClassifier(mvnArtifactNode.getClassifier());
    pomInfo.setGroupId(mvnArtifactNode.getGroup());
    pomInfo.setArtifactId(mvnArtifactNode.getArtifact());
    pomInfo.setArtifactVersion(mvnArtifactNode.getVersion());
    pomInfo.setRepoURL(this.repoUrl);
    pomInfo.setFileExtension("pom");
    return pomInfo;
  }

  public void add(MvnArtifactNode mvnArtifactNode, Model model) {
    nodeToModel.put(Scene.genId(mvnArtifactNode), model);
  }

  public MvnArtifactNodeReference makeNodeRef(
      String groupId, String artifact, String version, String classifier, String packaging) {

    // only here a call to new is allowed .. the others are look ups
    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setGroup(groupId);
    mvnArtifactNode.setArtifact(artifact);
    mvnArtifactNode.setVersion(version);
    mvnArtifactNode.setClassifier(classifier);
    mvnArtifactNode.setPackaging(packaging);
    mvnArtifactNode.setCrawlerVersion(AbstractCrawler.getCrawlerVersion());

    if (!StringUtils.isBlank(groupId)
        && !StringUtils.isBlank(artifact)
        && !StringUtils.isBlank(version)
        && !StringUtils.startsWith(version, "$")) {

      // we have all info to create a proper reference
      String identifier = genId(groupId, artifact, version, classifier, packaging);

      // check if already in scen
      if (nodesInScene.containsKey(identifier)) {
        return nodesInScene.get(identifier);
      } else {

        Stopwatch stopwatch = Stopwatch.createStarted();
        MvnArtifactNode nodeToReturn;
        //
        final Optional<MvnArtifactNode> optionalMvnArtifactNode =
            daoMvnArtifactNode.get(mvnArtifactNode);
        //
        // problem, when we have a "dangling" node in the db.
        // 1. we saw the node as a dependency and added it to the db
        // 2. we return the node here, however, neither its properties nor its dependencies have
        // been
        // resolved before...
        // merge with mvnNode - use the shallow info from the database
        if (optionalMvnArtifactNode.isPresent()
            && optionalMvnArtifactNode.get().getResolvingLevel()
                == MvnArtifactNode.ResolvingLevel.FULL) {
          //  if we want to resolve a parent ... the refernce is obvoiusly not updated in the
          // child but still pointing to the "old" unresolved node ... :(
          // -- same goes obviously for import nodes... :(

          nodeToReturn = optionalMvnArtifactNode.get();
          LOGGER.debug(
              "[Stats] DB lookup of Artifact took: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));
        } else {
          nodeToReturn = mvnArtifactNode;
        }

        MvnArtifactNodeReference mvnArtifactNodeReference =
            new MvnArtifactNodeReference(nodeToReturn);
        nodesInScene.put(identifier, mvnArtifactNodeReference);
        return mvnArtifactNodeReference;
      }
    }

    MvnArtifactNodeReference mvnArtifactNodeReference =
        new MvnArtifactNodeReference(mvnArtifactNode);

    return mvnArtifactNodeReference;
  }

  public Model nodeToModelGetOrFetchModel(MvnArtifactNode mvnArtifactNode) {
    Model model = this.nodeToModel.get(genId(mvnArtifactNode));
    if (model == null) {
      CustomArtifactInfo pomInfo = this.getCustomArtifactInfo(mvnArtifactNode);
      Path pomLocation = null;
      try {
        pomLocation = DownloadUtils.downloadFilePlainURL(pomInfo, this.TEMP_LOCATION);

        final MavenProject mavenProject = PomFileUtil.readPom(pomLocation);

        if (mavenProject != null && mavenProject.getModel() != null) {
          model = mavenProject.getModel();
          // add to the hashset - to get profile information later (easily)
          nodeToModel.put(genId(mvnArtifactNode), model);
        }
      } catch (IOException exception) {
        LOGGER.error("Failed to resolve model for {}", pomInfo);
      }
    }
    return model;
  }
}

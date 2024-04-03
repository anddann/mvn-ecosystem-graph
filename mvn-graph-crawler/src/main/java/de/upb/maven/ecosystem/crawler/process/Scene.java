package de.upb.maven.ecosystem.crawler.process;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.PomFileUtil;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

/**
 * @author adann
 */
public class Scene {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ArtifactProcessor.class);
  private final HashMap<String, Model> nodeToModel = new HashMap<>();

  private static final int CONNECT_TIMEOUT = 5 * 60000;
  private static final int READ_TIMEOUT = 5 * 60000;

  private final Path TEMP_LOCATION;
  private final String repoUrl;
  private final DaoMvnArtifactNode daoMvnArtifactNode;

  private final HashMap<String, MvnArtifactNode> nodesInScene = new HashMap<>();

  public Scene(String repoUrl, DaoMvnArtifactNode doaArtifactNode) throws IOException {
    TEMP_LOCATION = Files.createTempDirectory(RandomStringUtils.randomAlphabetic(10));
    this.repoUrl = repoUrl;
    this.daoMvnArtifactNode = doaArtifactNode;
  }

  public static String genId(MvnArtifactNode node) {
    String identifier =
        node.getGroup()
            + ":"
            + node.getArtifact()
            + ":"
            + node.getVersion()
            + "-"
            + node.getClassifier()
            + "-"
            + node.getPackaging();
    return identifier;
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

  public Path downloadFilePlainURL(CustomArtifactInfo info) throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();

    URL downloadURL = ArtifactUtils.constructURL(info);
    LOGGER.info("Downloading file from plain url: {}", downloadURL);

    String classifier = "";
    // handle null values coming from the database, since neo4j does not allow null, we have the
    // string "null"
    if (StringUtils.isNotBlank(info.getClassifier())
        && !StringUtils.equals("null", info.getClassifier())) {
      classifier = "-" + info.getClassifier();
    }
    String jarName =
        info.getArtifactId()
            + "-"
            + info.getArtifactVersion()
            + classifier
            + "."
            + info.getFileExtension();
    Path fileName = this.TEMP_LOCATION.resolve(jarName);
    FileUtils.copyURLToFile(downloadURL, fileName.toFile(), CONNECT_TIMEOUT, READ_TIMEOUT);
    if (!Files.exists(fileName)) {
      throw new IOException("Failed to download file: " + jarName);
    }
    stopwatch.stop();

    LOGGER.info(
        "[Stats] Downloading {} took {}", fileName.getFileName().toString(), stopwatch.elapsed());
    return fileName;
  }

  public void add(MvnArtifactNode mvnArtifactNode, Model model) {
    nodeToModel.put(Scene.genId(mvnArtifactNode), model);
  }

  public MvnArtifactNode makeNodeRef(
      String groupId, String artifact, String version, String classifier, String packaging) {

    // only here a call to new is allowed .. the others are look ups
    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setGroup(groupId);
    mvnArtifactNode.setArtifact(artifact);
    mvnArtifactNode.setVersion(version);
    mvnArtifactNode.setClassifier(classifier);
    mvnArtifactNode.setPackaging(packaging);

    // if not fully resolved properties loopup is wasted
    if (StringUtils.isBlank(groupId)
        || StringUtils.isBlank(artifact)
        || StringUtils.isBlank(version)
        || StringUtils.startsWith(version, "$")) {
      // not resolved just a dummy refernce that needs to be resolved later
      return mvnArtifactNode;
    }
    String identifier = genId(groupId, artifact, version, classifier, packaging);

    if (nodesInScene.containsKey(identifier)) {
      return nodesInScene.get(identifier);
    }

    Stopwatch stopwatch = Stopwatch.createStarted();
    MvnArtifactNode nodeToReturn;
    //
    final Optional<MvnArtifactNode> optionalMvnArtifactNode =
        daoMvnArtifactNode.get(mvnArtifactNode);
    //
    // problem, when we have a "dangling" node in the db.
    // 1. we saw the node as a dependency and added it to the db
    // 2. we return the node here, however, neither its properties nor its dependencies have been
    // resolved before...
    // merge with mvnNode - use the shallow info from the database
    if (optionalMvnArtifactNode.isPresent()
        && optionalMvnArtifactNode.get().getResolvingLevel()
            == MvnArtifactNode.ResolvingLevel.FULL) {
      //  if we want to resolve a parent ... the refernce is obvoiusly not updated in the
      // child but still pointing to the "old" unresolved node ... :(
      // -- same goes obvoiulsy for import nodes... :(

      nodeToReturn = optionalMvnArtifactNode.get();
      LOGGER.debug(
          "[Stats] DB lookup of Artifact took: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    } else {
      nodeToReturn = mvnArtifactNode;
    }
    nodesInScene.put(identifier, nodeToReturn);
    return nodeToReturn;
  }

  public Model nodeToModelGetOrFetchModel(MvnArtifactNode mvnArtifactNode) {
    Model model = this.nodeToModel.get(genId(mvnArtifactNode));
    if (model == null) {

      CustomArtifactInfo pomInfo = this.getCustomArtifactInfo(mvnArtifactNode);
      Path pomLocation = null;
      try {
        pomLocation = downloadFilePlainURL(pomInfo);

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

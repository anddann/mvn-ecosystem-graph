package de.upb.maven.ecosystem.crawler.process;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.PomFileUtil;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

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

  public Scene(String repoUrl) throws IOException {
    TEMP_LOCATION = Files.createTempDirectory(RandomStringUtils.randomAlphabetic(10));
    this.repoUrl = repoUrl;
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

  @NotNull
  public CustomArtifactInfo getCustomArtifactInfo(
      MvnArtifactNode mvnArtifactNode) {
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

  public Path downloadFilePlainURL(CustomArtifactInfo info)
      throws IOException {
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

  public void add(MvnArtifactNode mvnArtifactNode, Model model){
    nodeToModel.put(Scene.genId(mvnArtifactNode), model);
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

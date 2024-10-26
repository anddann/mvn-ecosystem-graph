package de.upb.maven.ecosystem;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

public class ArtifactDownloader {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ArtifactDownloader.class);

  private static final int CONNECT_TIMEOUT = 30000;
  private static final int READ_TIMEOUT = 30000;
  private final Path downloadFolder;

  public ArtifactDownloader(Path downloadFolder) {
    this.downloadFolder = downloadFolder;
  }

  public Path downloadFilePlainURL(CustomArtifactInfo info) throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();

    URL downloadURL = ArtifactUtils.constructURL(info);

    String classifier = "";
    // handle null values coming from the database, since neo4j does not allow null, we have the
    // string "null"
    if (StringUtils.isNotBlank(info.getClassifier())
        && !StringUtils.equals("null", info.getClassifier())) {
      classifier = "-" + info.getClassifier();
    }

    if (StringUtils.equalsIgnoreCase(info.getFileExtension(), "pom")) {
      // no classifier for downloading a pom file (e.g., cut of jar-with-dependencies)
      classifier = "";
    }

    String jarName =
        info.getArtifactId()
            + "-"
            + info.getArtifactVersion()
            + classifier
            + "."
            + info.getFileExtension();
    Path fileName = downloadFolder.resolve(jarName);
    FileUtils.copyURLToFile(downloadURL, fileName.toFile(), CONNECT_TIMEOUT, READ_TIMEOUT);
    if (!Files.exists(fileName)) {
      throw new IOException("Failed to download: " + jarName);
    }
    stopwatch.stop();

    LOGGER.info(
        "[Stats] Downloading {} took {}", fileName.getFileName().toString(), stopwatch.elapsed());
    return fileName;
  }
}

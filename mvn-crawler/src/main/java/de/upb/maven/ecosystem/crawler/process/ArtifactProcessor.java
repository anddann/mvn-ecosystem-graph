package de.upb.maven.ecosystem.crawler.process;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.crawler.PomFileUtil;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.DependencyRelation;
import de.upb.maven.ecosystem.persistence.DependencyScope;
import de.upb.maven.ecosystem.persistence.MvnArtifactNode;
import de.upb.maven.ecosystem.persistence.Neo4JConnector;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

public class ArtifactProcessor {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ArtifactProcessor.class);
  private static final int CONNECT_TIMEOUT = 5 * 60000;
  private static final int READ_TIMEOUT = 5 * 60000;

  private final Path TEMP_LOCATION;

  private final DaoMvnArtifactNode daoMvnArtifactNode;
  private final String repoUrl;

  public ArtifactProcessor(DaoMvnArtifactNode doaArtifactNode, String repoUrl) throws IOException {
    TEMP_LOCATION = Files.createTempDirectory(RandomStringUtils.randomAlphabetic(10));
    this.daoMvnArtifactNode = doaArtifactNode;
    this.repoUrl = repoUrl;
  }

  @Nullable
  public MvnArtifactNode process(CustomArtifactInfo mvenartifactinfo) {

    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setGroup(mvenartifactinfo.getGroupId());
    mvnArtifactNode.setArtifact(mvenartifactinfo.getArtifactId());
    mvnArtifactNode.setVersion(mvenartifactinfo.getArtifactVersion());

    mvnArtifactNode.setClassifier(mvenartifactinfo.getClassifier());
    mvnArtifactNode.setPackaging(mvenartifactinfo.getPackaging());
    mvnArtifactNode.setCrawlerVersion(Neo4JConnector.getCrawlerVersion());

    // return if it already exists
    final Optional<MvnArtifactNode> optionalMvnArtifactNode =
        daoMvnArtifactNode.get(mvnArtifactNode);
    if (optionalMvnArtifactNode.isPresent()) {
      return optionalMvnArtifactNode.get();
    }

    // 2. Download file
    // 2.1 try by URL
    // Compute further information from the pom
    addInfoFromPom(mvenartifactinfo, mvnArtifactNode);

    // resolve all direct dependencies using the parent and dependency management edges
    // go through the dependencies and resolve the properties
    Queue<DependencyRelation> dependenciesToCheck = new ArrayDeque<>();
    dependenciesToCheck.addAll(mvnArtifactNode.getDependencies());
    dependenciesToCheck.addAll(mvnArtifactNode.getDependencyManagement());

    Queue<MvnArtifactNode> dependencyPropertiesToResolve = new ArrayDeque<>();
    Queue<MvnArtifactNode> dependencyWithoutVersion = new ArrayDeque<>();

    while (!dependenciesToCheck.isEmpty()) {
      DependencyRelation dependencyRelation = dependenciesToCheck.poll();
      if (StringUtils.isBlank(dependencyRelation.getTgtNode().getVersion())) {
        dependencyWithoutVersion.add(dependencyRelation.getTgtNode());
      }
      if (StringUtils.startsWith(dependencyRelation.getTgtNode().getVersion(), "$")) {
        dependencyPropertiesToResolve.add(mvnArtifactNode);
      }
    }

    // USE as fifo with add and poll since the order matters
    ArrayDeque<MvnArtifactNode> dependencyManagementNodesToCheck = new ArrayDeque<>();
    // resolve parents first <-> since the dependency management may also contain properties that we
    // need to resolve first
    MvnArtifactNode currentNode = mvnArtifactNode;
    while (currentNode != null) {
      // check for properties
      final Map<String, String> properties = mvnArtifactNode.getProperties();

      for (Iterator<MvnArtifactNode> iterator = dependencyPropertiesToResolve.iterator();
          iterator.hasNext(); ) {
        MvnArtifactNode dep = iterator.next();
        String version = dep.getVersion().substring(2, dep.getVersion().length() - 1);
        final String s = properties.get(version);
        if (s != null) {
          dep.setVersion(s);
          iterator.remove();
        }
      }

      dependencyManagementNodesToCheck.add(mvnArtifactNode);

      final MvnArtifactNode parent = currentNode.getParent();
      if (parent != null) {
        CustomArtifactInfo customArtifactInfo = new CustomArtifactInfo();
        customArtifactInfo.setGroupId(parent.getGroup());
        customArtifactInfo.setArtifactId(parent.getArtifact());
        customArtifactInfo.setArtifactVersion(parent.getVersion());
        customArtifactInfo.setPackaging("pom");
        customArtifactInfo.setClassifier(parent.getClassifier());
        customArtifactInfo.setRepoURL(this.repoUrl);
        currentNode = process(customArtifactInfo);
      } else {
        currentNode = null;
      }
    }
    if (!dependencyPropertiesToResolve.isEmpty()) {
      // we still have unresolved properties?
      LOGGER.error("we still have unresolved properties?");
    }

    // go through dependency mgt
    while (!dependencyWithoutVersion.isEmpty() && !dependencyManagementNodesToCheck.isEmpty()) {
      final MvnArtifactNode poll = dependencyManagementNodesToCheck.poll();

      poll.getDependencyManagement()
          .sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));

      // default dependencymgt nodes
      final List<DependencyRelation> defaultNodes =
          poll.getDependencyManagement().stream()
              .filter(x -> x.getScope() != DependencyScope.IMPORT)
              .collect(Collectors.toList());
      defaultNodes.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
      for (DependencyRelation dependencyRelation : defaultNodes) {
        final MvnArtifactNode tgtNode = dependencyRelation.getTgtNode();

        // check if we found our managed dependency
        for (Iterator<MvnArtifactNode> iterator = dependencyWithoutVersion.iterator();
            iterator.hasNext(); ) {
          MvnArtifactNode dep = iterator.next();
          if (StringUtils.equals(tgtNode.getGroup(), dep.getGroup())
              && StringUtils.equals(tgtNode.getArtifact(), dep.getArtifact())) {
            dep.setVersion(tgtNode.getVersion());
            iterator.remove();
          }
        }
      }

      // import dependencymgt nodes
      final List<DependencyRelation> importNodes =
          poll.getDependencyManagement().stream()
              .filter(x -> x.getScope() == DependencyScope.IMPORT)
              .collect(Collectors.toList());
      importNodes.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));

      for (DependencyRelation dependencyRelation : importNodes) {

        final MvnArtifactNode tgtNode = dependencyRelation.getTgtNode();

        // expliclty add add the head of the queue, since this is the closed import

        CustomArtifactInfo customArtifactInfo = new CustomArtifactInfo();
        customArtifactInfo.setGroupId(tgtNode.getGroup());
        customArtifactInfo.setArtifactId(tgtNode.getArtifact());
        customArtifactInfo.setArtifactVersion(tgtNode.getVersion());
        customArtifactInfo.setPackaging("pom");
        customArtifactInfo.setClassifier(tgtNode.getClassifier());
        customArtifactInfo.setRepoURL(this.repoUrl);
        currentNode = process(customArtifactInfo);
        dependencyManagementNodesToCheck.addFirst(currentNode);
      }
    }

    if (!dependencyWithoutVersion.isEmpty()) {
      // we still have unresolved properties?
      LOGGER.error("we still have unresolved deps?");
    }

    LOGGER.info("Done crawling Artifact");

    // when nothing to resolve write into db
    daoMvnArtifactNode.saveOrMerge(mvnArtifactNode);

    return mvnArtifactNode;
  }

  /**
   * Extract the license and dependency information from the pom.xml file of that artifact and addes
   * them to the mavenArtifactMetadat
   *
   * @param info
   */
  public void addInfoFromPom(final CustomArtifactInfo info, MvnArtifactNode mvnArtifactNode) {

    // Derive pom.xml from info
    CustomArtifactInfo pomInfo = new CustomArtifactInfo();
    pomInfo.setClassifier(info.getClassifier());
    pomInfo.setGroupId(info.getGroupId());
    pomInfo.setArtifactVersion(info.getArtifactVersion());
    pomInfo.setArtifactId(info.getArtifactId());
    pomInfo.setBundleLicense(info.getBundleLicense());
    pomInfo.setRepoURL(info.getRepoURL());
    pomInfo.setFileExtension("pom");
    Path pomLocation = null;
    try {
      pomLocation = downloadFilePlainURL(pomInfo, TEMP_LOCATION);

      final MavenProject mavenProject = PomFileUtil.readPom(pomLocation);

      if (mavenProject != null) {

        final MavenProject mavenProjectParent = mavenProject.getParent();
        // add the parent
        MvnArtifactNode parent = new MvnArtifactNode();

        parent.setGroup(mavenProjectParent.getGroupId());
        parent.setArtifact(mavenProjectParent.getArtifactId());
        parent.setVersion(mavenProjectParent.getVersion());
        final String packaging = mavenProjectParent.getPackaging();
        if (StringUtils.isNotBlank(packaging)) {
          parent.setPackaging(packaging);
        } else {
          parent.setPackaging("pom");
        }

        HashMap<String, String> newPros = new HashMap<>();
        for (Map.Entry<Object, Object> entry : mavenProject.getProperties().entrySet()) {
          newPros.put(entry.getKey().toString(), entry.getValue().toString());
        }

        // add the properties
        mvnArtifactNode.setProperties(newPros);

        // find the dependencies, and add them
        for (int i = 0; i < mavenProject.getDependencies().size(); i++) {
          Dependency mavenDep = (Dependency) mavenProject.getDependencies().get(i);
          mvnArtifactNode.getDependencies().add(create(mavenDep, i));
        }

        for (int i = 0; i < mavenProject.getDependencyManagement().getDependencies().size(); i++) {
          Dependency mavenDep = mavenProject.getDependencyManagement().getDependencies().get(i);
          mvnArtifactNode.getDependencies().add(create(mavenDep, i));
        }
      }
    } catch (IOException ex) {
      LOGGER.error("Downloading or parsing of .pom failed with: {}", ex.getMessage());
    } finally {
      // 5. Delete temp folder contents
      try {
        if (pomLocation != null) {
          Files.delete(pomLocation);
        }
      } catch (IOException e) {

      }
    }
  }

  private DependencyRelation create(Dependency mavenDep, int position) {
    MvnArtifactNode dependency = new MvnArtifactNode();

    dependency.setGroup(mavenDep.getGroupId());
    dependency.setArtifact(mavenDep.getArtifactId());
    dependency.setVersion(mavenDep.getVersion());
    dependency.setClassifier(mavenDep.getClassifier());
    // should correspond BUT not a must have?
    dependency.setPackaging(mavenDep.getType());

    final String mavenDepScope = mavenDep.getScope();
    DependencyScope dependencyScope = getScope(mavenDepScope);
    DependencyRelation dependencyRelation = new DependencyRelation();
    dependencyRelation.setTgtNode(dependency);
    dependencyRelation.setClassifier(mavenDep.getClassifier());
    dependencyRelation.setOptional(mavenDep.isOptional());
    dependencyRelation.setScope(dependencyScope);
    dependencyRelation.setType(mavenDep.getType());
    dependencyRelation.setPosition(position);
    final List<String> exclusions =
        mavenDep.getExclusions().stream()
            .map(x -> x.getGroupId() + ":" + x.getArtifactId())
            .collect(Collectors.toList());
    dependencyRelation.setExclusions(exclusions);
    return dependencyRelation;
  }

  private DependencyScope getScope(String mavenScope) {
    DependencyScope dependencyScope = DependencyScope.COMPILE;
    if (StringUtils.isNotBlank(mavenScope)) {
      if (mavenScope.equalsIgnoreCase("compile")) {
        dependencyScope = DependencyScope.COMPILE;
      } else if (mavenScope.equalsIgnoreCase("runtime")) {
        dependencyScope = DependencyScope.RUNTIME;
      } else if (mavenScope.equalsIgnoreCase("provided")) {
        dependencyScope = DependencyScope.PROVIDED;
      } else if (mavenScope.equalsIgnoreCase("test")) {
        dependencyScope = DependencyScope.TEST;
      } else if (mavenScope.equalsIgnoreCase("system")) {
        dependencyScope = DependencyScope.SYSTEM;
      } else if (mavenScope.equalsIgnoreCase("import")) {
        dependencyScope = DependencyScope.IMPORT;
      }
    }
    return dependencyScope;
  }

  private Path downloadFilePlainURL(CustomArtifactInfo info, Path downloadFolder)
      throws IOException {
    Stopwatch stopwatch = Stopwatch.createStarted();

    URL downloadURL = ArtifactUtils.constructURL(info);
    LOGGER.info("Downloading file from plain url: {}", downloadURL);

    String classifier = "";
    if (info.getClassifier() != null) {
      classifier = "-" + info.getClassifier();
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
      throw new IOException("Failed to download file: " + jarName);
    }
    stopwatch.stop();

    LOGGER.info(
        "[Stats] Downloading {} took {}", fileName.getFileName().toString(), stopwatch.elapsed());
    return fileName;
  }

  private String getFilename(String fileName) {
    int lastIndexOf = fileName.lastIndexOf("/");
    return lastIndexOf > 0 ? fileName.substring(lastIndexOf + 1) : fileName;
  }
}

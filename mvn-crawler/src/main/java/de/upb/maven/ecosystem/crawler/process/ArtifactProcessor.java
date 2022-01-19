package de.upb.maven.ecosystem.crawler.process;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.crawler.PomFileUtil;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.dao.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import de.upb.maven.ecosystem.persistence.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.model.DependencyScope;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;

public class ArtifactProcessor {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ArtifactProcessor.class);
  private static final int CONNECT_TIMEOUT = 5 * 60000;
  private static final int READ_TIMEOUT = 5 * 60000;
  private static final int RESOLVE_NODE = 0;
  private static final int RESOLVE_PROPERTIES = 1;
  private static final int RESOLVE_IMPORTS = 2;

  // use a worklist to iteratively resolve all required (parent-/import-) pom.xml files
  // worklist 1 - resolve node in FIFO order (starting with child)
  // worklist 2 - resolve properties in LIFO order (starting with parent)
  // worklist 3 - resolve imports in dependency management
  // worklist 4 - (for dependencies --> that do not came out of the db --> resolve all their direct
  // dependencies)
  private static final int RESOLVE_DIRECT_DEPENDENCIES = 3;
  private final Path TEMP_LOCATION;
  private final DaoMvnArtifactNode daoMvnArtifactNode;
  private final String repoUrl;
  private final Deque<MvnArtifactNode>[] worklist = new Deque[4];

  private final List<MvnArtifactNode> writeToDBList = new ArrayList<>();

  public ArtifactProcessor(DaoMvnArtifactNode doaArtifactNode, String repoUrl) throws IOException {
    TEMP_LOCATION = Files.createTempDirectory(RandomStringUtils.randomAlphabetic(10));
    this.daoMvnArtifactNode = doaArtifactNode;
    this.repoUrl = repoUrl;
    // FIFO queue
    worklist[RESOLVE_NODE] = new ArrayDeque<>();
    // LIFO
    worklist[RESOLVE_PROPERTIES] = new ArrayDeque<>();
    worklist[RESOLVE_IMPORTS] = new ArrayDeque<>();
    worklist[RESOLVE_DIRECT_DEPENDENCIES] = new ArrayDeque<>();
  }

  private void addtoWorklist(MvnArtifactNode node, int resolvinglevel) {
    switch (resolvinglevel) {
      case RESOLVE_NODE:
        // FIFO resolve parents in FIFO order (starting with child)
        worklist[RESOLVE_NODE].addLast(node);
        break;
      case RESOLVE_PROPERTIES:
        // LIFO resolve properties in LIFO order (starting with parent)
        worklist[RESOLVE_PROPERTIES].addFirst(node);
        break;
      case RESOLVE_IMPORTS:
        // LIFO resolve imports in LIFO order (starting with parent) // actually the order is
        // irrelevant here?
        worklist[RESOLVE_IMPORTS].addFirst(node);
        break;
      case RESOLVE_DIRECT_DEPENDENCIES:
        // LIFO resolve imports in LIFO order (starting with parent) // actually the order is
        // irrelevant here?
        worklist[RESOLVE_DIRECT_DEPENDENCIES].addLast(node);
        break;
      default:
        throw new IllegalArgumentException("No valid resolving leven given");
    }
  }

  protected void processResolveWorklist() throws IOException {
    for (int i = RESOLVE_NODE; i <= RESOLVE_DIRECT_DEPENDENCIES; i++) {
      Deque<MvnArtifactNode> currWorklist = worklist[i];
      while (!currWorklist.isEmpty()) {
        MvnArtifactNode mvnNode = currWorklist.pop();
        switch (i) {
          case RESOLVE_NODE:
            final MvnArtifactNode mvnArtifactNode = resolveNode(mvnNode);
            addtoWorklist(mvnArtifactNode, RESOLVE_PROPERTIES);
            break;
          case RESOLVE_PROPERTIES:
            resolvePropertiesOfNodes(mvnNode);
            addtoWorklist(mvnNode, RESOLVE_IMPORTS);
            break;
          case RESOLVE_IMPORTS:
            resolveImportNodes(mvnNode);
            addtoWorklist(mvnNode, RESOLVE_DIRECT_DEPENDENCIES);
            break;
          case RESOLVE_DIRECT_DEPENDENCIES:
            resolveDirectDependencies(mvnNode);
            break;
        }
      }
    }
  }

  private void resolveDirectDependencies(MvnArtifactNode mvnArtifactNode) {

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
      } else if (StringUtils.startsWith(dependencyRelation.getTgtNode().getVersion(), "$")) {
        dependencyPropertiesToResolve.add(dependencyRelation.getTgtNode());
      }
    }

    // all properties should be resolved now, otherwise we are in an inconsistent state
    if (!dependencyPropertiesToResolve.isEmpty()) {
      // we still have unresolved properties?
      LOGGER.error("we still have unresolved properties?");
      throw new IllegalStateException("Properties not resolved. Invalid State");
    }

    // get the dependencyMgt Nodes to check
    // USE as fifo with add and poll since the order matters
    ArrayDeque<MvnArtifactNode> dependencyManagementNodesToCheck = new ArrayDeque<>();
    // resolve parents first <-> since the dependency management may also contain properties that we
    // need to resolve first
    dependencyManagementNodesToCheck.add(mvnArtifactNode);

    // resolve the properties without a version
    while (!dependencyWithoutVersion.isEmpty() && !dependencyManagementNodesToCheck.isEmpty()) {
      final MvnArtifactNode poll = dependencyManagementNodesToCheck.poll();

      poll.getDependencyManagement()
          .sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));

      // default dependencymgt nodes
      final List<DependencyRelation> dependencyMgmNode =
          poll.getDependencyManagement().stream()
              .filter(x -> x.getScope() != DependencyScope.IMPORT)
              .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
              .collect(Collectors.toList());
      for (DependencyRelation dependencyRelation : dependencyMgmNode) {
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

      // TODO -- Check what is resolved first (recursive imports or parent?)
      // search in the parent for dependency mgmt
      if (poll.getParent().isPresent()) {
        dependencyManagementNodesToCheck.add(poll.getParent().get());
      }

      // check if we import other dependency management sections
      // import dependencymgt nodes
      final List<DependencyRelation> importNodes =
          poll.getDependencyManagement().stream()
              .filter(x -> x.getScope() == DependencyScope.IMPORT)
              .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
              .collect(Collectors.toList());

      for (DependencyRelation dependencyRelation : importNodes) {

        final MvnArtifactNode tgtNode = dependencyRelation.getTgtNode();
        // expliclty add add the head of the queue, since this is the closed import
        dependencyManagementNodesToCheck.addFirst(tgtNode);
      }
    }

    if (!dependencyWithoutVersion.isEmpty()) {
      // we still have unresolved properties?
      LOGGER.error("we still have unresolved deps?");
    }
  }

  private void resolveImportNodes(MvnArtifactNode mvnNode) throws IOException {
    LOGGER.info("Resolve Imports: {}", mvnNode);

    final List<DependencyRelation> importNodes =
        mvnNode.getDependencyManagement().stream()
            .filter(x -> x.getScope() == DependencyScope.IMPORT)
            .collect(Collectors.toList());
    // these nodes must be resolved now, after the properties level
    importNodes.stream()
        .map(DependencyRelation::getTgtNode)
        .forEach(x -> addtoWorklist(x, RESOLVE_NODE));

    // TODO:  have to retrigger resolving to completely resolve the new import nodes; maybe there
    // is a nicer approach
    processResolveWorklist();
  }

  private void resolvePropertiesOfNodes(MvnArtifactNode mvnArtifactNode) {
    LOGGER.info("Resolve Properties: {}", mvnArtifactNode);

    Queue<DependencyRelation> dependenciesToCheck = new ArrayDeque<>();
    dependenciesToCheck.addAll(mvnArtifactNode.getDependencies());
    dependenciesToCheck.addAll(mvnArtifactNode.getDependencyManagement());

    Queue<MvnArtifactNode> dependencyPropertiesToResolve = new ArrayDeque<>();
    // also resolve the own version properties?

    while (!dependenciesToCheck.isEmpty()) {
      DependencyRelation dependencyRelation = dependenciesToCheck.poll();
      if (StringUtils.startsWith(dependencyRelation.getTgtNode().getVersion(), "$")) {
        dependencyPropertiesToResolve.add(dependencyRelation.getTgtNode());
      }
    }

    MvnArtifactNode currentNode = mvnArtifactNode;
    while (currentNode != null) {
      // check for properties
      final Map<String, String> properties = mvnArtifactNode.getProperties();

      Deque<MvnArtifactNode> workList = new ArrayDeque<>();
      workList.addAll(dependencyPropertiesToResolve);

      while (!workList.isEmpty()) {
        MvnArtifactNode dep = workList.poll();
        String version = dep.getVersion().substring(2, dep.getVersion().length() - 1);

        // special handling for the property ${project.version}
        // https://maven.apache.org/guides/introduction/introduction-to-the-pom.html#Project_Inheritance
        // One factor to note is that these variables are processed after inheritance as outlined
        // above. This means that if a parent project uses a variable, then its definition in the
        // child, not the parent, will be the one eventually used.
        if (StringUtils.equals(version, "project.version")) {
          // use the lowest child version
          dep.setVersion(mvnArtifactNode.getVersion());
          // fully resolved
          dependencyPropertiesToResolve.remove(dep);

        } else {
          final String s = properties.get(version);
          if (s != null) {
            dep.setVersion(s);
            // check if it another properties
            if (StringUtils.startsWith(s, "$")) {
              // do not remove
              LOGGER.debug("Found recursive property");
              // re-add to the queue
              workList.add(dep);
            } else {
              // fully resolved
              dependencyPropertiesToResolve.remove(dep);
            }
          }
        }
      }
      // search in the parent
      currentNode = currentNode.getParent().orElse(null);
    }
  }

  private MvnArtifactNode resolveNode(MvnArtifactNode mvnNode) throws IOException {
    LOGGER.info("Resolve node: {}", mvnNode);
    // lookup in database if we have the node already
    // return if it already exists
    final Optional<MvnArtifactNode> optionalMvnArtifactNode = daoMvnArtifactNode.get(mvnNode);
    //
    // problem, when we have a "dangling" node in the db.
    // 1. we saw the node as a dependency and added it to the db
    // 2. we return the node here, however, neither its properties nor its dependencies have been
    // resolved before...
    // merge with mvnNode - use the shallow info from the database
    if (optionalMvnArtifactNode.isPresent()
        && optionalMvnArtifactNode.get().getResolvingLevel()
            == MvnArtifactNode.ResolvingLevel.FULL) {
      mvnNode = optionalMvnArtifactNode.get();
    } else {
      // get the pom
      addInfoFromPom(mvnNode);
      mvnNode.setResolvingLevel(MvnArtifactNode.ResolvingLevel.FULL);
      // must be stored to the db later
      writeToDBList.add(mvnNode);
    }
    // put the parent on the worklist
    if (mvnNode.getParent().isPresent()) {
      addtoWorklist(mvnNode.getParent().get(), RESOLVE_NODE);
    }
    return mvnNode;
  }

  @Nullable
  public Collection<MvnArtifactNode> process(CustomArtifactInfo mvenartifactinfo)
      throws IOException {

    LOGGER.info("Start crawling Artifact: {}", mvenartifactinfo);

    MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
    mvnArtifactNode.setGroup(mvenartifactinfo.getGroupId());
    mvnArtifactNode.setArtifact(mvenartifactinfo.getArtifactId());
    mvnArtifactNode.setVersion(mvenartifactinfo.getArtifactVersion());

    mvnArtifactNode.setClassifier(mvenartifactinfo.getClassifier());
    mvnArtifactNode.setPackaging(mvenartifactinfo.getPackaging());
    mvnArtifactNode.setCrawlerVersion(Neo4JConnector.getCrawlerVersion());

    addtoWorklist(mvnArtifactNode, RESOLVE_NODE);

    processResolveWorklist();

    LOGGER.info("Done crawling Artifact: {}", mvenartifactinfo);

    return writeToDBList;
  }

  /**
   * Extract the license and dependency information from the pom.xml file of that artifact and addes
   * them to the mavenArtifactMetadat
   *
   * @param mvnArtifactNode the maven artifact to resolve fully
   */
  public void addInfoFromPom(MvnArtifactNode mvnArtifactNode) throws IOException {

    // Derive pom.xml from info
    CustomArtifactInfo pomInfo = new CustomArtifactInfo();
    pomInfo.setClassifier(mvnArtifactNode.getClassifier());
    pomInfo.setGroupId(mvnArtifactNode.getGroup());
    pomInfo.setArtifactId(mvnArtifactNode.getArtifact());
    pomInfo.setArtifactVersion(mvnArtifactNode.getVersion());
    pomInfo.setRepoURL(this.repoUrl);
    pomInfo.setFileExtension("pom");
    Path pomLocation = null;
    try {
      pomLocation = downloadFilePlainURL(pomInfo, TEMP_LOCATION);

      final MavenProject mavenProject = PomFileUtil.readPom(pomLocation);

      if (mavenProject != null && mavenProject.getModel() != null) {
        final Model model = mavenProject.getModel();
        final Parent mavenProjectParent = model.getParent();

        if (mavenProjectParent != null) {
          // add the parent
          MvnArtifactNode parent = new MvnArtifactNode();

          parent.setGroup(mavenProjectParent.getGroupId());
          parent.setArtifact(mavenProjectParent.getArtifactId());
          parent.setVersion(mavenProjectParent.getVersion());
          parent.setPackaging("pom");
          mvnArtifactNode.setParent(Optional.of(parent));
        }

        HashMap<String, String> newPros = new HashMap<>();
        for (Map.Entry<Object, Object> entry : model.getProperties().entrySet()) {
          newPros.put(entry.getKey().toString(), entry.getValue().toString());
        }
        // add the properties
        mvnArtifactNode.setProperties(newPros);
        // find the dependencies, and add them
        if (model.getDependencies() != null) {
          for (int i = 0; i < model.getDependencies().size(); i++) {
            Dependency mavenDep = model.getDependencies().get(i);
            mvnArtifactNode.getDependencies().add(create(mavenDep, i));
          }
        }
        if (model.getDependencyManagement() != null
            && model.getDependencyManagement().getDependencies() != null) {
          for (int i = 0; i < model.getDependencyManagement().getDependencies().size(); i++) {
            Dependency mavenDep = model.getDependencyManagement().getDependencies().get(i);
            mvnArtifactNode.getDependencyManagement().add(create(mavenDep, i));
          }
        }
      }
    } catch (IOException ex) {
      LOGGER.error("Downloading or parsing of .pom failed with: {}", ex.getMessage());
      throw new IOException("Downloading or parsing of .pom failed with: ", ex);
    } finally {
      // 5. Delete temp folder contents
      try {
        if (pomLocation != null) {
          Files.delete(pomLocation);
        }
      } catch (IOException e) {
        // nothing to do
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

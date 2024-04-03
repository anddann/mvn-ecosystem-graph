package de.upb.maven.ecosystem.crawler.process;

import com.google.common.base.Optional;
import de.upb.maven.ecosystem.AbstractCrawler;
import de.upb.maven.ecosystem.PomFileUtil;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.common.DependencyScope;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.graph.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

public class ArtifactProcessor {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ArtifactProcessor.class);

  private static final int RESOLVE_NODE = 0;
  private static final int RESOLVE_PROPERTIES = 1;
  private static final int RESOLVE_IMPORTS = 2;

  // use a worklist to iteratively resolve all required (parent-/import-) pom.xml files
  // worklist 1 - resolve node in FIFO order (starting with child)
  // worklist 2 - resolve properties in LIFO order (starting with parent)
  // worklist 3 - resolve imports in dependency management
  // worklist 4 - (for dependencies --> that do not come out of the db --> resolve all their direct
  // dependencies)
  private static final int RESOLVE_DIRECT_DEPENDENCIES = 3;

  private final Deque<MvnArtifactNode>[] worklist = new Deque[4];

  private final List<MvnArtifactNode> writeToDBList = new ArrayList<>();

  private final HashMap<String, Integer> internalResolvingLevelHashMap = new HashMap<>();
  private final Pattern PROPERTY_PATTERN = Pattern.compile("(\\$\\{[^\\}]+\\})");
  private final Scene scene;

  public ArtifactProcessor(DaoMvnArtifactNode doaArtifactNode, String repoUrl) throws IOException {
    this.scene = new Scene(repoUrl, doaArtifactNode);
    // FIFO queue
    worklist[RESOLVE_NODE] = new ArrayDeque<>();
    // LIFO
    worklist[RESOLVE_PROPERTIES] = new ArrayDeque<>();
    worklist[RESOLVE_IMPORTS] = new ArrayDeque<>();
    worklist[RESOLVE_DIRECT_DEPENDENCIES] = new ArrayDeque<>();
  }

  private void addtoWorklist(MvnArtifactNode node, int resolvinglevel) {
    String id = Scene.genId(node);
    final Integer currentResolvingLevel = internalResolvingLevelHashMap.getOrDefault(id, -1);
    if (currentResolvingLevel >= resolvinglevel) {
      return;
    }
    switch (resolvinglevel) {
      case RESOLVE_NODE:
        // FIFO resolve parents in FIFO order (starting with child)
        worklist[RESOLVE_NODE].addLast(node);
        internalResolvingLevelHashMap.put(id, RESOLVE_NODE);
        break;
      case RESOLVE_PROPERTIES:
        // LIFO resolve properties in LIFO order (starting with parent)
        worklist[RESOLVE_PROPERTIES].addFirst(node);
        internalResolvingLevelHashMap.put(id, RESOLVE_PROPERTIES);
        break;
      case RESOLVE_IMPORTS:
        // LIFO resolve imports in LIFO order (starting with parent) // actually the order is
        // irrelevant here?
        worklist[RESOLVE_IMPORTS].addFirst(node);
        internalResolvingLevelHashMap.put(id, RESOLVE_IMPORTS);
        break;
      case RESOLVE_DIRECT_DEPENDENCIES:
        // LIFO resolve imports in LIFO order (starting with parent) // actually the order is
        // irrelevant here?
        worklist[RESOLVE_DIRECT_DEPENDENCIES].addLast(node);
        internalResolvingLevelHashMap.put(id, RESOLVE_DIRECT_DEPENDENCIES);
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
            resolveNode(mvnNode);
            addtoWorklist(mvnNode, RESOLVE_PROPERTIES);
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

  private DependencyRelation createCopy(DependencyRelation srcDepRelation)
      throws InvocationTargetException, IllegalAccessException {
    final DependencyRelation newRelation = new DependencyRelation();
    final MvnArtifactNode newMvnNode = new MvnArtifactNode();
    BeanUtils.copyProperties(newMvnNode, srcDepRelation.getTgtNode());
    BeanUtils.copyProperties(newRelation, srcDepRelation);
    newRelation.setTgtNode(newMvnNode);
    return newRelation;
  }

  private void resolveDirectDependencies(MvnArtifactNode mvnArtifactNode) {

    // resolve all direct dependencies using the parent and dependency management edges
    // go through the dependencies and resolve the properties
    Queue<DependencyRelation> dependenciesToCheck = new ArrayDeque<>();
    dependenciesToCheck.addAll(mvnArtifactNode.getDependencies());
    dependenciesToCheck.addAll(mvnArtifactNode.getDependencyManagement());

    Queue<MvnArtifactNode> dependencyPropertiesToResolve = new ArrayDeque<>();
    Queue<DependencyRelation> dependencyWithoutVersion = new ArrayDeque<>();

    while (!dependenciesToCheck.isEmpty()) {
      DependencyRelation dependencyRelation = dependenciesToCheck.poll();
      if (StringUtils.isBlank(dependencyRelation.getTgtNode().getVersion())) {
        dependencyWithoutVersion.add(dependencyRelation);
      } else if (StringUtils.startsWith(dependencyRelation.getTgtNode().getVersion(), "$")) {
        dependencyPropertiesToResolve.add(dependencyRelation.getTgtNode());
      }
    }

    // all properties should be resolved now, otherwise we are in an inconsistent state
    if (!dependencyPropertiesToResolve.isEmpty()) {
      // we still have unresolved properties?
      LOGGER.error("we still have unresolved properties?");
      StringBuilder outout = new StringBuilder();
      for (MvnArtifactNode missingNodes : dependencyPropertiesToResolve) {
        outout
            .append(missingNodes.getGroup())
            .append(":")
            .append(missingNodes.getArtifact())
            .append(":")
            .append(missingNodes.getVersion())
            .append("\n");
      }
      throw new IllegalStateException("Invalid State. Unresolved Property: " + outout);
    }

    // get the dependencyMgt Nodes to check
    // USE as fifo with add and poll since the order matters
    ArrayDeque<MvnArtifactNode> dependencyManagementNodesToCheck = new ArrayDeque<>();
    // resolve parents first <-> since the dependency management may also contain properties that we
    // need to resolve first
    dependencyManagementNodesToCheck.add(mvnArtifactNode);

    // TODO - refactor
    // avoid circles
    HashSet<MvnArtifactNode> alreadyCheckedNodes = new HashSet<>();
    // resolve the properties without a version
    while (!dependencyManagementNodesToCheck.isEmpty() || !dependencyWithoutVersion.isEmpty()) {
      final MvnArtifactNode poll = dependencyManagementNodesToCheck.poll();
      if (poll == null) {
        // list is empty, we did not find a version
        break;
      }

      alreadyCheckedNodes.add(poll);

      poll.getDependencyManagement()
          .sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));

      // default dependencymgt nodes
      final List<DependencyRelation> dependencyMgmNode =
          poll.getDependencyManagement().stream()
              .filter(x -> x.getScope() != DependencyScope.IMPORT)
              .sorted(Comparator.comparingInt(DependencyRelation::getPosition))
              .collect(Collectors.toList());

      HashMap<DependencyRelation, Deque<DependencyRelation>> depWithOutVersionDependencyMgmtEdge =
          new HashMap<>();
      // find the dependency mgmt nodes that specify the version
      for (Iterator<DependencyRelation> iteratorDep = dependencyWithoutVersion.iterator();
          iteratorDep.hasNext(); ) {
        final DependencyRelation nextDep = iteratorDep.next();

        for (Iterator<DependencyRelation> iteratorDepMgmt = dependencyMgmNode.iterator();
            iteratorDepMgmt.hasNext(); ) {

          final DependencyRelation nextDepMgmt = iteratorDepMgmt.next();
          if (StringUtils.equals(
                  nextDep.getTgtNode().getGroup(), nextDepMgmt.getTgtNode().getGroup())
              && StringUtils.equals(
                  nextDep.getTgtNode().getArtifact(), nextDepMgmt.getTgtNode().getArtifact())
              && StringUtils.equals(nextDep.getTgtNode().getPackaging(), nextDepMgmt.getType())) {
            final Deque<DependencyRelation> orDefault =
                depWithOutVersionDependencyMgmtEdge.computeIfAbsent(
                    nextDep, x -> new ArrayDeque<>());
            orDefault.push(nextDepMgmt);
            // we found a depmgmt node
            // do not remove the node, to handle duplicate dependencies without a version, @see test
            // case
            // de.upb.maven.ecosystem.crawler.process.ArtifactProcessorTest.testDuplicateDependencyWithoutVersion
            // iteratorDepMgmt.remove();
          }
        }
      }

      ArrayList<DependencyRelation> newProfileDependencies = new ArrayList<>();

      // up-date dependencies and copy if needed
      for (Map.Entry<DependencyRelation, Deque<DependencyRelation>> entry :
          depWithOutVersionDependencyMgmtEdge.entrySet()) {
        DependencyRelation dependencyRelationWithOutVersion = entry.getKey();
        final Deque<DependencyRelation> depMgmtList = entry.getValue();

        // if we have multiple edges found // we need to duplicate it
        final Deque<DependencyRelation> relationsToRefile = new ArrayDeque<>();
        relationsToRefile.add(dependencyRelationWithOutVersion);

        while (relationsToRefile.size() < depMgmtList.size()) {
          // create new relatios if required
          // create a copy
          try {
            final DependencyRelation copy = createCopy(dependencyRelationWithOutVersion);
            newProfileDependencies.add(copy);
            relationsToRefile.add(copy);
          } catch (InvocationTargetException | IllegalAccessException e) {
            LOGGER.error("Create copy failed");
          }
        }

        while (!depMgmtList.isEmpty() && !relationsToRefile.isEmpty()) {
          DependencyRelation mgmtNode = depMgmtList.poll();
          final DependencyRelation relationToResolve = relationsToRefile.poll();

          relationToResolve.getTgtNode().setVersion(mgmtNode.getTgtNode().getVersion());
          // set the profile
          relationToResolve.setProfile(mgmtNode.getProfile());
        }
      }

      // add the new found deps
      mvnArtifactNode.getDependencies().addAll(newProfileDependencies);

      // TODO -- Check what is resolved first (recursive imports or parent?)
      // search in the parent for dependency mgmt
      if (poll.getParent().isPresent()) {
        final MvnArtifactNode e = poll.getParent().get();
        if (!alreadyCheckedNodes.contains(e)) {
          dependencyManagementNodesToCheck.add(e);
        }
      }

      // check if we import other dependency management sections
      // import dependencymgt nodes
      final List<DependencyRelation> importNodes =
          poll.getDependencyManagement().stream()
              .filter(x -> x.getScope() == DependencyScope.IMPORT)
              .sorted(Comparator.comparingInt(DependencyRelation::getPosition))
              .collect(Collectors.toList());

      for (DependencyRelation dependencyRelation : importNodes) {

        final MvnArtifactNode tgtNode = dependencyRelation.getTgtNode();
        // expliclty add add the head of the queue, since this is the closed import
        if (!alreadyCheckedNodes.contains(tgtNode)) {
          dependencyManagementNodesToCheck.addFirst(tgtNode);
        }
      }
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

  private String resolveProperty(
      String prop,
      final MvnArtifactNode currentNode,
      Map<String, String> mavenpropertiestocheck,
      HashSet<String> resolvedProperties) {
    if (StringUtils.isBlank(prop)) {
      return prop;
    }
    if (resolvedProperties.contains(prop)) {
      return prop;
    }
    resolvedProperties.add(prop);

    String newString = prop;
    final Matcher matcher = PROPERTY_PATTERN.matcher(prop);
    // special handling for the property ${project.version}, ...
    // https://maven.apache.org/guides/introduction/introduction-to-the-pom.html#Project_Inheritance
    // One factor to note is that these variables are processed after inheritance as outlined
    // above. This means that if a parent project uses a variable, then its definition in the
    // child, not the parent, will be the one eventually used.

    // add handling for parent properties... of the form ${parent.project.version}
    MvnArtifactNode nodePropertiesToUse = currentNode;

    while (matcher.find()) {

      final String group = matcher.group();
      String porName = group.substring(2, group.length() - 1);

      if (StringUtils.startsWith(porName, "project.parent.")
          || StringUtils.startsWith(porName, "parent.")) {
        // use the parent for resolving
        porName = porName.replaceFirst(".*parent\\.", "");

        final Optional<MvnArtifactNode> parent = currentNode.getParent();
        if (!parent.isPresent()) {
          throw new IllegalStateException(
              "Parent Properties request, but parent not present. Invalid State");
        }
        nodePropertiesToUse = parent.get();
      }
      if (StringUtils.equals(porName, "project.groupId")
          || StringUtils.equals(porName, "pom.groupId")
          || StringUtils.equals(porName, "groupId")) {
        newString = newString.replace(group, nodePropertiesToUse.getGroup());

      } else if (StringUtils.equals(porName, "project.name")
          || StringUtils.equals(porName, "pom.name")
          || StringUtils.equals(porName, "artifactId")) {
        newString = newString.replace(group, nodePropertiesToUse.getArtifact());

      } else if (StringUtils.equals(porName, "project.version")
          || StringUtils.equals(porName, "pom.version")
          || StringUtils.equals(porName, "version")) {
        newString = newString.replace(group, nodePropertiesToUse.getVersion());
      } else {
        final String s = mavenpropertiestocheck.get(porName);
        if (s != null) {
          newString = newString.replace(group, s);
        }
      }

      // reset to the original node
      nodePropertiesToUse = currentNode;
    }
    // re-trigger to resolve recursive-properties
    return resolveProperty(newString, currentNode, mavenpropertiestocheck, resolvedProperties);
  }

  private void resolvePropertiesOfNodes(MvnArtifactNode mvnArtifactNode) {
    LOGGER.info("Resolve Properties: {}", mvnArtifactNode);

    Queue<DependencyRelation> dependenciesToCheck = new ArrayDeque<>();
    dependenciesToCheck.addAll(mvnArtifactNode.getDependencies());
    dependenciesToCheck.addAll(mvnArtifactNode.getDependencyManagement());

    Queue<DependencyRelation> dependencyPropertiesToResolve = new ArrayDeque<>();
    // also resolve the own version properties?

    while (!dependenciesToCheck.isEmpty()) {
      DependencyRelation dependencyRelation = dependenciesToCheck.poll();
      if (StringUtils.contains(dependencyRelation.getTgtNode().getVersion(), "$")) {
        dependencyPropertiesToResolve.add(dependencyRelation);
      } else if (StringUtils.contains(dependencyRelation.getTgtNode().getGroup(), "$")) {
        dependencyPropertiesToResolve.add(dependencyRelation);
      } else if (StringUtils.contains(dependencyRelation.getTgtNode().getArtifact(), "$")) {
        dependencyPropertiesToResolve.add(dependencyRelation);
      } else if (StringUtils.contains(dependencyRelation.getTgtNode().getClassifier(), "$")) {
        dependencyPropertiesToResolve.add(dependencyRelation);
      }
    }

    // TODO: One factor to note is that these variables are processed after inheritance as
    // outlined above. This means that if a parent project uses a variable, then its
    // definition in the child, not the parent, will be the one eventually used.
    // https://maven.apache.org/guides/introduction/introduction-to-the-pom.html#Project_Inheritance
    // Thus maybe, we should resolve all parents first and then create an aggregate set of
    // properties... :(

    MvnArtifactNode currentNode = mvnArtifactNode;
    // sometimes artifacts have a cycles, thus, we break it up here
    HashSet<MvnArtifactNode> workedNodes = new HashSet<>();
    while (currentNode != null && workedNodes.add(currentNode)) {
      // check for properties

      Deque<DependencyRelation> workList = new ArrayDeque<>(dependencyPropertiesToResolve);

      while (!workList.isEmpty()) {
        final DependencyRelation poll = workList.poll();
        MvnArtifactNode dep = poll.getTgtNode();

        String resolvedVersion =
            resolveProperty(
                dep.getVersion(), currentNode, currentNode.getProperties(), new HashSet<>());
        String resolvedGroup =
            resolveProperty(
                dep.getGroup(), currentNode, currentNode.getProperties(), new HashSet<>());
        String resolvedArtifact =
            resolveProperty(
                dep.getArtifact(), currentNode, currentNode.getProperties(), new HashSet<>());
        String resolvedClassifier =
            resolveProperty(
                dep.getClassifier(), currentNode, currentNode.getProperties(), new HashSet<>());

        dep.setGroup(resolvedGroup);
        dep.setArtifact(resolvedArtifact);
        dep.setVersion(resolvedVersion);

        // safety check - maybe superflous
        dep.setClassifier(resolvedClassifier);
        // also set the classifier in the dependency relation
        poll.setClassifier(resolvedClassifier);

        // check if the artifact is now fully resolved

        if (isFullyResolved(dep)) {
          dependencyPropertiesToResolve.remove(dep);
        } else {

          // else we have to check if it defined in a profile
          final Model model = this.scene.nodeToModelGetOrFetchModel(currentNode);
          boolean isDirectDependency = mvnArtifactNode.getDependencies().contains(poll);

          for (Profile profile : model.getProfiles()) {
            String profileName = profile.getId();
            try {
              // copy for each profile
              final DependencyRelation profileDepRelation = createCopy(poll);
              profileDepRelation.setProfile(profileName);

              final MvnArtifactNode profileDep = profileDepRelation.getTgtNode();

              HashMap<String, String> newPros = new HashMap<>();
              for (Map.Entry<Object, Object> entry : profile.getProperties().entrySet()) {
                newPros.put(entry.getKey().toString(), entry.getValue().toString());
              }
              // also add the original properties
              newPros.putAll(currentNode.getProperties());

              resolvedVersion =
                  resolveProperty(profileDep.getVersion(), currentNode, newPros, new HashSet<>());
              resolvedGroup =
                  resolveProperty(profileDep.getGroup(), currentNode, newPros, new HashSet<>());
              resolvedArtifact =
                  resolveProperty(profileDep.getArtifact(), currentNode, newPros, new HashSet<>());

              resolvedClassifier =
                  resolveProperty(
                      dep.getClassifier(),
                      currentNode,
                      currentNode.getProperties(),
                      new HashSet<>());

              profileDep.setGroup(resolvedGroup);
              profileDep.setArtifact(resolvedArtifact);
              profileDep.setVersion(resolvedVersion);
              profileDep.setClassifier(resolvedClassifier);
              // also set the classifier in the dependency relation
              profileDepRelation.setClassifier(resolvedClassifier);

              if (isFullyResolved(profileDep)) {

                dependencyPropertiesToResolve.remove(poll);
                // remove the old one from dependencies
                mvnArtifactNode.getDependencies().remove(poll);
                mvnArtifactNode.getDependencyManagement().remove(poll);

                if (isDirectDependency) {
                  mvnArtifactNode.getDependencies().add(profileDepRelation);

                } else {
                  mvnArtifactNode.getDependencyManagement().add(profileDepRelation);
                }
              }
            } catch (IllegalAccessException | InvocationTargetException ex) {
              LOGGER.error("Cloning went wrong");
            }
          }
        }
      }
      // search in the parent
      currentNode = currentNode.getParent().orNull();
    }
  }

  private boolean isFullyResolved(MvnArtifactNode dep) {
    boolean fullyResolved =
        !StringUtils.contains(dep.getGroup(), "$")
            && !StringUtils.contains(dep.getArtifact(), "$")
            && !StringUtils.contains(dep.getVersion(), "$");
    return fullyResolved;
  }

  private void resolveNode(MvnArtifactNode mvnNode) throws IOException {
    LOGGER.info("Resolve node: {}", mvnNode);
    // return if it already fully resolved
    if (mvnNode.getResolvingLevel() == MvnArtifactNode.ResolvingLevel.FULL) {
      return;
    }

    // get the pom
    addInfoFromPom(mvnNode);
    mvnNode.setResolvingLevel(MvnArtifactNode.ResolvingLevel.FULL);
    // must be stored to the db later
    writeToDBList.add(mvnNode);

    // put the parent on the worklist
    if (mvnNode.getParent().isPresent()) {
      addtoWorklist(mvnNode.getParent().get(), RESOLVE_NODE);
    }
  }

  @Nullable
  public Collection<MvnArtifactNode> process(CustomArtifactInfo mvenartifactinfo)
      throws IOException {

    LOGGER.info("Start crawling Artifact: {}", mvenartifactinfo);

    MvnArtifactNode mvnArtifactNode =
        scene.makeNodeRef(
            mvenartifactinfo.getGroupId(),
            mvenartifactinfo.getArtifactId(),
            mvenartifactinfo.getArtifactVersion(),
            mvenartifactinfo.getClassifier(),
            mvenartifactinfo.getPackaging());
    mvnArtifactNode.setCrawlerVersion(AbstractCrawler.getCrawlerVersion());

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
    CustomArtifactInfo pomInfo = scene.getCustomArtifactInfo(mvnArtifactNode);
    Path pomLocation = null;
    try {
      pomLocation = scene.downloadFilePlainURL(pomInfo);

      final MavenProject mavenProject = PomFileUtil.readPom(pomLocation);

      if (mavenProject != null && mavenProject.getModel() != null) {
        final Model model = mavenProject.getModel();
        // add to the hashset - to get profile information later (easily)
        scene.add(mvnArtifactNode, model);

        final Parent mavenProjectParent = model.getParent();

        if (mavenProjectParent != null) {
          // add the parent
          MvnArtifactNode parent =
              scene.makeNodeRef(
                  mavenProjectParent.getGroupId(),
                  mavenProjectParent.getArtifactId(),
                  mavenProjectParent.getVersion(),
                  "null",
                  "pom");
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
          final List<DependencyRelation> dependencyRelations =
              createDependencyRelations(model.getDependencies());
          mvnArtifactNode.getDependencies().addAll(dependencyRelations);
        }
        if (model.getDependencyManagement() != null
            && model.getDependencyManagement().getDependencies() != null) {
          final List<DependencyRelation> dependencyRelations =
              createDependencyRelations(model.getDependencyManagement().getDependencies());
          mvnArtifactNode.getDependencyManagement().addAll(dependencyRelations);
        }

        // add for each profile a node
        // TODO - better profile handling
        if (model.getProfiles() != null) {
          for (Profile profile : model.getProfiles()) {

            if (profile.getDependencies() != null && profile.getDependencies() != null) {
              final List<DependencyRelation> dependencyRelations =
                  createDependencyRelations(profile.getDependencies());
              dependencyRelations.forEach(x -> x.setProfile(profile.getId()));
              mvnArtifactNode.getDependencies().addAll(dependencyRelations);
            }

            if (profile.getDependencyManagement() != null
                && profile.getDependencyManagement().getDependencies() != null) {
              final List<DependencyRelation> dependencyRelations =
                  createDependencyRelations(profile.getDependencyManagement().getDependencies());
              dependencyRelations.forEach(x -> x.setProfile(profile.getId()));
              mvnArtifactNode.getDependencyManagement().addAll(dependencyRelations);
            }
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

  private List<DependencyRelation> createDependencyRelations(List<Dependency> dependencies) {
    ArrayList<DependencyRelation> dependencyRelations = new ArrayList<>();
    for (int i = 0; i < dependencies.size(); i++) {
      Dependency mavenDep = dependencies.get(i);
      dependencyRelations.add(create(mavenDep, i));
    }
    return dependencyRelations;
  }

  private DependencyRelation create(Dependency mavenDep, int position) {
    MvnArtifactNode dependency =
        scene.makeNodeRef(
            mavenDep.getGroupId(),
            mavenDep.getArtifactId(),
            mavenDep.getVersion(),
            mavenDep.getClassifier(),
            mavenDep.getType());

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
    // the default scope
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
}

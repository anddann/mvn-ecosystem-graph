package de.upb.maven.ecosystem.persistence.dao;

import static java.util.stream.Collectors.groupingBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.persistence.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.model.DependencyScope;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoaMvnArtifactNodeImpl implements DaoMvnArtifactNode {

  private static final Logger logger = LoggerFactory.getLogger(DoaMvnArtifactNodeImpl.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final Driver driver;

  public DoaMvnArtifactNodeImpl(Driver driver) {
    this.driver = driver;
    try {
      this.createUniqueConstraint();
    } catch (ClientException ex) {
      // may fail on embedded database checks
      logger.error("Failed create constraint", ex);
    }
  }

  private void createUniqueConstraint() {
    try (Session session = driver.session()) {
      try (Transaction tx = session.beginTransaction()) {

        // add unique constraints
        tx.run(
            "CREATE CONSTRAINT uni_mvnartifact_hashids IF NOT EXISTS FOR (node:MvnArtifact) REQUIRE node.hashId IS UNIQUE");
        // add index for lookup - increases speed heavily
        tx.run(
            "CREATE INDEX gavcp_index IF NOT EXISTS FOR (n:MvnArtifact) ON (n.group, n.artifact, n.version, n.classifier, n.packaging)");
        tx.commit();
      }
    }
  }

  private static String getGav(MvnArtifactNode node) {
    return node.getGroup()
        + ":"
        + node.getArtifact()
        + ":"
        + node.getVersion()
        + "-"
        + node.getClassifier();
  }

  private static void sanityCheckProperties(MvnArtifactNode mvnArtifactNode) {
    if (StringUtils.isBlank(mvnArtifactNode.getGroup())
        || StringUtils.startsWith(mvnArtifactNode.getGroup(), "$")) {
      // the parent artifact may only have the packaging pom
      logger.error(
          "The group is invalid: {} -- {}", getGav(mvnArtifactNode), mvnArtifactNode.getGroup());
      throw new IllegalArgumentException("The group is invalid");
    }
    if (StringUtils.isBlank(mvnArtifactNode.getArtifact())
        || StringUtils.startsWith(mvnArtifactNode.getArtifact(), "$")) {
      // the parent artifact may only have the packaging pom
      logger.error(
          "The artifact is invalid: {} -- {}",
          getGav(mvnArtifactNode),
          mvnArtifactNode.getArtifact());
      throw new IllegalArgumentException("The artifact is invalid");
    }
    if (StringUtils.isBlank(mvnArtifactNode.getVersion())
        || StringUtils.startsWith(mvnArtifactNode.getVersion(), "$")) {
      // the parent artifact may only have the packaging pom
      logger.error(
          "The version is invalid: {} -- {}",
          getGav(mvnArtifactNode),
          mvnArtifactNode.getVersion());
      throw new IllegalArgumentException("The version is invalid");
    }

    if (StringUtils.isBlank(mvnArtifactNode.getClassifier())
        || StringUtils.startsWith(mvnArtifactNode.getClassifier(), "$")) {
      // the parent artifact may only have the packaging pom
      logger.error(
          "The classifier is invalid:  {} -- {}",
          getGav(mvnArtifactNode),
          mvnArtifactNode.getClassifier());
      throw new IllegalArgumentException("The classifier is invalid");
    }
  }

  /**
   * sanity check of the data to write into the database
   *
   * @param mvnArtifactNode
   * @throws IllegalArgumentException
   */
  public static void sanityCheck(MvnArtifactNode mvnArtifactNode) throws IllegalArgumentException {

    if (mvnArtifactNode.getParent().isPresent()
        && !StringUtils.equals(mvnArtifactNode.getParent().get().getPackaging(), "pom")) {
      // the parent artifact may only have the packaging pom
      logger.error("The packaging of the parent is invalid");
      throw new IllegalArgumentException("A Maven parent must have packaging: pom");
    }
    // validate direct dependencies
    for (DependencyRelation dependencyRelation : mvnArtifactNode.getDependencies()) {
      // check for sanity
      if (!StringUtils.equals(
          dependencyRelation.getClassifier(), dependencyRelation.getTgtNode().getClassifier())) {
        logger.error("The dependency relation classifier do not match");
        throw new IllegalArgumentException("The dependency relation classifier do not match");
      }
      if (!StringUtils.equals(
          dependencyRelation.getType(), dependencyRelation.getTgtNode().getPackaging())) {
        logger.error("The dependency relation type/packaging do not match");
        throw new IllegalArgumentException("The dependency relation type/packaging do not match");
      }
    }

    for (DependencyRelation dependencyRelation : mvnArtifactNode.getDependencyManagement()) {
      // check for sanity
      if (!StringUtils.equals(
          dependencyRelation.getClassifier(), dependencyRelation.getTgtNode().getClassifier())) {
        logger.error("The import dependency relation classifier do not match");
        throw new IllegalArgumentException("The dependency relation classifier do not match");
      }
      if (!StringUtils.equals(
          dependencyRelation.getType(), dependencyRelation.getTgtNode().getPackaging())) {
        logger.error("The import dependency relation type/packaging do not match");
        throw new IllegalArgumentException("The dependency relation type/packaging do not match");
      }
      if (dependencyRelation.getScope() == DependencyScope.IMPORT
          && !StringUtils.equals(dependencyRelation.getType(), "pom")) {
        logger.error("The import dependency relation type do not match");
        throw new IllegalArgumentException("The import dependency relation type do not match");
      }
      if (dependencyRelation.getScope() == DependencyScope.IMPORT
          && !StringUtils.equals(dependencyRelation.getTgtNode().getPackaging(), "pom")) {
        logger.error("The import dependency has not packaging: pom");
        throw new IllegalArgumentException("The import dependency has not packaging: pom");
      }
    }
    {
      // check position of dependencies
      final Map<Integer, List<DependencyRelation>> collect =
          mvnArtifactNode.getDependencies().stream()
              .collect(groupingBy(DependencyRelation::getPosition));
      HashSet<Integer> seenPos = new HashSet<>();
      for (Map.Entry<Integer, List<DependencyRelation>> entry : collect.entrySet()) {

        if (!seenPos.add(entry.getKey())) {
          logger.error("Position of dependency is incorrect");
          throw new IllegalArgumentException("Position of dependency is incorrect");
        }
        // if multiple with same position different profiles
        final long count = entry.getValue().stream().map(x -> x.getProfile()).count();
        if (count != entry.getValue().size()) {
          logger.error("Position of dependency is incorrect");
          throw new IllegalArgumentException("Position of dependency is incorrect");
        }
      }
      // check if the numbers are consecutive

      for (int i = 0; i < seenPos.size(); i++) {
        final boolean contains = seenPos.contains(i);
        if (!contains) {
          logger.error("Position of dependency is incorrect");
          throw new IllegalArgumentException("Position of dependency is incorrect");
        }
      }
    }
    {
      // check position of dependencies mgmg
      final Map<Integer, List<DependencyRelation>> collect =
          mvnArtifactNode.getDependencyManagement().stream()
              .collect(groupingBy(DependencyRelation::getPosition));
      HashSet<Integer> seenPos = new HashSet<>();
      for (Map.Entry<Integer, List<DependencyRelation>> entry : collect.entrySet()) {

        if (!seenPos.add(entry.getKey())) {
          logger.error("Position of dependency is incorrect");
          throw new IllegalArgumentException("Position of dependency is incorrect");
        }
        // if multiple with same position different profiles
        final long count = entry.getValue().stream().map(x -> x.getProfile()).count();
        if (count != entry.getValue().size()) {
          logger.error("Position of dependency is incorrect");
          throw new IllegalArgumentException("Position of dependency is incorrect");
        }
      }
      // check if the numbers are consecutive
      for (int i = 0; i < seenPos.size(); i++) {
        final boolean contains = seenPos.contains(i);
        if (!contains) {
          logger.error("Position of dependency is incorrect");
          throw new IllegalArgumentException("Position of dependency is incorrect");
        }
      }
    }

    if (StringUtils.isBlank(mvnArtifactNode.getPackaging())) {
      logger.error("Packaging is blank");
      throw new IllegalArgumentException("Packaging is blank");
    }

    // check nodes for sanity
    List<MvnArtifactNode> mvnArtifactNodes = new ArrayList<>();
    mvnArtifactNodes.add(mvnArtifactNode);
    mvnArtifactNodes.addAll(
        mvnArtifactNode.getDependencies().stream()
            .map(DependencyRelation::getTgtNode)
            .collect(Collectors.toList()));
    mvnArtifactNodes.addAll(
        mvnArtifactNode.getDependencyManagement().stream()
            .map(DependencyRelation::getTgtNode)
            .collect(Collectors.toList()));
    for (MvnArtifactNode nodeToCheck : mvnArtifactNodes) {
      sanityCheckProperties(nodeToCheck);
    }
  }

  /**
   * Uses Jackson to fill property set for Neo4j. Collections and complex objects will be serialized
   * as JSON for now as Neo4j does not support them as properties.
   *
   * @param entity
   * @return
   */
  @NotNull
  private Map<String, Object> createProperties(Object entity) {
    // return OBJECT_MAPPER.convertValue(entity, new TypeReference<Map<String, Object>>() {});

    Map<String, Object> properties = new HashMap<>();

    JsonNode objectNode = OBJECT_MAPPER.valueToTree(entity);

    for (Iterator<Map.Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> next = it.next();

      String key = next.getKey();
      JsonNode value = next.getValue();

      if (value.isContainerNode()) {
        properties.put(key + "_json", value.toString());
      } else {
        try {
          properties.put(key, OBJECT_MAPPER.treeToValue(value, Object.class));
        } catch (JsonProcessingException e) {
          logger.warn("Error deserializing primitive value " + value, e);
        }
      }
    }
    return properties;
  }

  @Override
  public Optional<MvnArtifactNode> get(long id) {
    throw new UnsupportedOperationException("getAll() not implemented");
  }

  @Override
  public Optional<MvnArtifactNode> get(MvnArtifactNode instance) {
    //  give back a proxy element to resolve getParent and getDependency on demand ... also

    Map<String, Object> parameters = new HashMap<>();
    // match based on gav, classifier
    String query =
        "MATCH (n:MvnArtifact {group:$group, artifact:$artifact, version:$version, classifier:$classifier, packaging:$packaging}) RETURN n";

    parameters.put("group", instance.getGroup());
    parameters.put("artifact", instance.getArtifact());
    parameters.put("version", instance.getVersion());
    parameters.put("classifier", instance.getClassifier());
    parameters.put("packaging", instance.getPackaging());

    try (Session session = driver.session()) {
      final Record record =
          session.readTransaction(
              tx -> {
                Result result = tx.run(query, parameters);
                if (result == null) {
                  return null;
                }
                if (result.hasNext()) {
                  final Record single = result.single();
                  return single;
                } else {
                  return null;
                }
              });

      if (record == null) {
        return Optional.absent();
      }

      final Value n = record.get("n");
      if (n != null) {

        return Optional.of(createProxyNode(n));
      }
      return Optional.absent();
    }
  }

  @Override
  public List<MvnArtifactNode> getAll() {
    throw new UnsupportedOperationException("getAll() not implemented");
  }

  @Override
  public void save(MvnArtifactNode mvnArtifactNode) {
    throw new UnsupportedOperationException("getAll() not implemented");
  }

  private <T> void write(Collection<T> entities, Session session, Function<T, Query> queryFactory) {
    try (Transaction tx = session.beginTransaction()) {
      for (T entity : entities) {
        if (entity == null) {
          continue;
        }

        Query query = queryFactory.apply(entity);
        tx.run(query);
      }
      tx.commit();
    }
  }

  private <T, U> void write(
      T srcNode, Collection<U> entities, Session session, BiFunction<T, U, Query> queryFactory) {
    try (Transaction tx = session.beginTransaction()) {
      for (U entity : entities) {
        if (entity == null) {
          continue;
        }

        Query query = queryFactory.apply(srcNode, entity);
        tx.run(query);
      }
      tx.commit();
    }
  }

  // While it usually represents the extension on the filename of the dependency, that is not always
  // the case: a type can be mapped to a different extension and a classifier. The type often
  // corresponds to the packaging used, though this is also not always the case.

  private Query createNodeQuery(MvnArtifactNode node) {
    Map<String, Object> parameters = new HashMap<>();

    String query =
        "MERGE (n:MvnArtifact {group:$group, artifact:$artifact, version:$version, classifier:$classifier, packaging:$packaging})"
            + " ON CREATE SET n = $props";
    if (node.getResolvingLevel() == MvnArtifactNode.ResolvingLevel.FULL) {
      // only override if the resolving level of this node is full
      query += " ON MATCH SET n += $props";
    }
    query += " RETURN n";

    Map<String, Object> properties = createProperties(node);

    parameters.put("props", properties);
    parameters.put("group", node.getGroup());
    parameters.put("artifact", node.getArtifact());
    parameters.put("version", node.getVersion());
    parameters.put("classifier", node.getClassifier());
    parameters.put("packaging", node.getPackaging());

    return new Query(query, parameters);
  }

  private Query createDependencyManagementRelationQuery(
      MvnArtifactNode srcNode, DependencyRelation dependencyRelation) {
    // Tdo distinguish IMPORT vs normal dependency_management edges
    Map<String, Object> parameters = new HashMap<>();

    StringBuilder query = new StringBuilder();
    query.append("MATCH (src:MvnArtifact), (tgt:MvnArtifact)");
    query
        .append(" WHERE ")
        .append(createMatchingCondition(srcNode, "src", "src.", parameters))
        .append(" AND ")
        .append(
            createMatchingCondition(dependencyRelation.getTgtNode(), "tgt", "tgt.", parameters));

    if (dependencyRelation.getScope() == DependencyScope.IMPORT) {
      query.append(" MERGE (src)-[r:IMPORTS]->(tgt)").append(" ON CREATE SET r = $props");
    } else {
      query.append(" MERGE (src)-[r:MANAGES]->(tgt)").append(" ON CREATE SET r = $props");
    }

    Map<String, Object> properties = createProperties(dependencyRelation);

    parameters.put("props", properties);

    return new Query(query.toString(), parameters);
  }

  private Query createDirectDependencyRelationQuery(
      MvnArtifactNode srcNode, DependencyRelation dependencyRelation) {

    Map<String, Object> parameters = new HashMap<>();

    StringBuilder query = new StringBuilder();
    query
        .append("MATCH (src:MvnArtifact), (tgt:MvnArtifact)")
        .append(" WHERE ")
        .append(createMatchingCondition(srcNode, "src", "src.", parameters))
        .append(" AND ")
        .append(createMatchingCondition(dependencyRelation.getTgtNode(), "tgt", "tgt.", parameters))
        .append(" MERGE (src)-[r:DEPENDS_ON]->(tgt)")
        .append(" ON CREATE SET r = $props");

    Map<String, Object> properties = createProperties(dependencyRelation);

    parameters.put("props", properties);

    return new Query(query.toString(), parameters);
  }

  private Query createParentRelationQuery(MvnArtifactNode srcNode, MvnArtifactNode parent) {
    HashMap<String, Object> parameters = new HashMap<>();

    StringBuilder query = new StringBuilder();
    query
        .append("MATCH (src:MvnArtifact), (parent:MvnArtifact)")
        .append(" WHERE ")
        .append(createMatchingCondition(srcNode, "src", "src.", parameters))
        .append(" AND ")
        .append(createMatchingCondition(parent, "parent", "parent.", parameters))
        .append(" MERGE (parent)-[r:PARENT]->(src)");

    return new Query(query.toString(), parameters);
  }

  @Override
  public void saveOrMerge(MvnArtifactNode instance) {

    // do not re-run for fully resolved proxy nodes
    if (instance instanceof MvnArtifactNodeProxy
        && instance.getResolvingLevel() == MvnArtifactNode.ResolvingLevel.FULL) {
      return;
    }

    sanityCheck(instance);

    Stopwatch sw = Stopwatch.createStarted();

    try (Session session = driver.session()) {

      // create the node itself
      write(Collections.singleton(instance), session, this::createNodeQuery);

      if (instance.getParent().isPresent()) {
        // create the parent node

        write(Collections.singleton(instance.getParent().get()), session, this::createNodeQuery);

        // create the parent relationship
        write(
            instance,
            Collections.singleton(instance.getParent().get()),
            session,
            this::createParentRelationQuery);
      }
      // create the dependency nodes
      write(
          instance.getDependencies().stream()
              .map(DependencyRelation::getTgtNode)
              .collect(Collectors.toList()),
          session,
          this::createNodeQuery);

      // create the dependency management nodes
      write(
          instance.getDependencyManagement().stream()
              .map(DependencyRelation::getTgtNode)
              .collect(Collectors.toList()),
          session,
          this::createNodeQuery);

      // create the dependency management relationship
      write(
          instance,
          instance.getDependencyManagement(),
          session,
          this::createDependencyManagementRelationQuery);

      // create the direct dependency relationship
      write(
          instance, instance.getDependencies(), session, this::createDirectDependencyRelationQuery);
    }

    logger.info("Finished writing node to Neo4j in {}", sw.elapsed(TimeUnit.MILLISECONDS));
  }

  @Override
  public void update(MvnArtifactNode mvnArtifactNode, String[] params) {
    throw new UnsupportedOperationException("getAll() not implemented");
  }

  @Override
  public void delete(MvnArtifactNode mvnArtifactNode) {
    throw new UnsupportedOperationException("getAll() not implemented");
  }

  @Override
  public Optional<MvnArtifactNode> getParent(long id) {
    throw new UnsupportedOperationException("getAll() not implemented");
  }

  @Override
  public Optional<MvnArtifactNode> getParent(MvnArtifactNode instance) {

    HashMap<String, Object> parameters = new HashMap<>();

    StringBuilder query = new StringBuilder();
    query
        .append("MATCH (src:MvnArtifact), (parent:MvnArtifact)")
        .append(" WHERE ")
        .append(createMatchingCondition(instance, "src", "src.", parameters))
        .append(" AND parent.packaging = \"pom\"")
        .append(" MATCH (parent)-[r:PARENT]->(src)")
        .append(" RETURN parent");
    try (Session session = driver.session()) {
      final Value value =
          session.readTransaction(
              tx -> {
                Result result = tx.run(query.toString(), parameters);
                if (result == null) {
                  return null;
                }
                if (result.hasNext()) {
                  return result.single().get(0);
                } else {
                  return null;
                }
              });

      if (value == null) {
        return Optional.absent();
      }

      return Optional.of(createProxyNode(value));
    }
  }

  @Override
  public Optional<DependencyRelation> getRelationship(
      MvnArtifactNode instance, MvnArtifactNode dependency) {

    HashMap<String, Object> parameters = new HashMap<>();

    StringBuilder query = new StringBuilder();
    query
        .append("MATCH (src:MvnArtifact), (tgt:MvnArtifact)")
        .append(" WHERE ")
        .append(createMatchingCondition(instance, "src", "src.", parameters))
        .append(" AND ")
        .append(createMatchingCondition(dependency, "tgt", "tgt.", parameters))
        .append(" MATCH (src)-[r]->(tgt)")
        .append(" RETURN r");

    try (Session session = driver.session()) {
      final Value value =
          session.readTransaction(
              tx -> {
                Result result = tx.run(query.toString(), parameters);
                if (result == null) {
                  return null;
                }
                if (result.hasNext()) {
                  return result.single().get(0);
                } else {
                  return null;
                }
              });

      if (value == null) {
        return Optional.absent();
      }

      return Optional.of(createDepRelation(value));
    }
  }

  private DependencyRelation createDepRelation(Value value) {
    // create the node back
    final DependencyRelation depRelation =
        OBJECT_MAPPER.convertValue(value.asMap(), DependencyRelation.class);
    // get back the properties
    try {
      // TODO adapt deserializer in the future to use jackson only
      final String o = (String) value.asMap().get("exclusions_json");
      final List<String> stringStringMap;
      stringStringMap = OBJECT_MAPPER.readValue(o, List.class);
      depRelation.setExclusions(stringStringMap);
      // TODO?? reference to tgtNode and srceNode, set them...

    } catch (JsonProcessingException e) {
      logger.error("Could not parse node from db back", e);
      e.printStackTrace();
    }
    return depRelation;
  }

  private String createMatchingCondition(
      MvnArtifactNode mvnArtifactNode,
      String label,
      @Nullable String prefixParameter,
      Map<String, Object> parameters) {
    if (StringUtils.isNotBlank(prefixParameter) && StringUtils.endsWith(prefixParameter, ".")) {
      // remove the dot
      prefixParameter = prefixParameter.substring(0, prefixParameter.length() - 2);
    }

    String groupParameter = prefixParameter + "Group";
    String artifactParameter = prefixParameter + "Artifact";
    String versionParameter = prefixParameter + "Version";
    String classifierParameter = prefixParameter + "Classifier";
    String packagingParameter = prefixParameter + "Packaging";

    parameters.put(groupParameter, mvnArtifactNode.getGroup());
    parameters.put(artifactParameter, mvnArtifactNode.getArtifact());
    parameters.put(versionParameter, mvnArtifactNode.getVersion());
    parameters.put(classifierParameter, mvnArtifactNode.getClassifier());
    parameters.put(packagingParameter, mvnArtifactNode.getPackaging());

    return String.format(
        "%1$s.group = $%2$s AND %1$s.artifact = $%3$s AND %1$s.version = $%4$s AND %1$s.classifier = $%5$s AND %1$s.packaging = $%6$s",
        label,
        groupParameter,
        artifactParameter,
        versionParameter,
        classifierParameter,
        packagingParameter);
  }

  private String createMatchExpression(
      String group,
      String artifact,
      String version,
      String classifier,
      String packaging,
      String label,
      @Nullable String prefixParameter,
      HashMap<String, Object> parameters) {
    if (StringUtils.isNotBlank(prefixParameter) && !StringUtils.endsWith(".", prefixParameter)) {
      // add the dot
      prefixParameter = prefixParameter + ".";
    }

    String groupParameter = prefixParameter + "group";
    String artifactParameter = prefixParameter + "artifact";
    String versionParameter = prefixParameter + "version";
    String classifierParameter = prefixParameter + "classifier";
    String packagingParameter = prefixParameter + "packaging";

    parameters.put(groupParameter, group);
    parameters.put(artifactParameter, artifact);
    parameters.put(versionParameter, version);
    parameters.put(classifierParameter, classifier);
    parameters.put(packagingParameter, packaging);

    return String.format(
        "MATCH (%s:MvnArtifact {group:$%s, artifact:$%s, version:$%s, classifier:$%s, packaging:$%s})",
        label,
        groupParameter,
        artifactParameter,
        versionParameter,
        classifierParameter,
        packagingParameter);
  }

  private String createMatchExpression(
      MvnArtifactNode mvnArtifactNode,
      String label,
      @Nullable String prefixParameter,
      HashMap<String, Object> parameters) {
    return createMatchExpression(
        mvnArtifactNode.getGroup(),
        mvnArtifactNode.getArtifact(),
        mvnArtifactNode.getVersion(),
        mvnArtifactNode.getClassifier(),
        mvnArtifactNode.getPackaging(),
        label,
        prefixParameter,
        parameters);
  }

  public boolean containsNodeWithVersionGQ(
      String groupId,
      String artifactId,
      String version,
      String classifier,
      String packaging,
      String targetVersion) {

    if (StringUtils.isBlank(classifier)) {
      // neo4j cannot deal with null values
      classifier = "null";
    }

    HashMap<String, Object> parameters = new HashMap<>();
    // match based on gav, classifier
    String query =
        createMatchExpression(
                groupId, artifactId, version, classifier, packaging, "n", null, parameters)
            + " return n.crawlerVersion, n.resolvingLevel";
    Pair<String, String> versionNumberAndResolvingLevel;
    try (Session session = driver.session()) {
      versionNumberAndResolvingLevel =
          session.writeTransaction(
              tx -> {
                Result result = tx.run(query, parameters);
                if (result == null) {
                  return null;
                }

                if (result.hasNext()) {
                  final Record single = result.single();
                  return Pair.of(single.get(0).asString(), single.get(1).asString());
                } else {
                  return null;
                }
              });
    }
    if (versionNumberAndResolvingLevel == null) {
      // we did not find a node
      return false;
    }

    String versionNumber = versionNumberAndResolvingLevel.getLeft();
    String resolvingLevel = versionNumberAndResolvingLevel.getRight();
    if (StringUtils.isBlank(versionNumber) || StringUtils.isBlank(resolvingLevel)) {
      return false;
    }
    final boolean upToDate = StringUtils.compare(versionNumber, targetVersion) >= 0;
    final boolean fullResolved =
        StringUtils.equals(MvnArtifactNode.ResolvingLevel.FULL.toString(), resolvingLevel);
    return upToDate & fullResolved;
  }

  @Override
  public List<DependencyRelation> getDependencyManagement(MvnArtifactNode instance) {
    HashMap<String, Object> parameters = new HashMap<>();

    StringBuilder query = new StringBuilder();
    query
        .append("MATCH (src:MvnArtifact), (tgt:MvnArtifact)")
        .append(" WHERE ")
        .append(createMatchingCondition(instance, "src", "src.", parameters))
        .append(" MATCH (src)-[r:MANAGES|IMPORTS]->(tgt)")
        .append(" RETURN r, tgt");

    try (Session session = driver.session()) {
      List<DependencyRelation> list =
          session.readTransaction(
              tx -> {
                Result result = tx.run(query.toString(), parameters);
                if (result == null) {
                  return Collections.emptyList();
                }
                List<DependencyRelation> dependencyRelations = new ArrayList<>();
                while (result.hasNext()) {
                  final Record next = result.next();
                  final DependencyRelation r = createDepRelation(next.get("r"));
                  final MvnArtifactNodeProxy tgt = createProxyNode(next.get("tgt"));
                  r.setTgtNode(tgt);
                  dependencyRelations.add(r);
                }
                return dependencyRelations;
              });

      return list;
    }
  }

  private MvnArtifactNodeProxy createProxyNode(Value value) {
    // create the node back
    final MvnArtifactNodeProxy mvnArtifactNode =
        OBJECT_MAPPER.convertValue(value.asMap(), MvnArtifactNodeProxy.class);
    mvnArtifactNode.setDoaMvnArtifactNode(this);
    // get back the properties
    try {
      // TODO adapt deserializer in the future to use jackson only
      final String o = (String) value.asMap().get("properties_json");
      final Map<String, String> stringStringMap;
      stringStringMap = OBJECT_MAPPER.readValue(o, Map.class);
      mvnArtifactNode.setProperties(stringStringMap);
    } catch (JsonProcessingException e) {
      logger.error("Could not parse node from db back", e);
      e.printStackTrace();
    }
    return mvnArtifactNode;
  }

  @Override
  public List<DependencyRelation> getDependencies(MvnArtifactNode instance) {
    HashMap<String, Object> parameters = new HashMap<>();

    StringBuilder query = new StringBuilder();
    query
        .append("MATCH (src:MvnArtifact), (tgt:MvnArtifact)")
        .append(" WHERE ")
        .append(createMatchingCondition(instance, "src", "src.", parameters))
        .append(" MATCH (src)-[r:DEPENDS_ON]->(tgt)")
        .append(" RETURN r, tgt");

    try (Session session = driver.session()) {
      List<DependencyRelation> list =
          session.readTransaction(
              tx -> {
                Result result = tx.run(query.toString(), parameters);
                if (result == null) {
                  return Collections.emptyList();
                }
                List<DependencyRelation> dependencyRelations = new ArrayList<>();
                while (result.hasNext()) {
                  final Record next = result.next();
                  final DependencyRelation r = createDepRelation(next.get("r"));
                  final MvnArtifactNodeProxy tgt = createProxyNode(next.get("tgt"));
                  r.setTgtNode(tgt);
                  dependencyRelations.add(r);
                }
                return dependencyRelations;
              });

      return list;
    }
  }
}

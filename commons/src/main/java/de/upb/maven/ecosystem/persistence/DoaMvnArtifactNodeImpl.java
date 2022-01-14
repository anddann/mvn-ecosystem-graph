package de.upb.maven.ecosystem.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Query;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DoaMvnArtifactNodeImpl implements DaoMvnArtifactNode {


    private static final Logger logger = LoggerFactory.getLogger(DoaMvnArtifactNodeImpl.class);
    private final Driver driver;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    public DoaMvnArtifactNodeImpl(Driver driver) {
        this.driver = driver;
    }

    /**
     * Uses Jackson to fill property set for Neo4j.  Collections and complex objects will
     * be serialized as JSON for now as Neo4j does not support them as properties.
     *
     * @param entity
     * @return
     */
    @NotNull
    private Map<String, Object> createProperties(Object entity) {
        Map<String, Object> properties = new HashMap<>();

        JsonNode objectNode = OBJECT_MAPPER.valueToTree(entity);

        for (Iterator<Map.Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> next = it.next();

            String key = next.getKey();
            JsonNode value = next.getValue();

            if (value.isContainerNode()) {
                properties.put(key + ".json", value.toString());
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


    private MvnArtifactNode constructJavaObject() {
        MvnArtifactNode mvnArtifactNode = new MvnArtifactNode();
        // set the fields
        return mvnArtifactNode;
    }
    /**
     * sanity check of the data to write into the database
     * @param mvnArtifactNode
     * @throws IllegalArgumentException
     */
    public static void sanityCheck(MvnArtifactNode mvnArtifactNode) throws IllegalArgumentException {

        if (StringUtils.isBlank(mvnArtifactNode.getGroup())) {
            logger.error("Group is blank");
            throw new IllegalArgumentException("Group is blank");
        }
        if (StringUtils.isBlank(mvnArtifactNode.getArtifact())) {
            logger.error("Artifact is blank");
            throw new IllegalArgumentException("Artifact is blank");
        }
        if (StringUtils.isBlank(mvnArtifactNode.getVersion())) {
            logger.error("Version is blank");
            throw new IllegalArgumentException("Version is blank");
        }
        if (StringUtils.isBlank(mvnArtifactNode.getPackaging())) {
            logger.error("Packaging is blank");
            throw new IllegalArgumentException("Packaging is blank");
        }
        if (mvnArtifactNode.getParent() != null && !StringUtils.equals(mvnArtifactNode.getParent().getPackaging(), "pom")) {
            // the parent artifact may only have the packaging pom
            logger.error("The packaging of the parent is invalid");
            throw new IllegalArgumentException("A Maven parent must have packaging: pom");
        }
        // validate direct dependencies
        for (DependencyRelation dependencyRelation : mvnArtifactNode.getDependencies()) {
            //check for sanity
            if (!StringUtils.equals(dependencyRelation.getClassifier(), dependencyRelation.getDependency().getClassifier())) {
                logger.error("The dependency relation classifier do not match");
                throw new IllegalArgumentException("The dependency relation classifier do not match");
            }
            if (!StringUtils.equals(dependencyRelation.getType(), dependencyRelation.getDependency().getPackaging())) {
                logger.error("The dependency relation type/packaging do not match");
                throw new IllegalArgumentException("The dependency relation type/packaging do not match");
            }

        }

        for (DependencyRelation dependencyRelation : mvnArtifactNode.getDependencyManagement()) {
            //check for sanity
            if (!StringUtils.equals(dependencyRelation.getClassifier(), dependencyRelation.getDependency().getClassifier())) {
                logger.error("The import dependency relation classifier do not match");
                throw new IllegalArgumentException("The dependency relation classifier do not match");
            }
            if (!StringUtils.equals(dependencyRelation.getType(), dependencyRelation.getDependency().getPackaging())) {
                logger.error("The import dependency relation type/packaging do not match");
                throw new IllegalArgumentException("The dependency relation type/packaging do not match");
            }
            if (dependencyRelation.getScope() == DependencyScope.IMPORT && !StringUtils.equals(dependencyRelation.getType(), "pom")) {
                logger.error("The import dependency relation type do not match");
                throw new IllegalArgumentException("The import dependency relation type do not match");
            }
            if (dependencyRelation.getScope() == DependencyScope.IMPORT && !StringUtils.equals(dependencyRelation.getDependency().getPackaging(), "pom")) {
                logger.error("The import dependency has not packaging: pom");
                throw new IllegalArgumentException("The import dependency has not packaging: pom");
            }

        }


    }

    @Override
    public Optional<MvnArtifactNode> get(long id) {
        throw new UnsupportedOperationException("getAll() not implemented");

    }

    @Override
    public Optional<MvnArtifactNode> get(MvnArtifactNode instance) {
        //todo must I include the packaging?

        // match based on gav, classifier
        String query = String.format("MATCH (n:MvnArtifact {g:%s, a:%s, v:%s, c:%s}) return n", instance.getGroup(), instance.getArtifact(), instance.getVersion(), instance.getClassifier());


        return Optional.empty();
    }

    @Override
    public List<MvnArtifactNode> getAll() {
        throw new UnsupportedOperationException("getAll() not implemented");
    }

    @Override
    public void save(MvnArtifactNode mvnArtifactNode) {
        throw new UnsupportedOperationException("getAll() not implemented");
    }


    private <T> void write(
            Collection<T> entities,
            Session session,
            Function<T, Query> queryFactory) {
        try (Transaction tx = session.beginTransaction()) {
            for (T entity : entities) {
                Query query = queryFactory.apply(entity);
                tx.run(query);
            }
            tx.commit();
        }
    }

    private <T, U> void write(
            T srcNode,
            Collection<U> entities,
            Session session,
            BiFunction<T, U, Query> queryFactory) {
        try (Transaction tx = session.beginTransaction()) {
            for (U entity : entities) {
                Query query = queryFactory.apply(srcNode, entity);
                tx.run(query);
            }
            tx.commit();
        }
    }

    //TODO
    //for dependency relatiopns
    // While it usually represents the extension on the filename of the dependency, that is not always the case: a type can be mapped to a different extension and a classifier. The type often corresponds to the packaging used, though this is also not always the case.


    private Query createNodeQuery(MvnArtifactNode node) {
        StringBuilder query = new StringBuilder();
        query.append(String.format("MERGE (n:MvnArtifact {g:%s, a%s, v:%s, c:%s, packaging:%s})", node.getGroup(), node.getArtifact(), node.getVersion(), node.getClassifier(), node.getPackaging()));
        query.append(" ON CREATE SET n = $props");
        query.append(" ON MATCH SET n += $props");
        Map<String, Object> properties = createProperties(node);

        return new Query(query.toString(), Collections.singletonMap("props", properties));
    }


    private Query createDependencyManagementRelationQuery(MvnArtifactNode srcNode, DependencyRelation dependencyRelation) {
        //Tdo distinguish IMPORT vs normal dependency_management edges

        StringBuilder query = new StringBuilder();
        query
                .append("MATCH (src:MvnArtifact), (tgt:MvnArtifact)");
        query.append(
                " WHERE src.g = $srcG AND src.a = $srcA AND src.v = $srcV AND src.c = $srcC AND tgt.g = $tgtG AND tgt.a = $tgtA AND tgt.v = $tgtV AND tgt.c = $tgtC");

        if (dependencyRelation.getScope() == DependencyScope.IMPORT) {
            query.append(" CREATE (src)-[r:IMPORTS $props]->(tgt)");
        } else {
            query.append(" CREATE (src)-[r:MANAGES $props]->(tgt)");

        }

        Map<String, Object> properties = createProperties(dependencyRelation);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("props", properties);
        //src node
        parameters.put("srcG", srcNode.getGroup());
        parameters.put("srcA", srcNode.getArtifact());
        parameters.put("srcV", srcNode.getVersion());
        parameters.put("srcC", srcNode.getClassifier());

        //tgt node
        parameters.put("tgtG", dependencyRelation.getDependency().getGroup());
        parameters.put("tgtA", dependencyRelation.getDependency().getArtifact());
        parameters.put("tgtV", dependencyRelation.getDependency().getVersion());
        parameters.put("tgtC", dependencyRelation.getDependency().getClassifier());

        return new Query(query.toString(), parameters);
    }

    private Query createDirectDependencyRelationQuery(MvnArtifactNode srcNode, DependencyRelation dependencyRelation) {
        StringBuilder query = new StringBuilder();
        query
                .append("MATCH (src:MvnArtifact), (tgt:MvnArtifact)")
                .append(
                        " WHERE src.g = $srcG AND src.a = $srcA AND src.v = $srcV AND src.c = $srcC AND tgt.g = $tgtG AND tgt.a = $tgtA AND tgt.v = $tgtV AND tgt.c = $tgtC")
                .append(" CREATE (src)-[r:DEPENDS_ON $props]->(tgt)");

        Map<String, Object> properties = createProperties(dependencyRelation);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("props", properties);
        //src node
        parameters.put("srcG", srcNode.getGroup());
        parameters.put("srcA", srcNode.getArtifact());
        parameters.put("srcV", srcNode.getVersion());
        parameters.put("srcC", srcNode.getClassifier());

        //tgt node
        parameters.put("tgtG", dependencyRelation.getDependency().getGroup());
        parameters.put("tgtA", dependencyRelation.getDependency().getArtifact());
        parameters.put("tgtV", dependencyRelation.getDependency().getVersion());
        parameters.put("tgtC", dependencyRelation.getDependency().getClassifier());

        return new Query(query.toString(), parameters);
    }

    private Query createParentRelationQuery(MvnArtifactNode srcNode, MvnArtifactNode parent) {
        StringBuilder query = new StringBuilder();
        query
                .append("MATCH (src:MvnArtifact), (tgt:MvnArtifact)")
                .append(
                        " WHERE src.g = $srcG AND src.a = $srcA AND src.v = $srcV AND src.c = $srcC AND tgt.g = $tgtG AND tgt.a = $tgtA AND tgt.v = $tgtV AND tgt.c = $tgtC")
                .append(" CREATE (tgt)-[r:PARENT]->(src)");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("srcG", srcNode.getGroup());
        parameters.put("srcA", srcNode.getArtifact());
        parameters.put("srcV", srcNode.getVersion());
        parameters.put("srcC", srcNode.getClassifier());

        parameters.put("tgtG", parent.getGroup());
        parameters.put("tgtA", parent.getArtifact());
        parameters.put("tgtV", parent.getVersion());
        parameters.put("tgtC", parent.getClassifier());

        return new Query(query.toString(), parameters);
    }


    @Override
    public void saveOrMerge(MvnArtifactNode instance) {

        sanityCheck(instance);


        Stopwatch sw = Stopwatch.createStarted();

        try (Session session = driver.session()) {

            //create the parent node
            write(Collections.singleton(instance.getParent()), session, this::createNodeQuery);

            //create the dependency nodes
            write(instance.getDependencies().stream().map(x -> x.getDependency()).collect(Collectors.toList()), session, this::createNodeQuery);

            //create the dependency management nodes
            write(instance.getDependencyManagement().stream().map(x -> x.getDependency()).collect(Collectors.toList()), session, this::createNodeQuery);

            //create the node itself
            write(Collections.singleton(instance), session, this::createNodeQuery);

            //create the parent relationship
            write(instance, Collections.singleton(instance.getParent()), session, this::createParentRelationQuery);

            // create the dependency management relationship
            write(instance, instance.getDependencyManagement(), session, this::createDependencyManagementRelationQuery);

            //create the direct dependency relationship
            write(instance, instance.getDependencies(), session, this::createDirectDependencyRelationQuery);
        }

        logger.info("Finished writing node to Neo4j in {}", sw);
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
        //TODO
        return Optional.empty();
    }
}

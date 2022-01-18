package de.upb.maven.ecosystem.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MvnArtifactNode {


    public enum ResolvingLevel {
        DANGLING, FULL;
    }


    private ResolvingLevel resolvingLevel = ResolvingLevel.DANGLING;

    // version number that created this node, e.g., used for updating and check which crawler was used
    private String crawlerVersion = Neo4JConnector.getCrawlerVersion();

    private String group;
    private String artifact;
    private String version;

    // TODO set in code for multiple repo support
    private String repoURL = "https://repo1.maven.org/maven2/";

    // the url of the repo
    private String scmURL;

    // the default is null
    private String classifier = "null";

    // When no packaging is declared, Maven assumes the packaging is the default: jar
    private String packaging = "jar";

    // the maven properties declared in this artifact's pom which are inherited to the children
    // @JsonProperty("properties_json")
    // flatt the nested object into a string, since neo4j does not support complex types
//  @JsonSerialize(using = ToStringSerializer.class)
//  @JsonDeserialize(using = CustomNullDeserializer.class)
    private Map<String, String> properties = new HashMap<>();

    // relationship type=PARENT
    @JsonIgnore
    private Optional<MvnArtifactNode> parent;

    // must be list to be ordered, in the mvn resolution process the order of dependencies matters for
    // resolving
    // relationship type=DEPENDENCY / DEPENDS_ON
    // inherited to the children
    @JsonIgnore
    private List<DependencyRelation> dependencies = new ArrayList<>();

    // relationship type=DEPENDENCY_MANAGEMENT / MANAGES
    @JsonIgnore
    private List<DependencyRelation> dependencyManagement = new ArrayList<>();

    public void setParent(MvnArtifactNode parent) {
        // TODO add nullness check?
        // quick sanity check
        if (!StringUtils.equals(parent.getPackaging(), "pom")) {
            // the parent artifact may only have the packaging pom
            throw new IllegalArgumentException("A Maven parent must have packaging: pom");
        }
        this.parent = Optional.of(parent);
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier == null ? "null" : classifier;
    }
}

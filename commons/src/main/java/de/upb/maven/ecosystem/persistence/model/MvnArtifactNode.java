package de.upb.maven.ecosystem.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MvnArtifactNode implements Serializable {

  private ResolvingLevel resolvingLevel = ResolvingLevel.DANGLING;

  // FIXME: add the Model from maven as a serialized artifact to the properties...

  // only used as an identifier for neo4j
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
  // the maven properties declared in this artifact's pom. They are inherited to the children
  // @JsonProperty("properties_json")
  // flatt the nested object into a string, since neo4j does not support complex types
  //  @JsonSerialize(using = ToStringSerializer.class)
  //  @JsonDeserialize(using = CustomNullDeserializer.class)
  private Map<String, String> properties = new HashMap<>();
  // relationship type=PARENT // use guava to serialize it for redis :(
  @ToString.Exclude @EqualsAndHashCode.Exclude @JsonIgnore
  private Optional<MvnArtifactNode> parent = Optional.absent();
  // must be list to be ordered, in the mvn resolution process the order of dependencies matters for
  // resolving
  // relationship type=DEPENDENCY / DEPENDS_ON
  // inherited to the children,
  // exclude to avoid recursive calling in the case of circular dependencies
  @ToString.Exclude @EqualsAndHashCode.Exclude @JsonIgnore
  private List<DependencyRelation> dependencies = new ArrayList<>();
  // relationship type=DEPENDENCY_MANAGEMENT / MANAGES
  // exclude to avoid recursive calling in the case of circular dependencies
  @ToString.Exclude @EqualsAndHashCode.Exclude @JsonIgnore
  private List<DependencyRelation> dependencyManagement = new ArrayList<>();

  /**
   * A node is uniquely identified by g,a,v, classifier
   *
   * @return
   */
  @JsonProperty("hashId")
  public String getHashId() {
    return DigestUtils.sha1Hex(group + ":" + artifact + ":" + version + "-" + classifier);
  }

  public void setParent(Optional<MvnArtifactNode> parent) {
    // quick sanity check
    if (parent.isPresent() && !StringUtils.equals(parent.get().getPackaging(), "pom")) {
      // the parent artifact may only have the packaging pom
      throw new IllegalArgumentException("A Maven parent must have packaging: pom");
    }
    this.parent = parent;
  }

  public void setClassifier(String classifier) {
    this.classifier = (classifier == null || StringUtils.isBlank(classifier)) ? "null" : classifier;
  }

  public enum ResolvingLevel {
    DANGLING,
    FULL;
  }
}

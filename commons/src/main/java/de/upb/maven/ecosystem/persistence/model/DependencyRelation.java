package de.upb.maven.ecosystem.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

// the relationship
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DependencyRelation implements Serializable {

  // the default scope is compile
  private DependencyScope scope = DependencyScope.COMPILE;
  private boolean optional;

  // The profile name under which this dependency is included, by default ="". Thus, the dependency
  // is always included
  private String profile = "";

  // This defaults to jar.
  // While it usually represents the extension on the filename of the dependency, that is not always
  // the case: a type can be mapped to a different extension and a classifier. The type often
  // corresponds to the packaging used, though this is also not always the case.
  private String type = "jar";

  // defaults to null
  private String classifier = "null";

  // exclude to avoid recursive calling in the case of circular dependencies
  @ToString.Exclude @EqualsAndHashCode.Exclude @JsonIgnore private MvnArtifactNode tgtNode;

  // in the mvn resolution process the order of dependencies matters for resolving. Thus, we store
  // its position in the pom file, starting from 0
  private int position;

  // contains the exclusions in the format g:a
  // @JsonProperty("exclusions_json")
  // flatt the nested object into a string, since neo4j does not support complex types
  private List<String> exclusions = new ArrayList<>();

  public void setClassifier(String classifier) {
    this.classifier = (classifier == null || StringUtils.isBlank(classifier)) ? "null" : classifier;
  }
}

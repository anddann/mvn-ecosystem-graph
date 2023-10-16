package de.upb.maven.ecosystem.licenses;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.upb.maven.ecosystem.persistence.common.DependencyScope;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Gav;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.License;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.Inheritance;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@Inheritance
@JsonInclude(JsonInclude.Include.NON_NULL)
// include subtype info for de-serialization
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public abstract class LocalDependency {

  // do not serialize/deserialize the file path, it is only relevant locally
  // to put it in the class path for analyses...
  @JsonIgnore private Path filepath;

  private Set<License> licenses = new HashSet<>();

  private String name;
  private String classifier;

  /**
   * WARNING: We must not name this `type` since when deserializing, this will clash with the
   * JsonTypeInfo property `type` as defined for the class: @JsonTypeInfo(use =
   * JsonTypeInfo.Id.NAME, property = "type")
   */
  private String packaging;

  private Gav gav;

  private DependencyScope dependencyScope = DependencyScope.RUNTIME;

  /**
   * Root dependencies that introduced this dependencies. Might be multiple and might also be the
   * dependency itself for root dependencies.
   */
  @JsonIgnore
  private Set<LocalDependency> rootDependencies = Collections.synchronizedSet(new HashSet<>());

  /** Project that use this dependency */
  @JsonIgnore private Set<String> usingProjects = Collections.synchronizedSet(new HashSet<>());

  /**
   * Projects that directly include this dependency (allows having a single dependency
   * representation for multiple uses)
   */
  @JsonIgnore private Set<String> definingProjects = Collections.synchronizedSet(new HashSet<>());

  public Path getFilepath() {
    return filepath;
  }

  public void setFilepath(Path filepath) {
    this.filepath = filepath;
  }

  public Set<License> getLicenses() {
    return licenses;
  }

  public void setLicenses(Set<License> licenses) {
    this.licenses = licenses;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getClassifier() {
    return classifier;
  }

  public void setClassifier(String classifier) {
    this.classifier = classifier;
  }

  public String getPackaging() {
    return packaging;
  }

  public void setPackaging(String packaging) {
    this.packaging = packaging;
  }

  public DependencyScope getDependencyScope() {
    return dependencyScope;
  }

  public void setDependencyScope(DependencyScope dependencyScope) {
    this.dependencyScope = dependencyScope;
  }

  public Set<LocalDependency> getRootDependencies() {
    return rootDependencies;
  }

  public void setRootDependencies(Set<LocalDependency> rootDependencies) {
    this.rootDependencies = rootDependencies;
  }

  public Set<String> getUsingProjects() {
    return usingProjects;
  }

  public void setUsingProjects(Set<String> usingProjects) {
    this.usingProjects = usingProjects;
  }

  public Set<String> getDefiningProjects() {
    return definingProjects;
  }

  public void setDefiningProjects(Set<String> definingProjects) {
    this.definingProjects = definingProjects;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).append("name", name).toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    LocalDependency that = (LocalDependency) o;

    // i used the identifiers here as well but this just works if they are all computed before the
    // comparison happens which does nto work in parallel
    return new EqualsBuilder()
        .append(filepath, that.filepath)
        .append(getGav(), that.getGav())
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).append(filepath).append(getGav()).toHashCode();
  }

  public void addUsingProject(String projectName) {
    usingProjects.add(projectName);
  }

  public void addDefiningProject(String definingProject) {
    this.definingProjects.add(definingProject);
  }

  public Gav getGav() {
    return gav;
  }

  public void setGav(Gav gav) {
    this.gav = gav;
  }
}

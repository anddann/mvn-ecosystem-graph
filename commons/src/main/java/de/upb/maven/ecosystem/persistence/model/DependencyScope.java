package de.upb.maven.ecosystem.persistence.model;

public enum DependencyScope {
  COMPILE,
  PROVIDED,
  RUNTIME,
  TEST,
  SYSTEM,
  // IMPORT: This scope is only supported on a dependency of type pom in the <dependencyManagement>
  // section.
  IMPORT
}

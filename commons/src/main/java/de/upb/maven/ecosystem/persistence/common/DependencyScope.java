package de.upb.maven.ecosystem.persistence.common;

import lombok.ToString;

@ToString
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

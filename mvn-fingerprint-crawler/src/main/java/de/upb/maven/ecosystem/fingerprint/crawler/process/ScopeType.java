package de.upb.maven.ecosystem.fingerprint.crawler.process;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ScopeType {
  @JsonProperty("METHOD")
  METHOD("METHOD"),
  @JsonProperty("CLASS")
  CLASS("CLASS"),
  @JsonProperty("PACKAGE")
  PACKAGE("PACKAGE"),
  @JsonProperty("INIT")
  INIT("INIT"),
  @JsonProperty("CLINIT")
  CLINIT("CLINIT"),
  @JsonProperty("FIELD")
  FIELD("FIELD"),
  @JsonProperty("ANNOTATION")
  ANNOTATION("ANNOTATION"),
  @JsonProperty("IMPORT")
  IMPORT("IMPORT");

  private final String value;

  ScopeType(String value) {
    this.value = value;
  }
}

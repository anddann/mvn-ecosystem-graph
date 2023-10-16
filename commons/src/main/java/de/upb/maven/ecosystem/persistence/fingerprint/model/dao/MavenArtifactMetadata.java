package de.upb.maven.ecosystem.persistence.fingerprint.model.dao;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import de.upb.maven.ecosystem.AbstractCrawler;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "origin",
  "gav",
  "classifier",
  "embeddedGavs",
  "downloadUrl",
  "jarDigest",
  "crawlingExceptions",
  "classFile",
  "updateTime",
})
@Entity
@Table(
    name = "artifactmetadata",
    indexes = {@Index(name = "idx_artifact_sha256", columnList = "sha256")})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MavenArtifactMetadata {

  @Id @GeneratedValue private long id;

  private String crawlerVersion = AbstractCrawler.getCrawlerVersion();

  @JsonProperty("repository")
  private String repository;

  @CreationTimestamp private LocalDateTime createDateTime;
  @UpdateTimestamp private LocalDateTime updateDateTime;

  @JsonProperty("origin")
  @Column(columnDefinition = "TEXT")
  private String origin;

  @JsonProperty("gav")
  @OneToOne(cascade = CascadeType.ALL, optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "gav_id")
  private Gav gav;

  @JsonProperty("embeddedGavs")
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "artifact_embeddedgav",
      joinColumns = {@JoinColumn(name = "artifact_id")},
      inverseJoinColumns = {@JoinColumn(name = "gav_id")})
  private Set<Gav> embeddedGavs = new HashSet<>();

  @JsonProperty("downloadUrl")
  @Column(columnDefinition = "TEXT")
  private String downloadUrl;

  @JsonProperty("scmUrl")
  @Column(columnDefinition = "TEXT")
  private String scmUrl;

  @JsonProperty("sha256")
  @Column(length = 64)
  private String sha256;

  @JsonProperty("crawlingExceptions")
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "crawlingExceptions",
      joinColumns = @JoinColumn(name = "artifact_metadata_id"))
  @Column(columnDefinition = "TEXT")
  private List<String> crawlingExceptions = new ArrayList<String>();

  @JsonProperty("classFile")
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "artifact_classfile",
      joinColumns = {@JoinColumn(name = "artifact_id")},
      inverseJoinColumns = {@JoinColumn(name = "classfile_sha256")})
  private Set<ClassFile> classFile = new HashSet<ClassFile>();

  @JsonProperty("classifier")
  private String classifier;

  @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @JoinTable(
      name = "artifact_directdependencies",
      joinColumns = {@JoinColumn(name = "artifact_id")},
      inverseJoinColumns = {@JoinColumn(name = "dep_id")})
  private List<Dependency> directDependencies = new ArrayList<>();

  @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  @JoinTable(
      name = "artifact_optinaldependencies",
      joinColumns = {@JoinColumn(name = "artifact_id")},
      inverseJoinColumns = {@JoinColumn(name = "dep_id")})
  private List<Dependency> optionalDependencies = new ArrayList<>();
}

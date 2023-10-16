package de.upb.maven.ecosystem.persistence.fingerprint.model.dao;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"filename", "fullyQualifiedPath", "tlsh", "sha256", "timestamp"})
@Entity
@Table(
    name = "ClassFile",
    indexes = {
      @Index(name = "idx_class_sha256", columnList = "sha256"),
      @Index(name = "idx_class_tlsh", columnList = "tlsh")
    })
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClassFile {

  @JsonProperty("filename")
  @Column(columnDefinition = "TEXT")
  private String filename;

  @JsonProperty("fullyQualifiedPath")
  @Column(columnDefinition = "TEXT")
  private String fullyQualifiedPath;

  @JsonProperty("tlsh")
  private String tlsh;

  @CreationTimestamp private LocalDateTime createDateTime;

  @UpdateTimestamp private LocalDateTime updateDateTime;

  @JsonProperty("sha256")
  @Column(length = 64)
  @Id
  private String sha256;

  @JsonProperty("sha256jimple")
  @Column(length = 64)
  private String jimpleSha256;

  /** The timestamp of the classfile in the jar/war, etc. */
  @JsonProperty("timestamp")
  private long timestamp = 0;
}

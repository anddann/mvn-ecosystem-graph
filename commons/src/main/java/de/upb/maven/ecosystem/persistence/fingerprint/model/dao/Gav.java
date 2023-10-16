package de.upb.maven.ecosystem.persistence.fingerprint.model.dao;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"groupId", "artifactId", "versionId", "licences"})
@Entity
@Table(name = "gav")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Gav {

  @Id @GeneratedValue private long id;

  /** (Required) */
  @JsonProperty("groupId")
  private String groupId;

  /** (Required) */
  @JsonProperty("artifactId")
  private String artifactId;

  /** (Required) */
  @JsonProperty("versionId")
  private String versionId;

  @JsonProperty("licences")
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "licences",
      joinColumns = {@JoinColumn(name = "gav_id")},
      inverseJoinColumns = {@JoinColumn(name = "licences_id")})
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Set<License> licences = new HashSet<>();
}

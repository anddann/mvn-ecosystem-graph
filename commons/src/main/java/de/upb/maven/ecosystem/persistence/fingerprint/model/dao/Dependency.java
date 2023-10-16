package de.upb.maven.ecosystem.persistence.fingerprint.model.dao;

import de.upb.maven.ecosystem.persistence.common.DependencyScope;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Dependency {

  @Id @GeneratedValue private long id;

  @OneToOne(cascade = CascadeType.ALL, optional = true, fetch = FetchType.LAZY)
  private Gav gav;

  @Enumerated(EnumType.STRING)
  private DependencyScope scope;

  private String classifier;
}

package de.upb.maven.ecosystem.persistence.fingerprint.model.dao;

import de.upb.maven.ecosystem.persistence.common.DependencyScope;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
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

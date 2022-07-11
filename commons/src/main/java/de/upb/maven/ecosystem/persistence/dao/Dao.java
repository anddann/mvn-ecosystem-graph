package de.upb.maven.ecosystem.persistence.dao;

import com.google.common.base.Optional;
import de.upb.maven.ecosystem.persistence.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import org.jgrapht.graph.DefaultDirectedGraph;

import java.util.List;

public interface Dao<T> {

  com.google.common.base.Optional<MvnArtifactNode> get(long id);

  Optional<MvnArtifactNode> get(T instance);

  DefaultDirectedGraph<MvnArtifactNode, DependencyRelation> getGraph(String query);

  List<T> getAll();

  void save(T t);

  void saveOrMerge(T instance);

  void update(T t, String[] params);

  void delete(T t);
}

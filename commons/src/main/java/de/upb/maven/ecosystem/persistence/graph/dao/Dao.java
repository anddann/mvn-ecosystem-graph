package de.upb.maven.ecosystem.persistence.graph.dao;

import com.google.common.base.Optional;
import de.upb.maven.ecosystem.persistence.graph.model.DependencyRelation;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.util.List;
import org.jgrapht.graph.DefaultDirectedGraph;

/**
 * DAO pattern for database access
 *
 * @param <T>
 */
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

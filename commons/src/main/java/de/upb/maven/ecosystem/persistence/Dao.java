package de.upb.maven.ecosystem.persistence;

import java.util.List;
import java.util.Optional;

public interface Dao<T> {

    Optional<T> get(long id);

    Optional<T> get(T instance);

    List<T> getAll();

    void save(T t);

    void saveOrMerge(T instance);

    void update(T t, String[] params);

    void delete(T t);
}

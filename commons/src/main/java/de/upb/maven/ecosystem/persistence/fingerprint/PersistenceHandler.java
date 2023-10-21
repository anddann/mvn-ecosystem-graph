package de.upb.maven.ecosystem.persistence.fingerprint;

import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.ClassFile;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.IntConsumer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

public interface PersistenceHandler {
  int ARTIFACT_UP_TO_DATE = -1000;
  int ARTIFACT_NON_EXISTING = -1;

  org.slf4j.Logger LOGGER = LoggerFactory.getLogger(PersistenceHandler.class);

  IntConsumer shutdownHook();

  long containsDownloadUrl(String url) throws IllegalArgumentException;

  void persist(MavenArtifactMetadata crawledMavenArtifactMetaData, long existingID)
      throws IllegalArgumentException;

  /**
   * Some jars have multiple files with the same digest, e.g. by copying files in different
   * folders...
   *
   * @param classFiles
   * @return
   */
  static Collection<ClassFile> createUniqueSHASet(Collection<ClassFile> classFiles) {
    if (classFiles == null) {
      return Collections.emptySet();
    }

    HashMap<String, ClassFile> sha2clfile = new HashMap<>(classFiles.size());

    for (ClassFile classFile : classFiles) {

      if (StringUtils.isBlank(classFile.getSha256())) {
        throw new IllegalArgumentException(
            "Classfile without sha256" + classFile.getFullyQualifiedPath());
      }

      final ClassFile classFileFromMap = sha2clfile.get(classFile.getSha256());
      if (classFileFromMap == null) {
        sha2clfile.put(classFile.getSha256(), classFile);
      } else {
        if (classFileFromMap.getFullyQualifiedPath().startsWith("resources/")) {
          sha2clfile.replace(classFile.getSha256(), classFile);
        } else if (classFileFromMap.getFullyQualifiedPath().startsWith("src/main/test")) {
          sha2clfile.replace(classFile.getSha256(), classFile);
        } else {
          // leave it...
        }
      }
    }

    return sha2clfile.values();
  }
}

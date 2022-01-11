package de.upb.maven.ecosystem.persistence;

public interface PersistenceHandler {
  int ARTIFACT_UP_TO_DATE = -1000;
  int ARTIFACT_NON_EXISTING = -1;

    long containsDownloadUrl(String toString);
}

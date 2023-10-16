package de.upb.maven.ecosystem.persistence.fingerprint;

import com.google.common.base.Stopwatch;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.ClassFile;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Gav;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.License;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;
import javax.persistence.PersistenceException;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.LoggerFactory;

public class PostgresDBHandler implements PersistenceHandler {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(PostgresDBHandler.class);
  private final SessionFactory sessionFactory;
  private String crawlerVersion;

  private static PostgresDBHandler instance;

  /**
   * @param sessionFactory
   * @param crawlerVersion
   * @return
   * @throws IOException
   */
  public static PostgresDBHandler getInstance(
      SessionFactory sessionFactory, String crawlerVersion) {
    if (instance == null) {
      instance = new PostgresDBHandler(sessionFactory, crawlerVersion);
    }
    return instance;
  }

  private PostgresDBHandler(SessionFactory sessionFactory, String crawlerVersion) {
    this.sessionFactory = sessionFactory;
    this.crawlerVersion = crawlerVersion;
  }

  @Override
  public IntConsumer shutdownHook() {
    IntConsumer display = a -> sessionFactory.close();
    return display;
  }

  /**
   * Returns -1 if no artifact is present returns -1000 if the artifact is uptodate
   *
   * @param url
   * @return
   */
  public long containsDownloadUrl(String url) throws IllegalArgumentException {
    Session session = null;
    try {
      session = sessionFactory.openSession();
      final Query<MavenArtifactMetadata> query =
          session.createQuery(
              "SELECT mvnartifact from de.upb.maven.ecosystem.persistence.fingerprint.model.dao.artifacts.MavenArtifactMetadata mvnartifact where mvnartifact.downloadUrl= :param1");
      query.setParameter("param1", url);
      query.setMaxResults(1);
      MavenArtifactMetadata mavenArtifactMetadata = query.uniqueResult();
      if (mavenArtifactMetadata == null) {
        // there is no entry for the artifact
        LOGGER.trace("No artifact found for url {}", url);
        return PersistenceHandler.ARTIFACT_NON_EXISTING;
      } else if (StringUtils.compare(mavenArtifactMetadata.getCrawlerVersion(), crawlerVersion)
          < 0) {
        // detach it from the database to use it later
        session.detach(mavenArtifactMetadata);
        return mavenArtifactMetadata.getId();
      } else if (StringUtils.compare(mavenArtifactMetadata.getCrawlerVersion(), crawlerVersion)
          >= 0) {
        LOGGER.trace("Found artifact for url {}", url);
        return PersistenceHandler.ARTIFACT_UP_TO_DATE;
      }
      throw new IllegalArgumentException("Impossible state if ArtifactMetadata in DB");
    } finally {
      if (session != null) {
        session.close();
      }
    }
  }

  /**
   * To avoid/handle conflicts in the DB do manual upserts of the classfiles
   *
   * @param classFile
   * @param activeSession
   */
  private void doManualUpsert(ClassFile classFile, Session activeSession) {
    LOGGER.info("Do manual upsert");
    try {
      final Query query =
          activeSession.createNativeQuery(
              "INSERT INTO classfile (filename,sha256,fullyqualifiedpath,tlsh,timestamp, jimplesha256, updatedatetime) VALUES ( :paramFileName, :paramSHA, :paramFQN, :paramTLSH, :paramTime, :paramJimpleSHA256, :paramUpdateTime)  ON CONFLICT (sha256) DO UPDATE SET  tlsh = :paramTLSH, jimplesha256 = :paramJimpleSHA256, updatedatetime = :paramUpdateTime");
      query.setParameter("paramFileName", classFile.getFilename());
      query.setParameter("paramSHA", classFile.getSha256());
      query.setParameter("paramFQN", classFile.getFullyQualifiedPath());
      query.setParameter("paramTLSH", classFile.getTlsh());
      query.setParameter("paramTime", classFile.getTimestamp());
      query.setParameter("paramJimpleSHA256", classFile.getJimpleSha256());
      query.setParameter("paramUpdateTime", new Date());
      query.executeUpdate();
    } catch (IllegalStateException | PersistenceException e) {
      LOGGER.error("Failed Upsert with", e);
    }
  }

  @Override
  public void persist(MavenArtifactMetadata crawledMavenArtifactMetaData, long existingID)
      throws IllegalArgumentException {

    if (existingID == ARTIFACT_UP_TO_DATE) {
      LOGGER.info("Artifact is Up-to-Date");
      return;
    }

    MavenArtifactMetadata mavenArtifactMetadata4DB;

    Stopwatch stopwatch = Stopwatch.createStarted();
    LOGGER.info("Start Transaction to DB");

    Transaction transaction = null;

    try (Session session = sessionFactory.openSession()) {
      // ensure that the classfile constraint is keept
      Collection<ClassFile> uniqueClassFilesSha1 = new HashSet<>();
      try {
        uniqueClassFilesSha1 =
            PersistenceHandler.createUniqueSHASet(crawledMavenArtifactMetaData.getClassFile());
      } catch (IllegalArgumentException e) {
        LOGGER.error("Failed persistence with ", e);
      }

      if (uniqueClassFilesSha1.isEmpty()) {
        LOGGER.warn("No Classfiles found to write into DB");
      }
      // run the classfiles in a separate transaction to make them visible ASAP
      transaction = session.beginTransaction();
      LOGGER.info("Transaction for Classfiles {}", uniqueClassFilesSha1.size());

      int i = 0;
      for (ClassFile classFile : uniqueClassFilesSha1) {
        i++;
        // instead of "traditional" save, try an UPSERT
        doManualUpsert(classFile, session);
        if (i % 50 == 0) { // 50, same as the JDBC batch size
          // flush a batch of inserts and release memory:
          session.flush();
          session.clear();
          // does it works

        }
      }

      // populate all classfiles into the db

      transaction.commit();

      transaction = session.beginTransaction();

      if (existingID > PersistenceHandler.ARTIFACT_NON_EXISTING) {
        LOGGER.info("Update existing MavenArtifact {}", existingID);
        mavenArtifactMetadata4DB = session.find(MavenArtifactMetadata.class, existingID);
      } else {
        mavenArtifactMetadata4DB = crawledMavenArtifactMetaData;
      }

      mavenArtifactMetadata4DB.setClassFile(new HashSet<>(uniqueClassFilesSha1));

      mavenArtifactMetadata4DB.setCrawlerVersion(crawlerVersion);

      LOGGER.info("Transaction for Licences ");

      i = 0;
      ArrayList<License> licenseArrayList = new ArrayList<>();
      licenseArrayList.addAll(mavenArtifactMetadata4DB.getGav().getLicences());
      mavenArtifactMetadata4DB
          .getEmbeddedGavs()
          .forEach(
              x -> {
                final Set<License> licences = x.getLicences();
                licenseArrayList.addAll(licences);
              });
      for (License license : licenseArrayList) {
        i++;
        session.save(license);
        if (i % 50 == 0) { // 50, same as the JDBC batch size
          // flush a batch of inserts and release memory:
          session.flush();
          session.clear();
        }
      }

      LOGGER.info("Transaction for Gavs ");

      // write the embedded gavs, since we do not cascade anymore
      i = 0;
      for (Gav embedGav : mavenArtifactMetadata4DB.getEmbeddedGavs()) {
        i++;

        session.save(embedGav);

        if (i % 50 == 0) { // 50, same as the JDBC batch size
          // flush a batch of inserts and release memory:
          session.flush();
          session.clear();
        }
      }

      // do not persist, as they are cascaded automatically
      /*   i = 0;
      for (Dependency deps : mavenArtifactMetadata4DB.getDirectDependencies()) {
        i++;

        session.save(deps);

        if (i % 50 == 0) { // 50, same as the JDBC batch size
          // flush a batch of inserts and release memory:
          session.flush();
          session.clear();
        }
      }

      i = 0;
      for (Dependency deps : mavenArtifactMetadata4DB.getOptionalDependencies()) {
        i++;

        session.save(deps);

        if (i % 50 == 0) { // 50, same as the JDBC batch size
          // flush a batch of inserts and release memory:
          session.flush();
          session.clear();
        }
      }*/

      // add the new ones to the data
      if (existingID > PersistenceHandler.ARTIFACT_NON_EXISTING) {
        session.update(mavenArtifactMetadata4DB);
      } else {
        session.persist(mavenArtifactMetadata4DB);
      }

      transaction.commit();

      stopwatch.stop();
      LOGGER.info(
          "[Stats] Persisted to database {} took {}",
          mavenArtifactMetadata4DB.getGav(),
          stopwatch.elapsed());
    } catch (PersistenceException | IllegalArgumentException e) {
      if (transaction != null) {
        LOGGER.error("Thrown Exception, trigger rollback", e);
        transaction.rollback();
      }
      LOGGER.error("Database write failed", e);
      throw new IllegalStateException("DB write failed", e);
    }
  }
}

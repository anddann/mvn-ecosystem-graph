package de.upb.maven.ecosystem.fingerprint.crawler;

import static org.junit.Assert.*;

import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.fingerprint.crawler.process.ArtifactManager;
import de.upb.maven.ecosystem.fingerprint.crawler.process.ArtifactProcessor;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.fingerprint.PersistenceHandler;
import de.upb.maven.ecosystem.persistence.fingerprint.PostgresDBHandler;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.ClassFile;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Dependency;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Gav;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.junit.Ignore;
import org.junit.Test;

public class PostgresDBHandlerTest {

  @Test
  @Ignore
  public void testLocalDB() throws IOException {
    CustomArtifactInfo artifactInfo =
        new CustomArtifactInfo(); // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("com.codacy");
    artifactInfo.setArtifactId("codacy-plugins_2.11");
    artifactInfo.setArtifactVersion("12.0.7_play_2.6");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final SessionFactory sessionFactoryLocalDB = DBTestUtils.createSessionFactoryInMemDB();
    PostgresDBHandler postgresDBHandler = PostgresDBHandler.getInstance(sessionFactoryLocalDB, "0");
    ArtifactManager artifactManager = new ArtifactManager(true, postgresDBHandler, 50000);
    artifactManager.process(artifactInfo, 0);
  }

  @Test
  public void createUniqueSHASet() throws IOException {

    ArtifactProcessor artifactProcessor = new ArtifactProcessor(true, 50000);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();

    // this artifact contains multiple classes with the same digest
    // results in an error in the database, as we assume the sha is unique
    artifactInfo.setGroupId("com.codacy");
    artifactInfo.setArtifactId("codacy-plugins_2.11");
    artifactInfo.setArtifactVersion("12.0.7_play_2.6");
    artifactInfo.setFileExtension("jar");
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");

    final URL url = ArtifactUtils.constructURL(artifactInfo);

    //
    final MavenArtifactMetadata process = artifactProcessor.process(artifactInfo, 0, url);

    HashSet<String> foundSHA = new HashSet<>();
    final Set<ClassFile> classFile1 = process.getClassFile();
    // we only return unique sha class files here
    Collection<ClassFile> unique = PersistenceHandler.createUniqueSHASet(classFile1);
    boolean duplicateExistis = false;

    for (ClassFile classFile : unique) {
      duplicateExistis = duplicateExistis | foundSHA.contains(classFile.getSha256());

      foundSHA.add(classFile.getSha256());
    }
    assertFalse(duplicateExistis);
  }

  // FIXME: the crawler still produces gav duplicates this should be avoided in the future..
  @Test
  public void testUniqueGav() throws IOException {
    final SessionFactory sessionFactoryLocalDB = DBTestUtils.createSessionFactoryInMemDB();
    PostgresDBHandler postgresDBHandler = PostgresDBHandler.getInstance(sessionFactoryLocalDB, "0");

    MavenArtifactMetadata mavenArtifactMetadata = new MavenArtifactMetadata();

    Gav newGav = new Gav();
    newGav.setArtifactId("art");
    newGav.setGroupId("gro");
    newGav.setVersionId("1.0");
    mavenArtifactMetadata.setGav(newGav);

    Gav depGav = new Gav();
    depGav.setVersionId("1.0");
    depGav.setArtifactId("dA");
    depGav.setGroupId("dG");
    Dependency dependency = new Dependency();
    dependency.setGav(depGav);

    mavenArtifactMetadata.setDirectDependencies(Collections.singletonList(dependency));

    postgresDBHandler.persist(mavenArtifactMetadata, PersistenceHandler.ARTIFACT_NON_EXISTING);
    try (Session session = sessionFactoryLocalDB.openSession()) {

      Query<MavenArtifactMetadata> query =
          session.createQuery(
              "SELECT mvnArt from de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata mvnArt");
      List<MavenArtifactMetadata> resultList = query.getResultList();
      assertNotNull(resultList);
      assertEquals(1, resultList.size());
      assertEquals(1, resultList.get(0).getDirectDependencies().size());

      Query<Gav> gavquery =
          session.createQuery(
              "SELECT gav from de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Gav gav");
      List<Gav> resultList1 = gavquery.getResultList();
      assertNotNull(resultList1);
      assertEquals(2, resultList1.size());
    }

    MavenArtifactMetadata mavenArtifactMetadata2 = new MavenArtifactMetadata();

    Gav newGav2 = new Gav();
    newGav2.setArtifactId("art");
    newGav2.setGroupId("gro");
    newGav2.setVersionId("1.0");
    mavenArtifactMetadata2.setGav(newGav2);

    Dependency dep2 = new Dependency();

    Gav depGav2 = new Gav();
    depGav2.setVersionId("1.0");
    depGav2.setArtifactId("dA");
    depGav2.setGroupId("dG");
    dep2.setGav(depGav2);
    mavenArtifactMetadata2.setDirectDependencies(Collections.singletonList(dep2));

    postgresDBHandler.persist(mavenArtifactMetadata2, PersistenceHandler.ARTIFACT_NON_EXISTING);

    try (Session session = sessionFactoryLocalDB.openSession()) {

      Query<MavenArtifactMetadata> query =
          session.createQuery(
              "SELECT mvnArt from de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata mvnArt");
      List<MavenArtifactMetadata> resultList = query.getResultList();
      assertNotNull(resultList);
      assertEquals(2, resultList.size());
      assertEquals(1, resultList.get(0).getDirectDependencies().size());

      Query<Gav> gavquery =
          session.createQuery(
              "SELECT gav from de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Gav gav");
      List<Gav> resultList1 = gavquery.getResultList();
      assertNotNull(resultList1);
      assertEquals(4, resultList1.size());
    }
  }
}

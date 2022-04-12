package de.upb.maven.ecosystem.crawler.process;

import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.model.MvnArtifactNode;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.driver.Driver;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class ArtifactProcessorFurtherTest extends ArtifactProcessorTestAbstract {

  /**
   * Version of <artifactId>junit-jupiter</artifactId> missing
   *
   * <p>* 08:43:56.332 [pool-1-thread-1] ERROR d.u.m.e.r.Redis2Neo4JDB - Failed to persist *
   * MvnArtifactNode(resolvingLevel=FULL, crawlerVersion=0.5.2, group=com.9ls, artifact=common-util,
   * * version=1.0.6, repoURL=https://repo1.maven.org/maven2/, scmURL=null, classifier=null, *
   * packaging=jar, properties={}),
   *
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   */
  @Test
  @Ignore
  public void test1() throws IOException, ParserConfigurationException, SAXException {
      Driver driver = createDriver();

      DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

      ArtifactProcessor artifactProcessor =
              new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

      CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
      artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
      artifactInfo.setGroupId("com.9ls");
      artifactInfo.setArtifactId("common-util");
      artifactInfo.setArtifactVersion("1.0.6");
      artifactInfo.setFileExtension("jar");
      artifactInfo.setPackaging("jar");

      final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

      assertNotNull(process);
      assertFalse(process.isEmpty());
      assertEquals(1, process.size());
      testSerialize(process);

      for (MvnArtifactNode node : process) {
          testDependencies(node);
      }
      for (MvnArtifactNode node : process) {
          DoaMvnArtifactNodeImpl.sanityCheck(node);
      }
  }

  /**
   * * 08:43:56.417 [pool-1-thread-1] ERROR d.u.m.e.r.Redis2Neo4JDB - Failed to persist *
   * MvnArtifactNode(resolvingLevel=FULL, crawlerVersion=0.5.2, group=com.4paradigm.openmldb, *
   * artifact=openmldb-spark-connector, version=0.4.3, repoURL=https://repo1.maven.org/maven2/, *
   * scmURL=null, classifier=null, packaging=jar, properties={scala.binary.version=2.12, *
   * spark.dependencyScope=provided, maven.compiler.target=1.8, spark.version=3.0.0, *
   * java.version=1.8, maven.compiler.source=1.8, project.build.sourceEncoding=UTF-8, *
   * default.project.version=0.3.0, scala.version=2.12.8, encoding=UTF-8}),
   *
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   */
  @Test
  public void test2() throws IOException, ParserConfigurationException, SAXException {
        Driver driver = createDriver();

        DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

        ArtifactProcessor artifactProcessor =
                new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

        CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
        artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
        artifactInfo.setGroupId("com.4paradigm.openmldb");
        artifactInfo.setArtifactId("openmldb-spark-connector");
        artifactInfo.setArtifactVersion("0.4.3");
        artifactInfo.setFileExtension("jar");
        artifactInfo.setPackaging("jar");

        final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

        assertNotNull(process);
        assertFalse(process.isEmpty());
        assertEquals(2, process.size());
        testSerialize(process);

        for (MvnArtifactNode node : process) {
            testDependencies(node);
        }
      for (MvnArtifactNode node : process) {
          DoaMvnArtifactNodeImpl.sanityCheck(node);
      }
    }

  /**
   * 08:43:52.367 [pool-1-thread-1] ERROR d.u.m.e.r.Redis2Neo4JDB - Failed to persist *
   * MvnArtifactNode(resolvingLevel=FULL, crawlerVersion=0.5.2, *
   * group=com.alibaba.lindorm.thirdparty, artifact=avatica-server, version=1.13.4, *
   * repoURL=https://repo1.maven.org/maven2/, scmURL=null, classifier=null, packaging=jar, *
   * properties={top.dir=${project.basedir}/..}), 08:43:52.366 [pool-1-thread-1] ERROR *
   * d.u.m.e.r.Redis2Neo4JDB - Failed to persist MvnArtifactNode(resolvingLevel=FULL, *
   * crawlerVersion=0.5.2, group=com.alibaba.lindorm.thirdparty, artifact=avatica-server, *
   * version=1.1.10, repoURL=https://repo1.maven.org/maven2/, scmURL=null, classifier=null, *
   * packaging=jar, properties={top.dir=${project.basedir}/..})
   *
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   */
  @Test
  public void test3() throws IOException, ParserConfigurationException, SAXException {
        Driver driver = createDriver();

        DoaMvnArtifactNodeImpl doaMvnArtifactNodeImpl = new DoaMvnArtifactNodeImpl(driver);

        ArtifactProcessor artifactProcessor =
                new ArtifactProcessor(doaMvnArtifactNodeImpl, "https://repo1.maven.org/maven2/");

        CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
        artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
        artifactInfo.setGroupId("com.alibaba.lindorm.thirdparty");
        artifactInfo.setArtifactId("avatica-server");
        artifactInfo.setArtifactVersion("1.13.4");
        artifactInfo.setFileExtension("jar");
        artifactInfo.setPackaging("jar");

        final Collection<MvnArtifactNode> process = artifactProcessor.process(artifactInfo);

        assertNotNull(process);
        assertFalse(process.isEmpty());
        assertEquals(3, process.size());
        testSerialize(process);

        for (MvnArtifactNode node : process) {
            testDependencies(node);
        }
      for (MvnArtifactNode node : process) {
          DoaMvnArtifactNodeImpl.sanityCheck(node);
      }
    }
}

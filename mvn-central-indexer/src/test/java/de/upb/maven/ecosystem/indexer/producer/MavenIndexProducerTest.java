package de.upb.maven.ecosystem.indexer.producer;

import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNode;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNodeImpl;
import java.io.IOException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class MavenIndexProducerTest {

  // @Test
  // collective cannot be null, this makes testing very hard
  public void perform()
      throws PlexusContainerException, ComponentLookupException, IOException, InterruptedException {
    Driver driver =
        GraphDatabase.driver(
            "bolt://heap-snapshots.cs.upb.de:7687", AuthTokens.basic("neo4j", "PdBwGaQecqX69M28"));
    DaoMvnArtifactNode daoMvnArtifactNode = new DaoMvnArtifactNodeImpl(driver);
    MavenIndexProducer mavenIndexProducer =
        new MavenIndexProducer(null, new MvnGraphArtifactDecider(daoMvnArtifactNode));
    mavenIndexProducer.perform(null);
  }
}

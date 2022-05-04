package de.upb.maven.ecosystem.indexer.producer;

import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import java.io.IOException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

public class MavenIndexProducerTest {

  @Test
  @Ignore // collective cannot be null, this makes testing very hard
  public void perform()
      throws PlexusContainerException, ComponentLookupException, IOException, InterruptedException {
    Driver driver =
        GraphDatabase.driver(
            "bolt://heap-snapshots.cs.upb.de:7687", AuthTokens.basic("neo4j", "PdBwGaQecqX69M28"));
    DoaMvnArtifactNodeImpl daoMvnArtifactNode = new DoaMvnArtifactNodeImpl(driver);
    MavenIndexProducer mavenIndexProducer = new MavenIndexProducer(null, daoMvnArtifactNode);
    mavenIndexProducer.perform(null);
  }
}

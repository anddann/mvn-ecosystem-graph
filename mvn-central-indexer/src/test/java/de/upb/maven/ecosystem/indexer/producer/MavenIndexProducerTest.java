package de.upb.maven.ecosystem.indexer.producer;

import java.io.IOException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.Ignore;
import org.junit.Test;

public class MavenIndexProducerTest {

  @Test
  @Ignore // collective cannot be null, this makes testing very hard
  public void perform()
      throws PlexusContainerException, ComponentLookupException, IOException, InterruptedException {

    MavenIndexProducer mavenIndexProducer = new MavenIndexProducer(null, null);
    mavenIndexProducer.perform(null);
  }
}

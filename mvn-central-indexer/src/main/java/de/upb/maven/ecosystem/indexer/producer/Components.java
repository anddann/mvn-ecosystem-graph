package de.upb.maven.ecosystem.indexer.producer;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.NexusIndexer;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.creator.JarFileContentsIndexCreator;
import org.apache.maven.index.creator.MavenPluginArtifactInfoIndexCreator;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.apache.maven.index.packer.IndexPacker;
import org.apache.maven.index.updater.DefaultIndexUpdater;
import org.apache.maven.index.updater.IndexUpdater;

@Named
@Singleton
public class Components {


  @Inject
  Indexer indexer;
  @Inject
  DefaultIndexUpdater indexUpdater;

  @Inject
  MinimalArtifactInfoIndexCreator minimalArtifactInfoIndexCreator;

  @Inject
  JarFileContentsIndexCreator jarFileContentsIndexCreator;

  @Inject
  MavenPluginArtifactInfoIndexCreator mavenPluginArtifactInfoIndexCreator;
}
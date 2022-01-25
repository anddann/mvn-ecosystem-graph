package de.upb.maven.ecosystem.indexer.producer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.rabbitmq.client.AMQP;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.RabbitMQCollective;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.dao.DoaMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.dao.Neo4JConnector;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.Bits;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

/** Collection of some use cases. */
public class MavenIndexProducer {

  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MavenIndexProducer.class);
  private static final String TARGET_LOCAL_REPOSITORY = "target/repository";

  // ==
  private static final ObjectMapper mapper = new ObjectMapper();
  private static String MAVEN_REPO_URL;

  static {
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
  }

  private final RabbitMQCollective collective;
  private final PlexusContainer plexusContainer;
  private final Indexer indexer;
  private final IndexUpdater indexUpdater;
  private final Wagon httpWagon;
  private final DoaMvnArtifactNodeImpl doaMvnArtifactNode;

  public MavenIndexProducer(
      RabbitMQCollective collective, DoaMvnArtifactNodeImpl doaMvnArtifactNode)
      throws PlexusContainerException, ComponentLookupException {
    this.doaMvnArtifactNode = doaMvnArtifactNode;
    initMavenRepoUrl();
    this.collective = collective;

    // here we create Plexus container, the Maven default IoC container
    // Plexus falls outside of MI scope, just accept the fact that
    // MI is a Plexus component ;)
    // If needed more info, ask on Maven Users list or Plexus Users list
    // google is your friend!
    final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
    config.setClassPathScanning(PlexusConstants.SCANNING_INDEX);
    this.plexusContainer = new DefaultPlexusContainer(config);

    // lookup the indexer components from plexus
    this.indexer = plexusContainer.lookup(Indexer.class);
    this.indexUpdater = plexusContainer.lookup(IndexUpdater.class);
    // lookup wagon used to remotely fetch index
    this.httpWagon = plexusContainer.lookup(Wagon.class, "https");
  }

  public void initMavenRepoUrl() {
    String res = System.getenv("MAVEN_REPO_URL");
    if (res == null || res.isEmpty()) {
      MAVEN_REPO_URL = "https://repo1.maven.org/maven2/";
    } else {
      MAVEN_REPO_URL = res;
    }
    LOGGER.info("MAVEN_REPO_URL Index: {}", MAVEN_REPO_URL);
  }

  public void perform(AMQP.BasicProperties props)
      throws IOException, ComponentLookupException, InterruptedException {
    // Files where local cache is (if any) and Lucene Index should be located
    File centralLocalCache = new File("/tmp/target/central-cache");
    File centralIndexDir = new File("/tmp/target/central-index");

    // Creators we want to use (search for fields it defines)
    List<IndexCreator> indexers = new ArrayList<>();
    indexers.add(plexusContainer.lookup(IndexCreator.class, "min"));
    indexers.add(plexusContainer.lookup(IndexCreator.class, "jarContent"));
    indexers.add(plexusContainer.lookup(IndexCreator.class, "maven-plugin"));

    // Create context for central repository index
    IndexingContext centralContext =
        indexer.createIndexingContext(
            "central-context",
            "central",
            centralLocalCache,
            centralIndexDir,
            MAVEN_REPO_URL,
            null,
            true,
            true,
            indexers);

    LOGGER.info("START with index");
    // Update the index (incremental update will happen if this is not 1st run and files are not
    // deleted)
    // This whole block below should not be executed on every app start, but rather controlled by
    // some configuration
    // since this block will always emit at least one HTTP GET. Central indexes are updated once a
    // week, but
    // other index sources might have different index publishing frequency.
    // Preferred frequency is once a week.
    if (true) {
      LOGGER.info("Updating Index...");
      LOGGER.info("This might take a while on first run, so please be patient!");
      // Create ResourceFetcher implementation to be used with IndexUpdateRequest
      // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher
      // implementation
      TransferListener listener =
          new AbstractTransferListener() {
            public void transferStarted(TransferEvent transferEvent) {
              LOGGER.info("Downloading " + transferEvent.getResource().getName());
            }

            public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length) {}

            public void transferCompleted(TransferEvent transferEvent) {
              LOGGER.info("Done Downloading");
            }
          };
      ResourceFetcher resourceFetcher =
          new WagonHelper.WagonFetcher(httpWagon, listener, null, null);

      Date centralContextCurrentTimestamp = centralContext.getTimestamp();
      IndexUpdateRequest updateRequest = new IndexUpdateRequest(centralContext, resourceFetcher);
      IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);
      if (updateResult.isFullUpdate()) {
        LOGGER.info("Full update happened!");
      } else if (updateResult.getTimestamp().equals(centralContextCurrentTimestamp)) {
        LOGGER.info("No update needed, index is up to date!");
      } else {
        LOGGER.info(
            "Incremental update happened, change covered "
                + centralContextCurrentTimestamp
                + " - "
                + updateResult.getTimestamp()
                + " period.");
      }
    }
    LOGGER.info("END");
    LOGGER.info("Using index");
    LOGGER.info("===========");

    // ====
    // Case:
    // dump all the GAVs
    // NOTE: will not actually execute do this below, is too long to do (Central is HUGE), but is
    // here as code
    // example
    int crawledArtifacts = 0;
    final IndexSearcher searcher = centralContext.acquireIndexSearcher();
    // Creating the ObjectMapper object

    try {
      final IndexReader ir = searcher.getIndexReader();
      Bits liveDocs = MultiFields.getLiveDocs(ir);
      int numOfDocs = ir.maxDoc() - 1;
      for (int i = 0; i < ir.maxDoc(); i++) {
        int docIndex = i;

        if (liveDocs == null || liveDocs.get(docIndex)) {
          final Document doc = ir.document(docIndex);
          final ArtifactInfo ai = IndexUtils.constructArtifactInfo(doc, centralContext);
          String fileExtToUse = getFileExtToUse(ai);
          if (ai != null && fileExtToUse != null) {
            crawledArtifacts++;

            // convert
            CustomArtifactInfo customArtifactInfo = new CustomArtifactInfo();
            customArtifactInfo.setArtifactId(ai.getArtifactId());
            customArtifactInfo.setGroupId(ai.getGroupId());
            customArtifactInfo.setArtifactVersion(ai.getVersion());
            customArtifactInfo.setClassifier(ai.getClassifier());
            customArtifactInfo.setFileExtension(fileExtToUse);
            customArtifactInfo.setBundleLicense(ai.getBundleLicense());
            customArtifactInfo.setLicenseUrl(ai.getBundleDocUrl());
            customArtifactInfo.setDistribution(ai.getRemoteUrl());
            customArtifactInfo.setRepoURL(MAVEN_REPO_URL);
            customArtifactInfo.setPackaging(ai.getPackaging());

            if (ArtifactUtils.ignoreArtifact(customArtifactInfo)) {
              LOGGER.info(
                  "Skipping {}:{}:{}-{}",
                  customArtifactInfo.getGroupId(),
                  customArtifactInfo.getArtifactId(),
                  customArtifactInfo.getArtifactVersion(),
                  customArtifactInfo.getClassifier());
            }
            LOGGER.info("Checking Artifact#{}", crawledArtifacts);

            // Converting the Object to JSONString
            String jsonString = mapper.writeValueAsString(customArtifactInfo);

            // check if artifact up-to-date
            final URL url = ArtifactUtils.constructURL(customArtifactInfo);
            Stopwatch stopwatch = Stopwatch.createStarted();

            final boolean l =
                doaMvnArtifactNode.containsNodeWithVersionGQ(
                    ai.getGroupId(),
                    ai.getArtifactId(),
                    ai.getVersion(),
                    ai.getClassifier(),
                    ai.getPackaging(),
                    Neo4JConnector.getCrawlerVersion());
            if (l) {
              LOGGER.info("Artifact up-to-date: " + url);
              continue;
            }
            LOGGER.info(
                "Checking DB for  artifact took: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            LOGGER.info("Queueing Artifact#{}", crawledArtifacts);

            collective.enqueue(props, jsonString.getBytes());
          }
        }
      }
    } finally {
      LOGGER.info("Maven Crawler Crashed");
      centralContext.releaseIndexSearcher(searcher);
      LOGGER.info("Released Index");
    }
  }

  // FIXME: sieht so aus, als ob je nach index, die file extension nicht immer stimmt
  // z.b. im clojar index haben die  extension pom|jar|...., deswegen nur 80 artifacts
  @Nullable
  private String getFileExtToUse(ArtifactInfo artifactInfo) {
    if (artifactInfo == null) {
      return null;
    }
    if ("sources".equals(artifactInfo.getClassifier())
        || "javadoc".equals(artifactInfo.getClassifier())) {
      return null;
    }

    if (artifactInfo.getFileExtension() != null) {
      if (artifactInfo.getFileExtension().equalsIgnoreCase("jar")) {
        return "jar";
      }
      if (artifactInfo.getFileExtension().equalsIgnoreCase("war")) {
        return "war";
      }
    }
    if (artifactInfo.getPackaging() != null) {
      if (artifactInfo.getPackaging().equalsIgnoreCase("jar")) {
        return "jar";
      }
      if (artifactInfo.getPackaging().equalsIgnoreCase("war")) {
        return "war";
      }
    }
    return null;
  }
}

package de.upb.maven.ecosystem.indexer.producer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.rabbitmq.client.AMQP;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.RabbitMQCollective;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Bits;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.FlatSearchRequest;
import org.apache.maven.index.FlatSearchResponse;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
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
  // private final Wagon httpWagon;
  private final ArtifactCrawlDecider artifactCrawlDecider;
  private IndexingContext centralContext;

  public MavenIndexProducer(
      RabbitMQCollective collective, ArtifactCrawlDecider artifactCrawlDecider)
      throws PlexusContainerException, ComponentLookupException {
    this.artifactCrawlDecider = artifactCrawlDecider;
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
    //    // lookup wagon used to remotely fetch index
    //    this.httpWagon = plexusContainer.lookup(Wagon.class, "https");
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
    centralContext =
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
      Instant updateStart = Instant.now();
      System.out.println("Updating Index...");
      System.out.println("This might take a while on first run, so please be patient!");

      Date centralContextCurrentTimestamp = centralContext.getTimestamp();
      IndexUpdateRequest updateRequest =
          new IndexUpdateRequest(centralContext, new Java11HttpClient());
      IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);
      if (updateResult.isFullUpdate()) {
        System.out.println("Full update happened!");
      } else if (updateResult.getTimestamp().equals(centralContextCurrentTimestamp)) {
        System.out.println("No update needed, index is up to date!");
      } else {
        System.out.println(
            "Incremental update happened, change covered "
                + centralContextCurrentTimestamp
                + " - "
                + updateResult.getTimestamp()
                + " period.");
      }

      System.out.println(
          "Finished in " + Duration.between(updateStart, Instant.now()).getSeconds() + " sec");
      System.out.println();
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
      Bits liveDocs = MultiBits.getLiveDocs(ir);
      for (int i = 0; i < ir.maxDoc(); i++) {
        int docIndex = i;

        if (liveDocs == null || liveDocs.get(docIndex)) {
          final Document doc = ir.document(docIndex);
          final ArtifactInfo ai = IndexUtils.constructArtifactInfo(doc, centralContext);
          // FIXME use the url to determine the file extension
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

            if (ArtifactUtils.ignoredArtifactType(customArtifactInfo)) {
              LOGGER.info(
                  "Skipping {}:{}:{}-{}",
                  customArtifactInfo.getGroupId(),
                  customArtifactInfo.getArtifactId(),
                  customArtifactInfo.getArtifactVersion(),
                  customArtifactInfo.getClassifier());
              continue;
            }
            LOGGER.info("Checking Artifact#{}", crawledArtifacts);

            // Converting the Object to JSONString
            String jsonString = mapper.writeValueAsString(customArtifactInfo);

            // check if artifact up-to-date
            final URL url = ArtifactUtils.constructURL(customArtifactInfo);
            Stopwatch stopwatch = Stopwatch.createStarted();

            final boolean l = artifactCrawlDecider.shouldProcessArtifact(ai, url);
            if (l) {
              LOGGER.info("Artifact up-to-date: " + url);
              continue;
            }
            LOGGER.info(
                "Checking DB for artifact took: {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));

            LOGGER.info("Queueing Artifact#{}", crawledArtifacts);

            collective.enqueue(props, jsonString.getBytes());
          }
        }
      }
    } finally {
      LOGGER.info("Maven Crawler Finished");
      centralContext.releaseIndexSearcher(searcher);
      LOGGER.info("Released Index");
    }
  }

  public Collection<ArtifactInfo> search(String groupId, String artifactId) throws IOException {
    Query gidQ = indexer.constructQuery(MAVEN.GROUP_ID, new SourcedSearchExpression(groupId));
    Query aidQ = indexer.constructQuery(MAVEN.ARTIFACT_ID, new SourcedSearchExpression(artifactId));

    BooleanQuery bq =
        new BooleanQuery.Builder()
            .add(gidQ, BooleanClause.Occur.MUST)
            .add(aidQ, BooleanClause.Occur.MUST)
            .build();
    FlatSearchResponse response = indexer.searchFlat(new FlatSearchRequest(bq, centralContext));

    return response.getResults();
  }

  // sieht so aus, als ob je nach index, die file extension nicht immer stimmt
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
      if (artifactInfo.getFileExtension().equalsIgnoreCase("module")) {
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
      if (artifactInfo.getPackaging().equalsIgnoreCase("bundle")) {
        return "jar";
      }
      if (artifactInfo.getPackaging().equalsIgnoreCase("war")) {
        return "war";
      }
    }
    return null;
  }

  private static class Java11HttpClient implements ResourceFetcher {
    private final HttpClient client =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private URI uri;

    @Override
    public void connect(String id, String url) throws IOException {
      this.uri = URI.create(url + "/");
    }

    @Override
    public void disconnect() throws IOException {}

    @Override
    public InputStream retrieve(String name) throws IOException, FileNotFoundException {
      HttpRequest request = HttpRequest.newBuilder().uri(uri.resolve(name)).GET().build();
      try {
        HttpResponse<InputStream> response =
            client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
          return response.body();
        } else {
          throw new IOException("Unexpected response: " + response);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException(e);
      }
    }
  }
}

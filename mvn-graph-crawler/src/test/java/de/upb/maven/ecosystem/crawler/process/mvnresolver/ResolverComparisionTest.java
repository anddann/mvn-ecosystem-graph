package de.upb.maven.ecosystem.crawler.process.mvnresolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.upb.maven.ecosystem.ArtifactDownloader;
import de.upb.maven.ecosystem.crawler.process.AbstractArtifactResolverTest;
import de.upb.maven.ecosystem.crawler.process.mvnresolver.aether.AetherArtifactResolver;
import de.upb.maven.ecosystem.crawler.process.mvnresolver.worklist.WorklistArtifactResolver;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.graph.dao.DaoMvnArtifactNodeImpl;
import de.upb.maven.ecosystem.persistence.graph.model.MvnArtifactNode;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.neo4j.driver.Driver;
import org.xml.sax.SAXException;

public class ResolverComparisionTest extends AbstractArtifactResolverTest {


  @ParameterizedTest
  @CsvFileSource(resources = "/data/artifacts4testing.csv", numLinesToSkip = 1)
  @Disabled
  void compareResolvers(
      String groupId, String artifactId, String version)
      throws IOException, ParserConfigurationException, SAXException {

    Driver driver = createDriver();

    DaoMvnArtifactNodeImpl daoMvnArtifactNodeImpl = new DaoMvnArtifactNodeImpl(driver);

    // logoutput

    ArtifactResolver artifactResolver =
        new WorklistArtifactResolver(
            "https://repo1.maven.org/maven2/", daoMvnArtifactNodeImpl);

    CustomArtifactInfo artifactInfo = new CustomArtifactInfo();
    artifactInfo.setRepoURL("https://repo1.maven.org/maven2/");
    artifactInfo.setGroupId(groupId);
    artifactInfo.setArtifactId(artifactId);
    artifactInfo.setArtifactVersion(version);
    artifactInfo.setFileExtension("jar");
    artifactInfo.setPackaging("jar");

    final Collection<MvnArtifactNode> outputWorklistResolver = artifactResolver.process(
        artifactInfo);
    assertNotNull(outputWorklistResolver);
    assertFalse(outputWorklistResolver.isEmpty());
    testSerialize(outputWorklistResolver);

    for (MvnArtifactNode node : outputWorklistResolver) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : outputWorklistResolver) {
      DaoMvnArtifactNodeImpl.sanityCheck(node);
    }

    // invoke the aether resolver
    AetherArtifactResolver aetherArtifactResolver = new AetherArtifactResolver();
    Collection<MvnArtifactNode> outputAether = aetherArtifactResolver.process(artifactInfo);

    for (MvnArtifactNode node : outputAether) {
      testDependencies(node);
    }
    for (MvnArtifactNode node : outputAether) {
      DaoMvnArtifactNodeImpl.sanityCheck(node);
    }

    for (MvnArtifactNode workListNode : outputWorklistResolver) {
      // find the corresponding node in
      Optional<MvnArtifactNode> first = outputAether.stream()
          .filter(x -> StringUtils.equalsIgnoreCase(getGav(workListNode), getGav(x))).findFirst();
      if (first.isPresent()) {
        compareNodes(first.get(), workListNode);
      }
    }

    // compare both results
  }


  public void compareNodes(MvnArtifactNode node1, MvnArtifactNode node2) {
    final Set<String> node1_depGAVs =
        node1.getDependencies().stream()
            .map(
                x ->
                    getGav(x.getTgtNode()))
            .collect(Collectors.toSet());

    final Set<String> node1_depMgmTGAVs =
        node1.getDependencyManagement().stream()
            .map(
                x -> getGav(x.getTgtNode()))
            .collect(Collectors.toSet());

    final Set<String> node2_depGAVs =
        node2.getDependencies().stream()
            .map(
                x ->
                    getGav(x.getTgtNode()))
            .collect(Collectors.toSet());

    final Set<String> node2_depMgmTGAVs =
        node2.getDependencyManagement().stream()
            .map(
                x -> getGav(x.getTgtNode()))
            .collect(Collectors.toSet());

    assertEquals(node1_depGAVs.size(), node2_depGAVs.size());
    assertEquals(node1_depMgmTGAVs.size(), node2_depMgmTGAVs.size());

    assertEquals(node1_depGAVs, node2_depGAVs);
    assertEquals(node1_depMgmTGAVs, node2_depMgmTGAVs);

  }

  public static String getGav(MvnArtifactNode node) {
    return node.getGroup() + ":" + node.getArtifact() + ":" + node.getVersion();
  }


}

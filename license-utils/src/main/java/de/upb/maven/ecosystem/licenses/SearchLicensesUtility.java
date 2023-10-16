package de.upb.maven.ecosystem.licenses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.upb.maven.ecosystem.PomFileUtil;
import de.upb.maven.ecosystem.licenses.spdx.SPDXLicensesJSON;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Gav;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.rdfparser.license.ListedLicenses;

/** Requires internet connection to function */
public class SearchLicensesUtility {

  public static final String NOASSERTION = "NOASSERTION";
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchLicensesUtility.class);

  public static javax.ws.rs.client.WebTarget SPDX_ENDPOINT =
      ClientBuilder.newBuilder().build().target("https://spdx.org");

  private static SearchLicensesUtility instance;
  private final HashSet<String> UPPER_CASE_SpdxListedLicenseIds = new HashSet<>();
  private final HashSet<String> LICENCES_NAMES = new HashSet<>();
  private final HashMap<String, String> LICENCE_NAME_TO_ID = new HashMap<>();
  private ListedLicenses listedLicenses;

  private SearchLicensesUtility() {
    initSpdxLicenseInformation();
  }

  public static String getNOASSERTION() {
    return NOASSERTION;
  }

  public static synchronized SearchLicensesUtility getInstance() {
    if (instance == null) {
      instance = new SearchLicensesUtility();
    }
    return instance;
  }

  public ListedLicenses getListedLicenses() {
    return listedLicenses;
  }

  public HashSet<String> getUPPER_CASE_SpdxListedLicenseIds() {
    return UPPER_CASE_SpdxListedLicenseIds;
  }

  public HashSet<String> getLICENCES_NAMES() {
    return LICENCES_NAMES;
  }

  public HashMap<String, String> getLICENCE_NAME_TO_ID() {
    return LICENCE_NAME_TO_ID;
  }

  private void initSpdxLicenseInformation() {
    // TODO: migrate to ListedLicenses.getListedLicenses().getSpdxListedLicenseIds()
    listedLicenses = ListedLicenses.getListedLicenses();

    WebTarget path = SPDX_ENDPOINT.path("licenses/licenses.json");

    SPDXLicensesJSON jsonMap = null;
    try {
      jsonMap = path.request(MediaType.APPLICATION_JSON_TYPE).get(SPDXLicensesJSON.class);

      if (jsonMap == null) {
        LOGGER.error("Could not read SPDX Document Information");
      } else {
        // fill the code
        for (SPDXLicensesJSON.SpdxLicenseJson licenseJSON : jsonMap.getLicenses()) {
          UPPER_CASE_SpdxListedLicenseIds.add(licenseJSON.getLicenseId().toUpperCase());
          LICENCES_NAMES.add(licenseJSON.getName());
          LICENCE_NAME_TO_ID.put(licenseJSON.getName(), licenseJSON.getLicenseId());
        }
      }
    } catch (Throwable e) {
      LOGGER.error("Failed to load SPDX License Information", e);
    }

    // also load the mappings from cyclonedx
    // TODO get access to LicenseResolver
    try (InputStream is =
        SearchLicensesUtility.class.getResourceAsStream("/license-mapping.json")) {
      final String jsonTxt = IOUtils.toString(is, StandardCharsets.UTF_8);
      ObjectMapper objectMapper = new ObjectMapper();
      final JsonNode json = objectMapper.readTree(jsonTxt);
      if (json.isArray()) {
        for (JsonNode jsonNode : json) {

          final JsonNode mapping = jsonNode;
          String licId = mapping.get("exp").asText();
          ArrayNode arrayNode = (ArrayNode) mapping.get("names");
          if (arrayNode.isArray()) {
            for (JsonNode jsonNodeName : arrayNode) {
              final String name = jsonNodeName.asText();
              LICENCES_NAMES.add(name);
              LICENCE_NAME_TO_ID.put(name, licId);
            }
          }
        }
      }
    } catch (IOException e) {
      LOGGER.error("Failed to load load CycloneDX Mappings", e);
    }
    LOGGER.info("Initialized SPDX Licence File");
  }

  // TODO: add options to search for  license file
  public <T> void searchMetaData(
      LocalDependency depGav,
      MavenProject mavenProject,
      T component,
      BiConsumer<MavenProject, T> addMethod) {
    addMethod.accept(mavenProject, component);
    if (mavenProject.getParent() != null) {
      searchMetaData(depGav, mavenProject.getParent(), component, addMethod);
    } else if (mavenProject.getModel().getParent() != null) {
      //  also check the parents...
      final MavenProject parentProject = retrieveParentProject(depGav, mavenProject);
      if (parentProject != null) {
        searchMetaData(depGav, parentProject, component, addMethod);
      }
    }

    // make sure to set a NOASSERTION license in case we did not find a license in the parent
    // hierarchy
    addEmptyLicense(component, addMethod);
  }

  public static <T> void addEmptyLicense(T component, BiConsumer<MavenProject, T> addMethod) {
    MavenProject dummyProject = new MavenProject();
    License dummyLicense = new License();
    dummyLicense.setName(NOASSERTION);
    dummyProject.setLicenses(Collections.singletonList(dummyLicense));
    addMethod.accept(dummyProject, component);
  }

  /**
   * Search in the jar, zip, war, etc. file for license.txt, md files and calls the add method with
   * a dummy maven project containing the found licenses
   *
   * @param archive
   * @param addMethod
   */
  public <T> void searchLicenseFile(
      Path archive, BiConsumer<MavenProject, T> addMethod, T component) {
    try (FileSystem fs = FileSystems.newFileSystem(archive, null)) {
      Path root = fs.getPath("/");
      LicenseFileVisitor readZipFileVisitor = new LicenseFileVisitor();
      Files.walkFileTree(root, readZipFileVisitor);
      for (MavenProject mavenProject : readZipFileVisitor.getMavenProject()) {
        addMethod.accept(mavenProject, component);
      }

    } catch (IOException e) {
      LOGGER.error("Failed to read POM ", e);
    }
  }

  /**
   * TODO: adapted from BaseCyclonDX Mojo FIXME: check if the depGav needs to change Retrieves the
   * parent pom for an artifact (if any). The parent pom may contain license, description, and other
   * metadata whereas the artifact itself may not.
   *
   * @param project the maven project the artifact is part of
   */
  @Nullable
  private MavenProject retrieveParentProject(
      final LocalDependency localDependency, final MavenProject project) {
    if (localDependency == null || localDependency.getFilepath() == null) {
      return null;
    }
    Path jarFile = localDependency.getFilepath();
    if (jarFile == null || jarFile.getParent() == null) {
      return null;
    }
    Gav depGav = localDependency.getGav();
    if (depGav == null) {
      LOGGER.debug("Dependency without GAV {}", localDependency.getName());
      return null;
    }

    final Model model = project.getModel();
    if (model.getParent() != null) {
      final Parent parent = model.getParent();
      // Navigate out of version, artifactId, and first (possibly only) level of groupId
      final StringBuilder getout = new StringBuilder("../../../");
      final int periods =
          depGav.getGroupId().length() - depGav.getGroupId().replace(".", "").length();
      for (int i = 0; i < periods; i++) {
        getout.append("../");
      }
      final Path parentFile =
          Paths.get(
              jarFile.getParent().toAbsolutePath().toString(),
              getout.toString(),
              parent.getGroupId().replace(".", "/"),
              parent.getArtifactId(),
              parent.getVersion(),
              parent.getArtifactId() + "-" + parent.getVersion() + ".pom");
      if (Files.exists(parentFile) && Files.isRegularFile(parentFile)) {
        try {
          return PomFileUtil.readPom(parentFile);
        } catch (Exception e) {
          LOGGER.error("An error occurred retrieving an artifacts parent pom", e);
        }
      }
    }
    return null;
  }
}

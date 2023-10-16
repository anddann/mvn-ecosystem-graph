package de.upb.maven.ecosystem.licenses;

import com.google.common.base.Joiner;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.model.License;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.license.SpdxListedLicense;

public class LicenseFileVisitor implements FileVisitor<Path> {

  private static final Logger LOGGER = LoggerFactory.getLogger(LicenseFileVisitor.class);

  public Collection<MavenProject> getMavenProject() {
    return mavenProjectCache.values();
  }

  private HashMap<String, MavenProject> mavenProjectCache = new HashMap<>();
  private HashMap<MavenProject, HashSet<String>> mavenProjectLicenceName = new HashMap<>();

  private HashSet<String> seenDirs = new HashSet<>();

  private void addToMavenProjectList(Path licenseFilePath, License license) {
    // try to derive the gav from the path
    Path parent = licenseFilePath.getParent();
    String artifact = SearchLicensesUtility.NOASSERTION;
    String group = SearchLicensesUtility.NOASSERTION;
    while (parent != null) {
      final Path fileName = parent.getFileName();
      if (fileName == null) {
        break;
      }
      final String parentName = fileName.toString();
      if (!parentName.equals("META_INF")
          && parent != licenseFilePath.getRoot()
          && !parent.getFileName().toString().toUpperCase().contains("LICENSE")) {
        if (artifact.equals(SearchLicensesUtility.NOASSERTION)) {
          // first one is the artifact
          artifact = parentName;
        } else if (group.equals(SearchLicensesUtility.NOASSERTION)) {
          group = parentName;
          break;
        }
      } else {
        break;
      }
      parent = parent.getParent();
    }

    // find if we already have a project with the infroamtion
    String key = group + ":" + artifact;
    MavenProject foundMavenProject = mavenProjectCache.get(key);

    if (foundMavenProject == null) {
      foundMavenProject = new MavenProject();
      foundMavenProject.setArtifactId(artifact);
      foundMavenProject.setGroupId(group);
      foundMavenProject.setVersion(SearchLicensesUtility.NOASSERTION);
      mavenProjectCache.put(key, foundMavenProject);
      mavenProjectLicenceName.put(foundMavenProject, new HashSet<>());
    }

    String licenseKey = license.getName();
    final HashSet<String> strings = mavenProjectLicenceName.get(foundMavenProject);
    if (!strings.contains(licenseKey)) {
      foundMavenProject.addLicense(license);
      strings.add(licenseKey);
    }
  }

  @Override
  public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
    String fileName = file.getFileName().toString();
    LOGGER.info("Visiting {}", file.toAbsolutePath());
    final String baseName = FilenameUtils.getBaseName(file.getFileName().toString());
    final String ext = FilenameUtils.getExtension(file.getFileName().toString());
    if (SearchLicensesUtility.getInstance()
        .getUPPER_CASE_SpdxListedLicenseIds()
        .contains(baseName.toUpperCase())) {

      extractLicense(file, baseName);
      return FileVisitResult.CONTINUE;
    }

    if (ext.equalsIgnoreCase("json")) {
      // FIXME: do not try to parse json files for now
      return FileVisitResult.CONTINUE;
    }

    if (baseName.toUpperCase().contains("LICENSE")) {
      ArrayList<String> lines = new ArrayList<>();
      // parse the text and search for identifiers?
      try (BufferedReader inputStream =
          new BufferedReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
        String str;
        int counter = 0;
        int maxLines = 10;
        while ((str = inputStream.readLine()) != null && (counter <= maxLines)) {
          lines.add(str.replace("\n", " "));
          counter++;
        }
      } catch (java.nio.charset.MalformedInputException e) {
        LOGGER.debug("Not UTF-8 Encoding file {} ", file);
      }

      if (lines.isEmpty()) {
        return FileVisitResult.CONTINUE;
      }

      // set as name the first non-empty line
      // Fixme: is this a wie choice?
      String licenseName =
          lines.stream()
              .filter(x -> !x.isEmpty() && x.length() > 2)
              .limit(2)
              .map(String::trim)
              .collect(Collectors.joining(" "));

      // TODO:make this more efficient - instead of running over the text again and again
      for (String licenceId :
          SearchLicensesUtility.getInstance().getLICENCE_NAME_TO_ID().values()) {
        // here we only check by the id, e.g. APL-2.0, if the name is full Apache Licence
        // Version 2.0 we would miss it...
        // but for the fullname, we check below
        // TODO: improve matching, e.g. changing v. to Version in the licenseName, for.
        // licenseName.contains(licenceId) and  licenceName
        if (licenseName.contains(licenceId)) {
          extractLicense(file, licenceId);
          return FileVisitResult.CONTINUE;
        }
      }

      for (String licenceName : SearchLicensesUtility.getInstance().getLICENCES_NAMES()) {
        // Here we check for the fullname
        if (licenseName.contains(licenceName)) {
          extractLicense(
              file, SearchLicensesUtility.getInstance().getLICENCE_NAME_TO_ID().get(licenceName));
          return FileVisitResult.CONTINUE;
        }
      }

      // the found license is not a org.spdx license, thus add a customlicense
      CustomMavenLicense customMavenLicense = new CustomMavenLicense();
      customMavenLicense.setName(licenseName);

      final List<String> licenceTextLines = Files.readAllLines(file);
      String licenseText = Joiner.on("\n").join(licenceTextLines);

      if (licenseText != null && !licenseText.isEmpty()) {
        customMavenLicense.setLicenseText(licenseText);
      }
      addToMavenProjectList(file, customMavenLicense);
    }

    if (fileName.toUpperCase().equals("SPDX.XSD")) {
      // FIXME: what to do with these file
    }
    return FileVisitResult.CONTINUE;
  }

  public void extractLicense(Path file, String baseName) {
    try {
      final SpdxListedLicense listedLicenseById =
          SearchLicensesUtility.getInstance().getListedLicenses().getListedLicenseById(baseName);
      License license = new License();
      license.setName(listedLicenseById.getName());
      license.setUrl(listedLicenseById.getUri(null));
      addToMavenProjectList(file, license);
    } catch (InvalidSPDXAnalysisException e) {
      LOGGER.debug("Not a valid SPDXLicense", e);
    }
  }

  @Override
  public FileVisitResult visitFileFailed(Path file, IOException exc) {
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
    // filetreewalker has a bug that leads to an infinite recursion if the zip file has an entry
    // for "/": https://bugs.openjdk.java.net/browse/JDK-8197398
    // we hence have to skip the sub tree if we encounter "/" a second time
    if (seenDirs.contains(dir.toString())) {
      return FileVisitResult.SKIP_SUBTREE;
    }
    LOGGER.trace("Pre-Visiting {}", dir.toAbsolutePath());
    seenDirs.add(dir.toString());

    return FileVisitResult.CONTINUE;
  }
}

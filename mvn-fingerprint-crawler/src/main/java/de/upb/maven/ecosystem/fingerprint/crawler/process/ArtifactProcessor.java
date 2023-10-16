package de.upb.maven.ecosystem.fingerprint.crawler.process;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import de.upb.maven.ecosystem.ArtifactUtils;
import de.upb.maven.ecosystem.PomFileUtil;
import de.upb.maven.ecosystem.licenses.LicenseFileVisitor;
import de.upb.maven.ecosystem.msg.CustomArtifactInfo;
import de.upb.maven.ecosystem.persistence.common.DependencyScope;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.ClassFile;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Gav;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.License;
import de.upb.maven.ecosystem.persistence.fingerprint.model.dao.MavenArtifactMetadata;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipError;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

public class ArtifactProcessor {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ArtifactProcessor.class);
  private static final int CONNECT_TIMEOUT = 5 * 60000;
  private static final int READ_TIMEOUT = 5 * 60000;
  private final boolean computeTLSH;

  private final Path TEMP_LOCATION;

  private final long sootTimeoutSettingMS;

  public ArtifactProcessor(boolean computeTLSH, long sootTimeoutSettingMS) throws IOException {
    this.computeTLSH = computeTLSH;
    this.sootTimeoutSettingMS = sootTimeoutSettingMS;
    TEMP_LOCATION = Files.createTempDirectory(RandomStringUtils.randomAlphabetic(10));
  }

  public ArtifactProcessor(long sootTimeoutSettingMS) throws IOException {
    this(true, sootTimeoutSettingMS);
  }

  @Nullable
  public MavenArtifactMetadata process(
      CustomArtifactInfo info, int crawledArtifacts, URL downloadURL) {

    MavenArtifactMetadata metadata = null;
    Path jarLocation = null;
    try {

      LOGGER.info("Processing Artifact#{} at url {}", crawledArtifacts, downloadURL);

      Stopwatch stopwatch = Stopwatch.createStarted();
      // 2. Download file
      // 2.1 try by URL
      try {
        jarLocation = downloadFilePlainURL(info, TEMP_LOCATION);
      } catch (IOException ex) {
        LOGGER.error("Plain Downloaded file failed with: {}", ex.getMessage());
      }
      stopwatch.stop();
      if (jarLocation == null) {
        return null;
      }
      LOGGER.info(
          "[Stats] Downloading {} took {}",
          jarLocation.getFileName().toString(),
          stopwatch.elapsed());

      metadata = new MavenArtifactMetadata();
      metadata.setRepository(info.getRepoURL());
      Gav orgArtifactGAV = new Gav();
      orgArtifactGAV.setGroupId(info.getGroupId());
      orgArtifactGAV.setArtifactId(info.getArtifactId());
      orgArtifactGAV.setVersionId(info.getArtifactVersion());
      final Set<License> licenses = new HashSet<>();
      // get the licence info directly from the artifact info
      if (info.getBundleLicense() != null) {
        License licence4Artifact = new License();

        licence4Artifact.setName(info.getBundleLicense());
        if (info.getDistribution() != null) {
          licence4Artifact.setDistribution(info.getDistribution());
        }
        if (info.getLicenseUrl() != null) {
          licence4Artifact.setUrl(info.getLicenseUrl());
        }
        licenses.add(licence4Artifact);
      }

      metadata.setGav(orgArtifactGAV);
      metadata.setDownloadUrl(downloadURL.toString());
      metadata.setClassifier(info.getClassifier());

      // Compute further information from the pom
      getInfoFromPom(info, metadata);

      stopwatch.reset();
      stopwatch.start();
      // compute the digest of the file
      String jarDigest = FingerPrintComputation.getSha256DigestFor(jarLocation);
      metadata.setSha256(jarDigest);

      LOGGER.info(
          "[Stats] Computing JarDigest {} took {}",
          jarLocation.getFileName().toString(),
          stopwatch.elapsed());

      // 3. Process Jar contents
      processJarContent(jarLocation, metadata);

      LOGGER.info("Done crawling Artifact#{}", crawledArtifacts);

    } catch (IOException | SecurityException e) {
      LOGGER.error("Exception thrown {}, {}", e, e.getStackTrace());
    } finally {
      // 5. Delete temp folder contents
      try {
        if (jarLocation != null) {
          Files.delete(jarLocation);
        }
        if (TEMP_LOCATION != null) {
          FileUtils.deleteDirectory(TEMP_LOCATION.toFile());
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return metadata;
  }

  /**
   * Extract the license and dependency information from the pom.xml file of that artifact and addes
   * them to the mavenArtifactMetadat
   *
   * @param info
   * @param mavenArtifactMetadata
   */
  public void getInfoFromPom(
      final CustomArtifactInfo info, @NotNull MavenArtifactMetadata mavenArtifactMetadata) {

    assert mavenArtifactMetadata != null;
    // Derive pom.xml from
    CustomArtifactInfo pomInfo = new CustomArtifactInfo();
    pomInfo.setClassifier(info.getClassifier());
    pomInfo.setGroupId(info.getGroupId());
    pomInfo.setArtifactVersion(info.getArtifactVersion());
    pomInfo.setArtifactId(info.getArtifactId());
    pomInfo.setBundleLicense(info.getBundleLicense());
    pomInfo.setRepoURL(info.getRepoURL());
    pomInfo.setFileExtension("pom");
    Path pomLocation = null;
    try {
      pomLocation = downloadFilePlainURL(pomInfo, TEMP_LOCATION);

      final MavenProject mavenProject = PomFileUtil.readPom(pomLocation);
      if (mavenProject != null) {

        // find for a source-code repo url

        Scm scm = mavenProject.getScm();
        if (scm != null) {
          String url = scm.getUrl();
          if (StringUtils.isNotBlank(url)) {
            mavenArtifactMetadata.setScmUrl(url);
          }
        }

        // find the licences
        final List<org.apache.maven.model.License> licenses = mavenProject.getLicenses();
        HashSet<License> licenseArrayList = new HashSet<>();

        // find the licenses
        for (org.apache.maven.model.License mvnLicense : licenses) {
          License l = createFrom(mvnLicense);
          licenseArrayList.add(l);
        }
        if (mavenArtifactMetadata.getGav() != null) {
          mavenArtifactMetadata.getGav().setLicences(licenseArrayList);

        } else {
          LOGGER.error("Artifact has no GAV");
        }

        // find the dependencies, and add them
        ArrayList<de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Dependency>
            directDependencyList = new ArrayList<>();
        ArrayList<de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Dependency>
            optionalDependencyList = new ArrayList<>();
        List<Dependency> dependencies = mavenProject.getDependencies();
        for (Dependency mavenDep : dependencies) {
          de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Dependency depModel =
              new de.upb.maven.ecosystem.persistence.fingerprint.model.dao.Dependency();
          Gav depGav = new Gav();
          depGav.setArtifactId(mavenDep.getArtifactId());
          depGav.setGroupId(mavenDep.getGroupId());
          depGav.setVersionId(mavenDep.getVersion());

          depModel.setGav(depGav);
          depModel.setClassifier(mavenDep.getClassifier());

          String mavenScope = mavenDep.getScope();
          DependencyScope dependencyScope = DependencyScope.COMPILE;
          if (StringUtils.isNotBlank(mavenScope)) {
            if (mavenScope.equalsIgnoreCase("compile")) {
              dependencyScope = DependencyScope.COMPILE;
            } else if (mavenScope.equalsIgnoreCase("runtime")) {
              dependencyScope = DependencyScope.RUNTIME;
            } else if (mavenScope.equalsIgnoreCase("provided")) {
              dependencyScope = DependencyScope.PROVIDED;
            } else if (mavenScope.equalsIgnoreCase("test")) {
              dependencyScope = DependencyScope.TEST;
            } else if (mavenScope.equalsIgnoreCase("system")) {
              dependencyScope = DependencyScope.SYSTEM;
            }
          }
          depModel.setScope(dependencyScope);

          if (mavenDep.isOptional()) {
            optionalDependencyList.add(depModel);
          } else {
            directDependencyList.add(depModel);
          }
        }
        mavenArtifactMetadata.setDirectDependencies(directDependencyList);
        mavenArtifactMetadata.setOptionalDependencies(optionalDependencyList);
      }

    } catch (IOException ex) {
      LOGGER.error("Downloading or parsing of .pom failed with: {}", ex.getMessage());
    } finally {
      // 5. Delete temp folder contents
      try {
        if (pomLocation != null) {
          Files.delete(pomLocation);
        }
      } catch (IOException e) {

      }
    }
  }

  private Path downloadFilePlainURL(CustomArtifactInfo info, Path downloadFolder)
      throws IOException {
    URL downloadURL = ArtifactUtils.constructURL(info);

    String classifier = "";
    if (info.getClassifier() != null) {
      classifier = "-" + info.getClassifier();
    }

    String jarName =
        info.getArtifactId()
            + "-"
            + info.getArtifactVersion()
            + classifier
            + "."
            + info.getFileExtension();
    Path fileName = downloadFolder.resolve(jarName);

    FileUtils.copyURLToFile(downloadURL, fileName.toFile(), CONNECT_TIMEOUT, READ_TIMEOUT);

    if (!Files.exists(fileName)) {
      throw new IOException("Failed to download jar: " + jarName);
    }
    LOGGER.info("Downloaded file from plain url: {}", downloadURL);

    return fileName;
  }

  public void processJarContent(Path pathToJar, MavenArtifactMetadata metadata) throws IOException {
    List<String> exceptions = Lists.newArrayList();
    HashMap<String, String[]> computedTLSHandSHA256 = new HashMap<>();
    Stopwatch stopwatch = Stopwatch.createStarted();

    try {
      FingerPrintComputation.FingerPrintComputationBuilder fpBuilder =
          new FingerPrintComputation.FingerPrintComputationBuilder(Collections.emptyList());
      fpBuilder.setSootTimeOut(sootTimeoutSettingMS);
      FingerPrintComputation fingerPrintComputation = fpBuilder.build();
      computedTLSHandSHA256 = fingerPrintComputation.invokejNorm(pathToJar);

      LOGGER.info(
          "[Stats] jNorm {} took {}", pathToJar.getFileName().toString(), stopwatch.elapsed());

    } catch (FingerPrintComputation.SootProcessInteruptedException e) {
      exceptions.add(e.getClass().getCanonicalName());
      LOGGER.error("Soot failed with: ", e);
    }

    stopwatch.reset();
    stopwatch.start();
    Set<Gav> embeddedGavs = new HashSet<>();
    Set<ClassFile> classFiles = new HashSet<>();

    // would it make sense to load the jar into memory before reading its content?
    try (JarFile jarFile = new JarFile(pathToJar.toFile())) {
      Enumeration<JarEntry> entries = jarFile.entries();

      while (entries.hasMoreElements()) {
        JarEntry jarEntry = entries.nextElement();
        if (jarEntry.isDirectory()) {
          continue;
        }
        String fileName = jarEntry.getName();
        if (fileName.endsWith("pom.xml")) {

          try (InputStream inputStream = jarFile.getInputStream(jarEntry);
              InputStream pomStream = new BufferedInputStream(inputStream)) {

            Gav gav = new Gav();
            final MavenProject mavenProject = PomFileUtil.readPom(pomStream);
            if (mavenProject != null) {
              gav.setGroupId(mavenProject.getGroupId());
              gav.setArtifactId(mavenProject.getArtifactId());
              gav.setVersionId(mavenProject.getVersion());
              final List<org.apache.maven.model.License> licenses = mavenProject.getLicenses();
              HashSet<de.upb.maven.ecosystem.persistence.fingerprint.model.dao.License>
                  licenseArrayList = new HashSet<>();
              for (org.apache.maven.model.License mvnLicense : licenses) {
                de.upb.maven.ecosystem.persistence.fingerprint.model.dao.License l =
                    createFrom(mvnLicense);
                licenseArrayList.add(l);
              }
              gav.setLicences(licenseArrayList);
            }
            embeddedGavs.add(gav);
          } catch (IOException e) {
            LOGGER.error("Error parsing pom of " + pathToJar, e);
          }

        } else if (fileName.endsWith(".class")) {

          ClassFile clsF = new ClassFile();
          clsF.setFilename(getFilename(fileName));
          clsF.setFullyQualifiedPath(fileName);
          clsF.setTimestamp(jarEntry.getLastModifiedTime().toMillis());
          String tlsh = FingerPrintComputation.getTLSHandSHA256(computedTLSHandSHA256, fileName);
          clsF.setTlsh(tlsh);
          String sha = FingerPrintComputation.getSHA(computedTLSHandSHA256, fileName);
          clsF.setJimpleSha256(sha);

          try (InputStream inputStream = jarFile.getInputStream(jarEntry);
              InputStream classStream = new BufferedInputStream(inputStream)) {
            String sha256 = FingerPrintComputation.getSha256DigestFor(classStream);
            clsF.setSha256(sha256);
          }
          classFiles.add(clsF);
        }
      }
    }
    stopwatch.stop();
    LOGGER.info(
        "[Stats] SHA256 for {} with #{} took {}",
        pathToJar.getFileName().toString(),
        classFiles.size(),
        stopwatch.elapsed());

    if (classFiles.size() != computedTLSHandSHA256.keySet().size()) {
      LOGGER.warn(
          "Number of computed TLSH ({}) differs from available .class files ({}) in jar",
          computedTLSHandSHA256.keySet().size(),
          classFiles.size());
    }

    // walks through the jar to find license files
    searchForLicenseFiles(pathToJar, metadata);

    metadata.setClassFile(classFiles);
    metadata.setEmbeddedGavs(embeddedGavs);
    metadata.setCrawlingExceptions(exceptions);
  }

  private void searchForLicenseFiles(Path jarArchive, MavenArtifactMetadata metadata) {
    LOGGER.info("Start searching for licenses");
    Stopwatch stopwatch = Stopwatch.createStarted();
    Set<License> licences = metadata.getGav().getLicences();
    if (licences == null) {
      licences = Sets.newHashSet();
    }
    try (FileSystem fs = FileSystems.newFileSystem(jarArchive, null)) {
      LicenseFileVisitor readZipFileVisitor = new LicenseFileVisitor();
      Path root = fs.getPath("/");
      Files.walkFileTree(root, readZipFileVisitor);
      for (MavenProject mavenProject : readZipFileVisitor.getMavenProject()) {
        final List<org.apache.maven.model.License> licenses = mavenProject.getLicenses();
        for (org.apache.maven.model.License mvnLicense : licenses) {
          License foundLicense = createFrom(mvnLicense);
          // check if the license belongs to an embedded project; add to the license
          if (mavenProject.getGroupId().equalsIgnoreCase(metadata.getGav().getGroupId())
              && mavenProject.getArtifactId().equalsIgnoreCase(metadata.getGav().getArtifactId())) {
            licences.add(foundLicense);
          }
        }
      }
    } catch (ZipError | IOException e) {
      LOGGER.error("Failed to search for license files");
    }
    stopwatch.stop();
    LOGGER.info("[Stats] Searching for Licenses took {}", stopwatch.elapsed());
  }

  private License createFrom(org.apache.maven.model.License mvnLicense) {
    License foundLicense = new License();
    foundLicense.setName(mvnLicense.getName());
    foundLicense.setComments(mvnLicense.getComments());
    foundLicense.setUrl(mvnLicense.getUrl());
    foundLicense.setDistribution(mvnLicense.getDistribution());
    return foundLicense;
  }

  private String getFilename(String fileName) {
    int lastIndexOf = fileName.lastIndexOf("/");
    return lastIndexOf > 0 ? fileName.substring(lastIndexOf + 1) : fileName;
  }
}

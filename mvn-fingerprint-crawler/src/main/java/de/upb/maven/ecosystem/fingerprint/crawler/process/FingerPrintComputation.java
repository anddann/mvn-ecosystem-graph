package de.upb.maven.ecosystem.fingerprint.crawler.process;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jnorm.cli.CliHandler;
import jnorm.cli.Main;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import soot.G;

public class FingerPrintComputation {

  private static final org.slf4j.Logger LOGGER =
      LoggerFactory.getLogger(FingerPrintComputation.class);
  private static ExecutorService newSingleThreadExecutor;
  private Path tmpDir;

  public static class FingerPrintComputationBuilder {

    Collection<Path> classPathEntries;
    @NotNull long sootTimeoutSettingMS = 5 * 60 * 1000;

    public FingerPrintComputationBuilder(Collection<Path> classPathEntries) {
      this.classPathEntries = classPathEntries;
    }

    public FingerPrintComputationBuilder(Path classPathEntry) {
      this(Collections.singletonList(classPathEntry));
    }

    public FingerPrintComputationBuilder setSootTimeOut(long inMilliSeconds) {
      this.sootTimeoutSettingMS = inMilliSeconds;
      return this;
    }

    public FingerPrintComputation build() {
      return new FingerPrintComputation(this);
    }
  }

  protected FingerPrintComputation(FingerPrintComputationBuilder builder) {
    this.sootTimeoutSetting = builder.sootTimeoutSettingMS;
    this.classPathEntries = builder.classPathEntries;
    try {
      this.tmpDir = Files.createTempDirectory("scopeDir");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private final Collection<Path> classPathEntries;
  private final long sootTimeoutSetting;
  private final HashMap<String, String[]> computedTLSHandJimpleSHA256 = new HashMap<>();
  private final HashSet<Path> failedOrDone = new HashSet<>();

  public static void shutdown() {
    if (newSingleThreadExecutor != null && !newSingleThreadExecutor.isShutdown()) {
      newSingleThreadExecutor.shutdown();
    }
  }

  public String getSha256For(String fqnJavaClassName) {
    Pair<Path, String> foundFileInPath =
        getClassPathEntryFor(
            fqnJavaClassName,
            classPathEntries.stream()
                .map(x -> x.toAbsolutePath().toString())
                .collect(Collectors.toList()));

    if (foundFileInPath != null) {
      return getSha256DigestFor(foundFileInPath.getKey());
    }

    return null;
  }

  public static String getSha256DigestFor(Path key) {
    String digest = null;
    try {
      digest = new DigestUtils(DigestUtils.getSha256Digest()).digestAsHex(key.toFile());
    } catch (IOException e) {
      e.printStackTrace();
    }
    return digest;
  }

  public static String getSha256DigestFor(InputStream inputStream) {
    String digest = null;
    try {
      digest = new DigestUtils(DigestUtils.getSha256Digest()).digestAsHex(inputStream);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return digest;
  }

  public static String getTLSHDigestFor(InputStream inputStream) {
    String digest = null;
    try {
      digest = new DigestUtils(DigestUtils.getSha256Digest()).digestAsHex(inputStream);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return digest;
  }

  // FIXME: move to classpath
  private static Pair<Path, String> getClassPathEntryFor(
      String fqnClassName, Iterable<String> classPathEntries) {
    String fqn2ClassFile = NameUtils.toFileName(fqnClassName);

    for (String classPathEntry : classPathEntries) {

      Path foundFile = lookUpInClassPath(fqn2ClassFile, classPathEntry);

      if (foundFile != null) {
        return Pair.of(foundFile, classPathEntry);
      }
    }
    return null;
  }

  // FIXME move to classpath
  public static Path lookUpInClassPath(String classFileName, String classPathEntry) {
    if (!classFileName.endsWith(".class")) {
      throw new IllegalArgumentException("" + classFileName + " is not a valid file name!");
    }

    Path rootDir = Paths.get(classPathEntry);
    Path foundFile = rootDir.resolve(classFileName);
    if (Files.exists(foundFile) && Files.isRegularFile(foundFile)) {
      return foundFile;
    }
    return null;
  }

  private static ArrayList<String> buildjNormMainArguments(
      String classPathEntry, @Nullable String outputDir) {
    ArrayList<String> args = new ArrayList<>();
    args.add("-" + CliHandler.inputDirOpt);
    args.add(classPathEntry);

    // jnorm optimize options
    args.add("-" + CliHandler.optimizationOpt);
    args.add("-" + CliHandler.aggressiveOpt);
    args.add("-" + CliHandler.packageOpt);
    args.add("-" + CliHandler.normalizationOpt);
    args.add("-" + CliHandler.standardizationOpt);
    args.add("-" + CliHandler.simpleRenameOpt);

    if (outputDir != null) {
      args.add("-" + CliHandler.outputDirOpt);
      args.add(outputDir);
    }

    return args;
  }

  /**
   * Takes a jar file and computes all TLSH as strings.
   *
   * @param classPathEntry the jar file
   * @return a map of FQN -> TLSH hashes.
   * @throws SootProcessInteruptedException
   */
  private static HashMap<String, String[]> run_in_same_thread(
      String classPathEntry, @Nullable String outputDir) {
    HashMap<String, String[]> computedTLSHs = new HashMap<>();
    try {
      ArrayList<String> args = buildjNormMainArguments(classPathEntry, outputDir);

      Main.main(args.toArray(new String[args.size()]));
      G.reset();
      G.v().resetSpark();
      // compute sha256 and tlsh on the jimple files
      // i terate trhough output ir
      // filename to class name
      HashMap<String, String[]> computedTLSHandJimpleSHA256 = new HashMap<>();
      final Path outputDirPath = Paths.get(outputDir);
      try (Stream<Path> stream = Files.walk(outputDirPath)) {
        stream
            .filter(Files::isRegularFile)
            .filter(x -> StringUtils.endsWith(x.getFileName().toString(), ".jimple"))
            .forEach(
                x -> {
                  // compute sha
                  try {
                    final Path relativize = outputDirPath.relativize(x);
                    String className =
                        relativize
                            .getFileName()
                            .toString()
                            .replace(".jimple", "")
                            .replace("/", ".");

                    final InputStream inputStream = Files.newInputStream(x);
                    BufferedInputStream buffInputStr = new BufferedInputStream(inputStream);
                    // Mark is set on the input stream
                    buffInputStr.mark(0);

                    final String sha256DigestFor = getSha256DigestFor(buffInputStr);
                    buffInputStr.reset();
                    final String tlshDigestFor = getTLSHDigestFor(buffInputStr);

                    computedTLSHandJimpleSHA256.put(
                        className, new String[] {tlshDigestFor, sha256DigestFor});

                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
      }

    } catch (IOException ex) {

    }
    return computedTLSHs;
  }

  /**
   * Runs soot in the same Process and cleans up memory afterwards
   *
   * @param pathToJar
   * @param sootTimeout
   * @param outputFolder
   * @return
   * @throws SootProcessInteruptedException
   */
  private static HashMap<String, String[]> invokeSameProcess(
      Path pathToJar, long sootTimeout, String outputFolder) throws SootProcessInteruptedException {

    if (newSingleThreadExecutor == null) {
      newSingleThreadExecutor = Executors.newSingleThreadExecutor();
    }
    Callable<HashMap<String, String[]>> task =
        () ->
            FingerPrintComputation.run_in_same_thread(
                pathToJar.toAbsolutePath().toString(), outputFolder);
    Future<HashMap<String, String[]>> future = newSingleThreadExecutor.submit(task);
    try {
      HashMap<String, String[]> computedTLSHandJimpleSHA256 =
          future.get(sootTimeout, TimeUnit.MILLISECONDS);
      return computedTLSHandJimpleSHA256;
    } catch (TimeoutException e) {
      throw new SootProcessInteruptedException(e);
    } catch (InterruptedException e) {
      throw new SootProcessInteruptedException(e);
    } catch (ExecutionException e) {
      throw new SootProcessInteruptedException(e);
    } catch (RuntimeException e) {
      System.err.println("Caught OutOfMemoryError");
      throw new SootProcessInteruptedException(e);
    } finally {
      future.cancel(true); // may or may not desire this
      // clean up memory afterwars
      G.v().resetSpark();
      G.reset();
    }
  }

  /**
   * Timeouts after SOOT_TIMEOUT_SECONDS
   *
   * @param fqnClassName
   * @return the computed tlsh and sha256
   * @throws SootProcessInteruptedException
   */
  public String[] getTLSHandSHA256(String fqnClassName) throws SootProcessInteruptedException {
    if (computedTLSHandJimpleSHA256.containsKey(fqnClassName)) {
      return computedTLSHandJimpleSHA256.get(fqnClassName);
    } else {
      Pair<Path, String> fileEntry =
          getClassPathEntryFor(
              fqnClassName,
              this.classPathEntries.stream()
                  .map(x -> x.toAbsolutePath().toString())
                  .collect(Collectors.toList()));
      if (fileEntry != null) {

        if (failedOrDone.contains(fileEntry)) {
          throw new SootProcessInteruptedException("");
        }
        HashMap<String, String[]> computed = null;
        try {
          computed = invokejNorm(fileEntry.getKey());
          this.failedOrDone.add(fileEntry.getKey());
        } catch (SootProcessInteruptedException e) {
          throw e;
        }
        computedTLSHandJimpleSHA256.putAll(computed);
      }
    }

    return computedTLSHandJimpleSHA256.get(fqnClassName);
  }

  public HashMap<String, String[]> invokejNorm() {
    if (classPathEntries != null) {
      for (Path ent : classPathEntries) {
        try {
          this.computedTLSHandJimpleSHA256.putAll(invokejNorm(ent));
        } catch (SootProcessInteruptedException e) {
          e.printStackTrace();
        }
      }
    }
    return this.computedTLSHandJimpleSHA256;
  }

  public HashMap<String, String[]> invokejNorm(Path pathToJar)
      throws SootProcessInteruptedException {
    HashMap<String, String[]> computedTLSHandJimpleSHA256 = new HashMap<>();

    computedTLSHandJimpleSHA256 =
        invokeSameProcess(
            pathToJar, this.sootTimeoutSetting, this.tmpDir.toAbsolutePath().toString());

    this.computedTLSHandJimpleSHA256.putAll(computedTLSHandJimpleSHA256);
    return computedTLSHandJimpleSHA256;
  }

  @Nullable
  public static String getTLSHandSHA256(
      HashMap<String, String[]> computedTLSHandJimpleSHA256, String name) {
    String stripSuffic = name.replace(".class", "").replace("/", ".");
    String[] hashes = computedTLSHandJimpleSHA256.get(stripSuffic);
    if (hashes == null || StringUtils.isEmpty(hashes[0])) {
      LOGGER.debug("No TLSH found for {}", stripSuffic);
      return null;
    }
    return hashes[0];
  }

  @Nullable
  public static String getSHA(HashMap<String, String[]> computedTLSHandJimpleSHA256, String name) {
    String stripSuffic = name.replace(".class", "").replace("/", ".");
    String[] hashes = computedTLSHandJimpleSHA256.get(stripSuffic);
    if (hashes == null || StringUtils.isEmpty(hashes[1])) {
      LOGGER.debug("No SHA256 found for {}", stripSuffic);
      return null;
    }
    return hashes[1];
  }

  public static class SootProcessInteruptedException extends Exception {
    public SootProcessInteruptedException(Exception e) {
      super(e);
    }

    public SootProcessInteruptedException(String message) {
      super(message);
    }
  }
}

package com.pty4j.util;

import com.google.common.base.MoreObjects;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class ExtractedNative {

  private static final Logger LOG = Logger.getLogger(ExtractedNative.class);
  static final String[] LOCATIONS = {
      "freebsd/x86/libpty.so",
      "freebsd/x86_64/libpty.so",
      "linux/x86/libpty.so",
      "linux/x86_64/libpty.so",
      "linux/aarch64/libpty.so",
      "linux/ppc64le/libpty.so",
      "linux/mips64el/libpty.so",
      "macosx/x86/libpty.dylib",
      "macosx/x86_64/libpty.dylib",
      "win/x86/winpty-agent.exe",
      "win/x86/winpty.dll",
      "win/x86_64/cyglaunch.exe",
      "win/x86_64/winpty-agent.exe",
      "win/x86_64/winpty.dll",
      "win/xp/winpty-agent.exe",
      "win/xp/winpty.dll"
  };
  static final String DEFAULT_RESOURCE_NAME_PREFIX = "resources/com/pty4j/native/";

  private static final ExtractedNative INSTANCE = new ExtractedNative();
  private String myPlatformFolderName;
  private String myArchFolderName;
  private String myResourceNamePrefix;
  private boolean myInitialized;
  private volatile File myDestDir;

  private ExtractedNative() {
    this(null, null, null);
  }

  ExtractedNative(@Nullable String platformFolderName,
                  @Nullable String archFolderName,
                  @Nullable String resourceNamePrefix) {
    myPlatformFolderName = platformFolderName;
    myArchFolderName = archFolderName;
    myResourceNamePrefix = resourceNamePrefix;
  }

  @NotNull
  public static ExtractedNative getInstance() {
    return INSTANCE;
  }

  @NotNull
  File getDestDir() {
    if (!myInitialized) {
      init();
    }
    return myDestDir;
  }

  private void init() {
    try {
      myPlatformFolderName = MoreObjects.firstNonNull(myPlatformFolderName, PtyUtil.getPlatformFolderName());
      myArchFolderName = MoreObjects.firstNonNull(myArchFolderName, PtyUtil.getPlatformArchFolderName());
      myResourceNamePrefix = MoreObjects.firstNonNull(myResourceNamePrefix, DEFAULT_RESOURCE_NAME_PREFIX);
      synchronized (this) {
        if (!myInitialized) {
          doInit();
        }
        myInitialized = true;
      }
    } catch (Exception e) {
      throw new IllegalStateException("Cannot extract pty4j native " + myPlatformFolderName + "/" + myArchFolderName, e);
    }
  }

  private void doInit() throws IOException {
    long startTimeNano = System.nanoTime();
    Path destDir = getOrCreateDestDir();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Found " + destDir.toString() + " in " + pastTime(startTimeNano));
    }
    //noinspection RedundantTypeArguments
    List<Path> children = Files.list(destDir).collect(Collectors.<Path>toList());
    if (LOG.isDebugEnabled()) {
      LOG.debug("Listed files in " + pastTime(startTimeNano));
    }
    //noinspection Convert2Diamond
    Map<String, Path> resourceToFileMap = new HashMap<String, Path>();
    for (Path child : children) {
      String resourceName = getResourceName(child.getFileName().toString());
      resourceToFileMap.put(resourceName, child);
    }
    Set<String> bundledResourceNames = getBundledResourceNames();
    boolean upToDate = isUpToDate(bundledResourceNames, resourceToFileMap);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Checked upToDate in " + pastTime(startTimeNano));
    }
    if (!upToDate) {
      for (Path child : children) {
        Files.delete(child);
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Cleared directory in " + pastTime(startTimeNano));
      }
      for (String bundledResourceName : bundledResourceNames) {
        copy(bundledResourceName, destDir);
      }
      if (LOG.isDebugEnabled()) {
        LOG.debug("Copied " + bundledResourceNames + " in " + pastTime(startTimeNano));
      }
    }
    myDestDir = destDir.toFile();
    LOG.info("Extracted pty4j native in " + pastTime(startTimeNano));
  }

  @NotNull
  private Path getOrCreateDestDir() throws IOException {
    String staticParentDirPath = System.getProperty("pty4j.tmpdir");
    String prefix = "pty4j-" + myPlatformFolderName + "-" + myArchFolderName;
    if (staticParentDirPath != null && !staticParentDirPath.trim().isEmpty()) {
      // It's assumed that "pty4j.tmpdir" directory should not be used by several processes with pty4j simultaneously.
      // And several pty4j.jar versions can't coexist in classpath of a process.
      Path staticParentDir = Paths.get(staticParentDirPath);
      if (staticParentDir.isAbsolute()) {
        Path staticDir = staticParentDir.resolve(prefix);
        if (Files.isDirectory(staticDir)) {
          return staticDir;
        }
        if (Files.isDirectory(staticParentDir)) {
          if (Files.exists(staticDir)) {
            Files.delete(staticDir);
          }
          return Files.createDirectory(staticDir);
        }
      }
    }
    Path tempDirectory = Files.createTempDirectory(prefix + "-");
    tempDirectory.toFile().deleteOnExit();
    return tempDirectory;
  }

  private boolean isUpToDate(@NotNull Set<String> bundledResourceNames, @NotNull Map<String, Path> resourceToFileMap) {
    if (!bundledResourceNames.equals(resourceToFileMap.keySet())) {
      return false;
    }
    for (Map.Entry<String, Path> entry : resourceToFileMap.entrySet()) {
      try {
        URL bundledUrl = getBundledResourceUrl(entry.getKey());
        byte[] bundledContentChecksum = md5(bundledUrl.openStream());
        byte[] fileContentChecksum = md5(Files.newInputStream(entry.getValue()));
        if (!Arrays.equals(bundledContentChecksum, fileContentChecksum)) {
          return false;
        }
      } catch (Exception e) {
        LOG.error("Cannot compare md5 checksums", e);
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static byte[] md5(@NotNull InputStream in) throws IOException, NoSuchAlgorithmException {
    try {
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      byte[] buffer = new byte[8192];
      int bufferSize;
      while ((bufferSize = in.read(buffer)) >= 0) {
        md5.update(buffer, 0, bufferSize);
      }
      return md5.digest();
    } finally {
      try {
        in.close();
      } catch (IOException e) {
        LOG.error("Cannot close", e);
      }
    }
  }

  @NotNull
  private Set<String> getBundledResourceNames() {
    //noinspection Convert2Diamond
    Set<String> resourceNames = new HashSet<String>();
    String prefix = myPlatformFolderName + "/" + myArchFolderName + "/";
    for (String location : LOCATIONS) {
      if (location.startsWith(prefix)) {
        resourceNames.add(myResourceNamePrefix + location);
      }
    }
    return resourceNames;
  }

  @NotNull
  private String getResourceName(@NotNull String fileName) {
    return myResourceNamePrefix + myPlatformFolderName + "/" + myArchFolderName + "/" + fileName;
  }

  private void copy(@NotNull String resourceName, @NotNull Path destDir) throws IOException {
    URL url = getBundledResourceUrl(resourceName);
    int lastNameInd = resourceName.lastIndexOf('/');
    String name = lastNameInd != -1 ? resourceName.substring(lastNameInd + 1) : resourceName;
    InputStream inputStream = url.openStream();
    //noinspection TryFinallyCanBeTryWithResources
    try {
      Files.copy(inputStream, destDir.resolve(name));
    }
    finally {
      inputStream.close();
    }
  }

  @NotNull
  private URL getBundledResourceUrl(@NotNull String resourceName) throws IOException {
    ClassLoader classLoader = ExtractedNative.class.getClassLoader();
    URL url = classLoader.getResource(resourceName);
    if (url == null) {
      ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
      if (contextClassLoader != null) {
        url = contextClassLoader.getResource(resourceName);
      }
      if (url == null) {
        throw new IOException("Unable to load " + resourceName);
      }
    }
    return url;
  }

  @NotNull
  private static String pastTime(long startTimeNano) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNano) + " ms";
  }
}

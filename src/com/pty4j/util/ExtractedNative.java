package com.pty4j.util;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class ExtractedNative {

  static final String[] LOCATIONS = {
      "freebsd/x86/libpty.so",
      "freebsd/x86_64/libpty.so",
      "linux/x86/libpty.so",
      "linux/x86_64/libpty.so",
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
  private static final String RESOURCE_NAME_PREFIX = "resources/com/pty4j/native";

  private static final ExtractedNative INSTANCE = new ExtractedNative();
  private final String myPlatformFolderName;
  private final String myArchFolderName;
  private boolean myInitialized;
  private volatile File myDestDir;

  private ExtractedNative() {
    this(null, null);
  }

  ExtractedNative(@Nullable String platformFolderName, @Nullable String archFolderName) {
    myPlatformFolderName = platformFolderName;
    myArchFolderName = archFolderName;
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
    String platformFolderName = MoreObjects.firstNonNull(myPlatformFolderName, PtyUtil.getPlatformFolderName());
    String archFolderName = MoreObjects.firstNonNull(myArchFolderName, PtyUtil.getPlatformArchFolderName());
    try {
      synchronized (this) {
        if (!myInitialized) {
          doInit(platformFolderName, archFolderName);
        }
        myInitialized = true;
      }
    } catch (Exception e) {
      throw new IllegalStateException("Cannot extract pty4j native for " + platformFolderName + "/" + archFolderName, e);
    }
  }

  private void doInit(@NotNull String platformFolderName, @NotNull String archFolderName) throws IOException {
    Path destDir = createTempDir("pty4j-" + platformFolderName + "-" + archFolderName + "-");
    myDestDir = destDir.toFile();
    myDestDir.deleteOnExit();
    String prefix = platformFolderName + "/" + archFolderName + "/";
    for (String location : LOCATIONS) {
      if (location.startsWith(prefix)) {
        copy(location, destDir);
      }
    }
  }

  private void copy(@NotNull String resourceNameSuffix, Path destDir) throws IOException {
    ClassLoader classLoader = ExtractedNative.class.getClassLoader();
    String resourceName = RESOURCE_NAME_PREFIX + "/" + resourceNameSuffix;
    URL url = classLoader.getResource(resourceName);
    if (url == null) {
      throw new RuntimeException("Unable to load " + resourceName);
    }
    int lastNameInd = resourceNameSuffix.lastIndexOf('/');
    String name = lastNameInd != -1 ? resourceNameSuffix.substring(lastNameInd + 1) : resourceNameSuffix;
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
  private static Path createTempDir(@NotNull String prefix) throws IOException {
    String tmpDirPath = System.getProperty("pty4j.tmpdir");
    if (tmpDirPath != null && !tmpDirPath.trim().isEmpty()) {
      Path tmpDir = Paths.get(tmpDirPath);
      if (Files.isDirectory(tmpDir)) {
        return Files.createTempDirectory(tmpDir, prefix);
      }
    }
    return Files.createTempDirectory(prefix);
  }
}

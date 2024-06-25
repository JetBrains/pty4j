package com.pty4j;

import com.pty4j.util.PtyUtil;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32;
import jtermios.JTermios;
import kotlin.KotlinVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author traff
 */
public class TestUtil {

  private static final String USE_PREFERRED_NATIVE_FOLDER_KEY = "use." + PtyUtil.PREFERRED_NATIVE_FOLDER_KEY;

  public static @NotNull String getTestDataPath() {
    return Paths.get("test/testData").toAbsolutePath().normalize().toString();
  }

  public static @NotNull String getTestDataFilePath(@NotNull String relativePath) {
    return new File(TestUtil.getTestDataPath(), relativePath).getAbsolutePath();
  }

  @NotNull
  public static String[] getJavaCommand(@NotNull Class<?> aClass, String... args) {
    List<String> result = new ArrayList<>();
    result.add(getJavaExecutablePath());
    result.add("-cp");
    result.add(getJarPathForClasses(aClass, WinSize.class, Logger.class, JTermios.class,
                                    Platform.class, Kernel32.class,
                                    KotlinVersion.class /* kotlin-stdlib.jar */));
    result.add(aClass.getName());
    result.addAll(Arrays.asList(args));
    return result.toArray(new String[0]);
  }

  @NotNull
  private static String getJavaExecutablePath() {
    return System.getProperty("java.home") + File.separator + "bin"
        + File.separator + (Platform.isWindows() ? "java.exe" : "java");
  }

  /**
   * Copied from com.intellij.openapi.application.PathManager#getJarPathForClass.
   */
  @NotNull
  private static String getJarPathForClass(@NotNull Class<?> aClass) {
    String resourceRoot = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    return new File(Objects.requireNonNull(resourceRoot)).getAbsolutePath();
  }

  private static String getJarPathForClasses(@NotNull Class<?>... classes) {
    List<String> paths = Arrays.stream(classes).map(TestUtil::getJarPathForClass).collect(Collectors.toList());
    return String.join(Platform.isWindows() ? ";" : ":", paths);
  }

  @Nullable
  private static String getResourceRoot(@NotNull Class<?> context, @NotNull String path) {
    URL url = context.getResource(path);
    if (url == null) {
      url = ClassLoader.getSystemResource(path.substring(1));
    }
    return url != null ? extractRoot(url, path) : null;
  }

  @NotNull
  private static String extractRoot(@NotNull URL resourceURL, @NotNull String resourcePath) {
    if (!resourcePath.startsWith("/") && !resourcePath.startsWith("\\")) {
      throw new IllegalStateException("precondition failed: " + resourcePath);
    }

    String resultPath = null;
    String protocol = resourceURL.getProtocol();
    if ("file".equals(protocol)) {
      String path = urlToFile(resourceURL).getPath();
      String testPath = path.replace('\\', '/');
      String testResourcePath = resourcePath.replace('\\', '/');
      if (testPath.endsWith(testResourcePath)) {
        resultPath = path.substring(0, path.length() - resourcePath.length());
      }
    }
    else if ("jar".equals(protocol)) {
      resultPath = getJarPath(resourceURL.getFile());
    }

    if (resultPath == null) {
      throw new IllegalStateException("Cannot extract '" + resourcePath + "' from '" + resourceURL + "', " + protocol);
    }
    return resultPath;
  }

  @NotNull
  private static File urlToFile(@NotNull URL url) {
    try {
      return new File(url.toURI().getSchemeSpecificPart());
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException("URL='" + url + "'", e);
    }
  }

  private static @Nullable String getJarPath(@NotNull String urlFilePart) {
    int pivot = urlFilePart.indexOf("!/");
    if (pivot < 0) {
      return null;
    }
    String fileUrlStr = urlFilePart.substring(0, pivot);

    String filePrefix = "file:";
    if (!fileUrlStr.startsWith(filePrefix)) {
      return fileUrlStr;
    }

    URL fileUrl;
    try {
      fileUrl = new URL(fileUrlStr);
    }
    catch (MalformedURLException e) {
      return null;
    }

    File result = urlToFile(fileUrl);
    return result.getPath().replace('\\', '/');
  }

  @NotNull
  public static Path getBuiltNativeFolder() {
    return Paths.get("os").toAbsolutePath().normalize();
  }

  public static void setLocalPtyLib() {
    if ("false".equals(System.getProperty(USE_PREFERRED_NATIVE_FOLDER_KEY))) {
      System.clearProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY);
    }
    else {
      System.setProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY, getBuiltNativeFolder().toString());
    }
  }

  public static void useLocalNativeLib(boolean enable) {
    if (enable) {
      System.setProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY, getBuiltNativeFolder().toString());
    }
    else {
      System.clearProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY);
    }
  }

  public static void assertConsoleExists() {
    if (System.console() == null) {
      System.err.println("Not a terminal");
      System.exit(1);
    }
  }

  public static int getTestWaitTimeoutSeconds() {
    String valueStr = System.getenv("PTY4J_TEST_TIMEOUT_SECONDS");
    if (valueStr != null) {
      try {
        int value = Integer.parseInt(valueStr);
        if (value > 0) {
          System.out.println("pty4j test timeout is set to " + value + " seconds");
          return value;
        }
      }
      catch (NumberFormatException ignored) {
      }
    }
    if (System.getenv("CI") != null || System.getenv("TEAMCITY_VERSION") != null) {
      return 60;
    }
    return 5;
  }

  public static @Nullable File findInPath(@NotNull String fileName) {
    String pathValue = System.getenv("PATH");
    if (pathValue == null) {
      return null;
    }
    List<String> dirPaths = getPathDirs(pathValue);
    for (String dirPath : dirPaths) {
      File dir = new File(dirPath);
      if (dir.isAbsolute() && dir.isDirectory()) {
        File exeFile = new File(dir, fileName);
        if (exeFile.isFile() && exeFile.canExecute()) {
          return exeFile;
        }
      }
    }
    return null;
  }

  private static @NotNull List<String> getPathDirs(@NotNull String pathEnvVarValue) {
    return Arrays.asList(pathEnvVarValue.split(Pattern.quote(File.pathSeparator)));
  }
}

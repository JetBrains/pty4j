package com.pty4j;

import com.pty4j.unix.PtyHelpers;
import com.pty4j.util.PtyUtil;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author traff
 */
public class TestUtil {
  @NotNull
  public static String getTestDataPath() {
    return Paths.get("test/testData").toAbsolutePath().normalize().toString();
  }

  @NotNull
  public static String[] getJavaCommand(@NotNull Class<?> aClass, String... args) {
    List<String> result = new ArrayList<>();
    result.add(getJavaExecutablePath());
    result.add("-cp");
    result.add(getJarPathForClass(aClass));
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
  private static String getJarPathForClass(@NotNull Class aClass) {
    String resourceRoot = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    return new File(Objects.requireNonNull(resourceRoot)).getAbsolutePath();
  }

  @Nullable
  private static String getResourceRoot(@NotNull Class context, @NotNull String path) {
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
      throw new IllegalArgumentException("URL='" + url.toString() + "'", e);
    }
  }

  @NotNull
  public static Path getBuiltNativeFolder() {
    return Paths.get("os").toAbsolutePath().normalize();
  }

  public static void setLocalPtyLib() {
    if (System.getProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY) == null) {
      System.setProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY, getBuiltNativeFolder().toString());
    }
  }

  public static void unsetLocalPtyLib() {
    System.clearProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY);
    PtyHelpers.dropPtyExecutor();
  }
}

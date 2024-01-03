package com.pty4j.util;

import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * @author traff
 */
public class PtyUtil {

  public static final String PREFERRED_NATIVE_FOLDER_KEY = "pty4j.preferred.native.folder";

  public static String[] toStringArray(Map<String, String> environment) {
    if (environment == null) return new String[0];
    return environment.entrySet().stream()
      .map(entry -> entry.getKey() + "=" + entry.getValue())
      .toArray(String[]::new);
  }

  private static @Nullable File getPreferredLibPtyFolder() {
    String path = System.getProperty(PREFERRED_NATIVE_FOLDER_KEY);
    File dir = path != null && !path.isEmpty() ? new File(path) : null;
    if (dir != null && dir.isDirectory()) {
      return dir.getAbsoluteFile();
    }
    return null;
  }

  public static @NotNull File resolveNativeFile(@NotNull String fileName) throws IllegalStateException {
    File preferredLibPtyFolder = getPreferredLibPtyFolder();
    if (preferredLibPtyFolder != null) {
      return resolveNativeFileFromFS(preferredLibPtyFolder, fileName);
    }
    File destDir = ExtractedNative.getInstance().getDestDir();
    return new File(destDir, fileName);
  }

  private static @NotNull File resolveNativeFileFromFS(@NotNull File libPtyFolder, @NotNull String fileName) {
    String nativeLibraryResourcePath = getNativeLibraryOsArchSubPath();
    return new File(new File(libPtyFolder, nativeLibraryResourcePath), fileName);
  }

  static @NotNull String getNativeLibraryOsArchSubPath() {
    int osType = Platform.getOSType();
    String arch = Platform.ARCH;
    if (osType == Platform.WINDOWS) {
      return "win/" + arch;
    }
    if (osType == Platform.MAC) {
      return "darwin";
    }
    if (osType == Platform.LINUX) {
      return "linux/" + arch;
    }
    if (osType == Platform.FREEBSD) {
      return "freebsd/" + arch;
    }
    throw new IllegalStateException("Pty4J has no native support for " +
      "OS name: " + System.getProperty("os.name") + " (JNA OS type: " + Platform.getOSType() + ")" +
      ", arch: " + System.getProperty("os.arch") + " (JNA arch: " + Platform.ARCH + ")");
  }
}

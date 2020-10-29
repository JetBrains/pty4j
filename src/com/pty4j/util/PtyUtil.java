package com.pty4j.util;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.pty4j.windows.WinPty;
import com.sun.jna.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class PtyUtil {
  public static final String OS_VERSION = System.getProperty("os.version").toLowerCase();

  private final static String PTY_LIB_FOLDER = System.getenv("PTY_LIB_FOLDER");
  public static final String PREFERRED_NATIVE_FOLDER_KEY = "pty4j.preferred.native.folder";

  public static String[] toStringArray(Map<String, String> environment) {
    if (environment == null) return new String[0];
    List<String> list = Lists.transform(Lists.newArrayList(environment.entrySet()), new Function<Map.Entry<String, String>, String>() {
      public String apply(Map.Entry<String, String> entry) {
        return entry.getKey() + "=" + entry.getValue();
      }
    });
    return list.toArray(new String[list.size()]);
  }

  /**
   * Returns the folder that contains a jar that contains the class
   *
   * @param aclass a class to find a jar
   * @return
   */
  public static String getJarContainingFolderPath(Class aclass) throws Exception {
    CodeSource codeSource = aclass.getProtectionDomain().getCodeSource();

    File jarFile;

    if (codeSource.getLocation() != null) {
      jarFile = new File(codeSource.getLocation().toURI());
    } else {
      String path = aclass.getResource(aclass.getSimpleName() + ".class").getPath();

      int startIndex = path.indexOf(":") + 1;
      int endIndex = path.indexOf("!");
      if (startIndex == -1 || endIndex == -1) {
        throw new IllegalStateException("Class " + aclass.getSimpleName() + " is located not within a jar: " + path);
      }
      String jarFilePath = path.substring(startIndex, endIndex);
      jarFilePath = new URI(jarFilePath).getPath();
      jarFile = new File(jarFilePath);
    }
    return jarFile.getParentFile().getAbsolutePath();
  }

  /**
   * @deprecated to be removed in future releases
   */
  @Deprecated
  public static String getPtyLibFolderPath() throws Exception {
    File file = getPreferredLibPtyFolder();
    return file != null ? file.getAbsolutePath() : null;
  }

  @Nullable
  private static File getPreferredLibPtyFolder() {
    if (PTY_LIB_FOLDER != null) {
      System.err.println("WARN: PTY_LIB_FOLDER environment variable is deprecated and" +
          " will not be used in future releases." +
          " Please set Java system property \"" + PREFERRED_NATIVE_FOLDER_KEY + "\" instead.");
    }
    String path = PTY_LIB_FOLDER != null ? PTY_LIB_FOLDER : System.getProperty(PREFERRED_NATIVE_FOLDER_KEY);
    if (path != null) {
      File dir = new File(path);
      if (dir.isAbsolute() && dir.isDirectory()) {
        return dir;
      }
    }
    return null;
  }

  @NotNull
  public static File resolveNativeLibrary() throws Exception {
    return resolveNativeFile(getNativeLibraryName());
  }

  /**
   * @deprecated to be removed in future releases
   */
  @Deprecated
  public static File resolveNativeLibrary(File parent) {
    return resolveNativeFileFromFS(parent, getNativeLibraryName());
  }

  @NotNull
  public static File resolveNativeFile(@NotNull String fileName) throws Exception {
    File preferredLibPtyFolder = getPreferredLibPtyFolder();
    if (preferredLibPtyFolder != null) {
      return resolveNativeFileFromFS(preferredLibPtyFolder, fileName);
    }
    Exception extractException;
    try {
      File destDir = ExtractedNative.getInstance().getDestDir();
      return new File(destDir, fileName);
    }
    catch (Exception e) {
      extractException = e;
    }
    File jarParentFolder = new File(getJarContainingFolderPath(WinPty.class));
    File file = resolveNativeFileFromFS(jarParentFolder, fileName);
    if (!file.exists()) {
      file = resolveNativeFileFromFS(new File(jarParentFolder, "libpty"), fileName);
    }
    if (file.exists()) {
      return file;
    }
    throw extractException;
  }

  @NotNull
  private static File resolveNativeFileFromFS(@NotNull File libPtyFolder, @NotNull String fileName) {
    final File platformFolder = new File(libPtyFolder, getPlatformFolderName());
    String prefix = getPlatformArchFolderName();
    if (new File(libPtyFolder, prefix).exists()) {
      return new File(new File(libPtyFolder, prefix), fileName);
    } else {
      return new File(new File(platformFolder, prefix), fileName);
    }
  }

  /**
   * @deprecated to be removed in future releases
   */
  @Deprecated
  public static File resolveNativeFile(File libPtyFolder, String fileName) {
    return resolveNativeFileFromFS(libPtyFolder, fileName);
  }

  @NotNull
  static String getPlatformArchFolderName() {
    // Handle special cases (xp, ppc64le, aarch64, mips64el)
    if (isWinXp())
      return "xp";
    if (System.getProperty("os.arch").equals("ppc64le"))
      return "ppc64le";
    if (System.getProperty("os.arch").equals("aarch64"))
      return "aarch64";
    if (System.getProperty("os.arch").equals("mips64el"))
      return "mips64el";

    // Special cases handled, assume x86
    return Platform.is64Bit() ? "x86_64" : "x86";
  }

  @NotNull
  static String getPlatformFolderName() {
    String result;

    if (Platform.isMac()) {
      result = "macosx";
    } else if (Platform.isWindows()) {
      result = "win";
    } else if (Platform.isLinux() || Platform.isAndroid()) {
      result = "linux";
    } else if (Platform.isFreeBSD()) {
      result = "freebsd";
    } else if (Platform.isOpenBSD()) {
      result = "openbsd";
    } else {
      throw new IllegalStateException("Platform " + Platform.getOSType() + " is not supported");
    }

    return result;
  }

  private static String getNativeLibraryName() {
    String result;

    if (Platform.isMac()) {
      result = "libpty.dylib";
    } else if (Platform.isWindows()) {
      result = "winpty.dll";
    } else if (Platform.isLinux() || Platform.isFreeBSD() || Platform.isOpenBSD() || Platform.isAndroid()) {
      result = "libpty.so";
    } else {
      throw new IllegalStateException("Platform " + Platform.getOSType() + " is not supported");
    }

    return result;
  }

  private static boolean isWinXp() {
    return Platform.isWindows() && (OS_VERSION.equals("5.1") || OS_VERSION.equals("5.2"));
  }
}

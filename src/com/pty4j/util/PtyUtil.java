package com.pty4j.util;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.pty4j.windows.WinPty;
import com.sun.jna.Platform;

import java.io.File;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class PtyUtil {
  public static String[] toStringArray(Map<String, String> environment) {
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
  public static String getJarContainingFolder(Class aclass) throws Exception {
    CodeSource codeSource = aclass.getProtectionDomain().getCodeSource();

    File jarFile;

    if (codeSource.getLocation() != null) {
      jarFile = new File(codeSource.getLocation().toURI());
    }
    else {
      String path = aclass.getResource(aclass.getSimpleName() + ".class").getPath();
      String jarFilePath = path.substring(path.indexOf(":") + 1, path.indexOf("!"));
      jarFilePath = URLDecoder.decode(jarFilePath, "UTF-8");
      jarFile = new File(jarFilePath);
    }
    return jarFile.getParentFile().getAbsolutePath();
  }


  public static String getJarFolder() throws Exception {
    //Class aclass = WinPty.class.getClassLoader().loadClass("com.jediterm.pty.PtyMain");
    Class aclass = WinPty.class;

    return getJarContainingFolder(aclass);
  }

  public static File resolveNativeLibrary() throws Exception {
    File jarFolder = new File(getJarFolder());
    File lib = resolveNativeLibrary(jarFolder);

    lib = lib.exists() ? lib : resolveNativeLibrary(new File(jarFolder, "libpty"));

    if (!lib.exists()) {
      throw new IllegalStateException(String.format("Couldn't find %s, jar folder %s", lib.getName(),
        jarFolder.getAbsolutePath()));
    }

    return lib;
  }

  public static File resolveNativeLibrary(File parent) {
    File path = new File(parent, getPlatformFolder());

    path = Platform.is64Bit() ? new File(path, "x86_64") : new File(path, "x86");

    return new File(path, getNativeLibraryName());
  }

  private static String getPlatformFolder() {
    String result;

    if (Platform.isMac()) {
      result = "macosx";
    } else if (Platform.isWindows()) {
      result = "win";
    } else if (Platform.isLinux()) {
      result = "linux";
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
      result = "libwinpty.dll";
    } else if (Platform.isLinux()) {
      result = "libpty.so";
    } else {
      throw new IllegalStateException("Platform " + Platform.getOSType() + " is not supported");
    }

    return result;
  }
}

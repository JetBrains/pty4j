package com.pty4j.util;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.pty4j.windows.WinPty;

import java.io.File;
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
  public static String getJarContainingFolder(Class aclass) {
    try {
      CodeSource codeSource = aclass.getProtectionDomain().getCodeSource();

      File jarFile;

      if (codeSource.getLocation() != null) {
        jarFile = new File(codeSource.getLocation().toURI());
      }
      else {
        String path = aclass.getResource(aclass.getSimpleName() + ".class").getPath();
        jarFile = new File(path.substring(path.indexOf(":") + 1, path.indexOf("!")));
      }
      return jarFile.getParentFile().getAbsolutePath();
    }
    catch (Exception e) {
      return null;
    }
  }


  public static String getJarFolder() {
    //Class aclass = WinPty.class.getClassLoader().loadClass("com.jediterm.pty.PtyMain");
    Class aclass = WinPty.class;

    return getJarContainingFolder(aclass);
  }
}

package com.pty4j.util;

/**
 * @author traff
 */
public class Util {
  public static String join(String[] array, String delimiter) {
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (String s : array) {
      if (first) {
        first = false;
      } else {
        builder.append(delimiter);
      }
      builder.append(s);
    }
    return builder.toString();
  }
}

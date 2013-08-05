package com.pty4j.util;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

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

  public static String[] toStringArray(Map<String, String> environment) {
    List<String> list = Lists.transform(Lists.newArrayList(environment.entrySet()), new Function<Map.Entry<String, String>, String>() {
      public String apply(Map.Entry<String, String> entry) {
        return entry.getKey() + "=" + entry.getValue();
      }
    });
    return list.toArray(new String[list.size()]);
  }
}

package testData;

import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConsoleSizeReporter {
  public static void main(String[] args) throws IOException {
    WinSize size = getSize();
    System.out.println("Initial columns: " + size.ws_col + ", initial rows: " + size.ws_row);
  }

  private static @NotNull WinSize getSize() throws IOException {
    Process process = new ProcessBuilder("/bin/bash", "-i", "-c", "echo columns:$COLUMNS, rows:$LINES")
      .inheritIO()
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .start();
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    String line = reader.readLine();
    WinSize winSize = parseShOutput(line);
    if (winSize == null) {
      throw new IllegalStateException("Cannot parse column,row from " + line);
    }
    return winSize;
  }

  private static @Nullable WinSize parseShOutput(@Nullable String line) {
    if (line == null) return null;
    Matcher matcher = Pattern.compile("columns:(\\d+), rows:(\\d+)").matcher(line);
    if (!matcher.matches()) {
      return null;
    }
    Integer columns = parseInt(matcher.group(1));
    Integer rows = parseInt(matcher.group(2));
    if (columns != null && rows != null) {
      return new WinSize(columns, rows);
    }
    return null;
  }

  private static @Nullable Integer parseInt(@Nullable String s) {
    if (s == null) return null;
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}

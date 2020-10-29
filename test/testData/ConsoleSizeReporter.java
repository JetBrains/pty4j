package testData;

import com.pty4j.TestUtil;
import com.pty4j.WinSize;
import com.pty4j.unix.PtyHelpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConsoleSizeReporter {

  public static final String PRINT_SIZE = "print_size";
  public static final String EXIT = "exit";

  public static void main(String[] args) throws IOException {
    TestUtil.setLocalPtyLib();
    printSize();
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String line;
    while ((line = reader.readLine()) != null) {
      if (line.equals(PRINT_SIZE)) {
        printSize();
      }
      else if (line.equals(EXIT)) {
        break;
      }
    }
  }

  private static void printSize() throws IOException {
    WinSize size = PtyHelpers.getWinSize(0);
    System.out.println("columns: " + size.getColumns() + ", rows: " + size.getRows());
  }
}

package testData;

import com.pty4j.TestUtil;
import com.pty4j.WinSize;
import com.pty4j.unix.PtyHelpers;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Wincon;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConsoleSizeReporter {

  public static final String PRINT_SIZE = "print_size";
  public static final String EXIT = "exit";

  public static void main(String[] args) throws IOException {
    TestUtil.assertConsoleExists();
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
    WinSize windowSize = getWindowSize();
    System.out.println("columns: " + windowSize.getColumns() + ", rows: " + windowSize.getRows());
  }

  private static @NotNull WinSize getWindowSize() throws IOException {
    if (Platform.isWindows()) {
      WinNT.HANDLE handle = Kernel32.INSTANCE.GetStdHandle(Kernel32.INSTANCE.STD_OUTPUT_HANDLE);
      Wincon.CONSOLE_SCREEN_BUFFER_INFO buffer = new Wincon.CONSOLE_SCREEN_BUFFER_INFO();
      if (!Kernel32.INSTANCE.GetConsoleScreenBufferInfo(handle, buffer)) {
        throw new IOException("GetConsoleScreenBufferInfo failed");
      }
      Wincon.SMALL_RECT window = buffer.srWindow;
      return new WinSize(window.Right - window.Left + 1, window.Bottom - window.Top + 1);
    }
    else {
      return PtyHelpers.getWinSize(0);
    }
  }
}

package testData;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Wincon;
import com.sun.jna.ptr.IntByReference;

import java.io.IOException;

/** Reports the console API cursor after an APC whose cursor effects only the terminal knows. */
public class GraphicsCursorReporter {
  public static final String GRAPHICS = "\u001b_Ga=T,f=24,s=1,v=1;AAAA\u001b\\";
  public static final String REPORT_PREFIX = "\u001b]0;cursor=";

  public static void main(String[] args) throws IOException {
    Kernel32 kernel = Kernel32.INSTANCE;
    WinNT.HANDLE output = kernel.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
    IntByReference mode = new IntByReference();
    if (!kernel.GetConsoleMode(output, mode) ||
        !kernel.SetConsoleMode(output, mode.getValue() | Wincon.ENABLE_VIRTUAL_TERMINAL_PROCESSING)) {
      throw new IOException("Cannot enable virtual terminal processing");
    }
    System.out.print(GRAPHICS);
    System.out.flush();

    Wincon.CONSOLE_SCREEN_BUFFER_INFO info = new Wincon.CONSOLE_SCREEN_BUFFER_INFO();
    if (!kernel.GetConsoleScreenBufferInfo(output, info)) {
      throw new IOException("GetConsoleScreenBufferInfo failed");
    }
    System.out.print(REPORT_PREFIX + info.dwCursorPosition.X + "," + info.dwCursorPosition.Y + "\u0007");
    System.out.flush();
    // Keep the child alive until the parent has consumed and checked the report.
    if (System.in.read() != 'x') throw new IOException("Missing completion acknowledgement");
  }
}

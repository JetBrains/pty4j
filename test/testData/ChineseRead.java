package testData;

import com.sun.jna.platform.win32.Kernel32;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class ChineseRead {
  public static final String PREFIX = "my_prefix";
  public static final String SUFFIX = "my_suffix";
  public static final String STDOUT = PREFIX + "测试" + SUFFIX;

  public static void main(String[] args) throws IOException {
    System.out.println("Console codepage:" + Kernel32.INSTANCE.GetConsoleOutputCP());
    Charset charset = Charset.defaultCharset();
    System.out.println("Default charset: " + charset);
    byte[] bytes = STDOUT.getBytes(charset);
    System.out.write(bytes);
    OutputStream outputStream = new FileOutputStream("D:\\projects\\pty4j\\test\\testData\\ChineseRead.txt");
    try (outputStream) {
      outputStream.write(bytes);
    }
  }
}

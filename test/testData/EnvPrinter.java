package testData;

import com.pty4j.TestUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class EnvPrinter {
  public static void main(String[] args) throws IOException {
    TestUtil.assertConsoleExists();
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("Enter env name:");
    String envName = reader.readLine();
    while (envName != null && !envName.isEmpty()) {
      System.out.println("Env " + envName + "=" + System.getenv(envName));
      System.out.print("Enter env name:");
      envName = reader.readLine();
    }
    System.out.println(envName == null ? "exit: stdin closed" : "exit: empty env name");
  }
}

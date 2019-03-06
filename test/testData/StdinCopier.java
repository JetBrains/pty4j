package testData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class StdinCopier {
  public static void main(String[] args) throws IOException {
    System.out.print("Enter: ");
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String x = reader.readLine();
    System.out.println(x);
  }
}

package testData;

public class Printer {
  public static final String STDOUT = "abcdefz";
  public static final String STDERR = "ABCDEFZ";
  public static void main(String[] args) {
    System.out.println(STDOUT);
    System.err.println(STDERR);
  }
}

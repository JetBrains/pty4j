package testData;

import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.IOException;

public class UndestroyablePromptReader {
  public static void main(String[] args) throws IOException {
    Signal.handle(new Signal("TERM"), SignalHandler.SIG_IGN);
    PromptReader.startPromptHandling();
  }
}

package com.pty4j.windows;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.PtyTest;
import com.pty4j.TestUtil;
import com.pty4j.windows.conpty.WinConPtyProcess;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import testData.EnvPrinter;
import testData.Printer;
import testData.PromptReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WinConPtyProcessTest {

  private @NotNull PtyProcessBuilder builder() {
    return new PtyProcessBuilder() {
      @Override
      public @NotNull PtyProcess start() throws IOException {
        PtyProcess process = super.start();
        Assert.assertTrue(process instanceof WinConPtyProcess);
        return process;
      }
    }.setUseWinConPty(true).setConsole(false);
  }

  @Test
  public void testBasic() throws Exception {
    PtyProcess process = builder().setCommand(TestUtil.getJavaCommand(Printer.class)).start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.awaitFinish();
    stderr.awaitFinish();
    stdout.assertEndsWith(Printer.STDOUT + "\r\n" + Printer.STDERR + "\r\n");
    Assert.assertEquals("", stderr.getOutput());
    PtyTest.assertProcessTerminatedNormally(process);
  }

  @Test
  public void testPromptReader() throws Exception {
    PtyProcessBuilder builder = builder().setCommand(TestUtil.getJavaCommand(PromptReader.class));
    PtyProcess process = builder.start();

    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith("Enter:");
    PtyTest.writeToStdinAndFlush(process, "Hi", true);
    stdout.assertEndsWith("Enter:Hi\r\nRead:Hi\r\nEnter:");

    PtyTest.writeToStdinAndFlush(process, "", true);

    stdout.assertEndsWith("Enter:\r\nexit: empty line\r\n");

    stdout.awaitFinish();
    stderr.awaitFinish();
    Assert.assertEquals("", stderr.getOutput());

    PtyTest.assertProcessTerminatedNormally(process);
  }

  @Test
  public void testPassingEnv() throws Exception {
    PtyProcess process = builder().setCommand(TestUtil.getJavaCommand(EnvPrinter.class))
            .setEnvironment(mergeCustomAndSystemEnvironment(Map.of("foo", "bar", "HELLO", "WORLD")))
            .start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith("Enter env name:");
    PtyTest.writeToStdinAndFlush(process, "foo", true);
    stdout.assertEndsWith("Enter env name:foo\r\n" +
                          "Env foo=bar\r\n" +
                          "Enter env name:");

    PtyTest.writeToStdinAndFlush(process, "HELLO", true);
    stdout.assertEndsWith("Enter env name:HELLO\r\n" +
                          "Env HELLO=WORLD\r\n" +
                          "Enter env name:");

    PtyTest.writeToStdinAndFlush(process, "", true);

    stdout.assertEndsWith("Enter env name:\r\n" +
                          "exit: empty env name\r\n");

    stdout.awaitFinish();
    stderr.awaitFinish();
    Assert.assertEquals("", stderr.getOutput());

    PtyTest.assertProcessTerminatedNormally(process);
  }

  private static @NotNull Map<String, String> mergeCustomAndSystemEnvironment(@NotNull Map<String, String> customEnv) {
    Map<String, String> env = new HashMap<>(System.getenv());
    env.putAll(customEnv);
    return env;
  }
}

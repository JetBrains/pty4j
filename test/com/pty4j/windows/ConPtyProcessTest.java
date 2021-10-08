package com.pty4j.windows;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.PtyTest;
import com.pty4j.TestUtil;
import com.pty4j.windows.conpty.ConPtyProcess;
import com.sun.jna.Platform;
import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Assume;
import testData.Printer;

import java.io.IOException;

public class ConPtyProcessTest extends TestCase {

  @Override
  public void setUp() {
    System.setProperty(PtyProcessBuilder.WINDOWS_CON_PTY_SYSTEM_PROPERTY, "true");
    Assume.assumeTrue(Platform.isWindows());
  }

  @Override
  protected void tearDown() throws Exception {
    System.setProperty(PtyProcessBuilder.WINDOWS_CON_PTY_SYSTEM_PROPERTY, "false");
    super.tearDown();
  }

  public void _testProcessBuilder() throws IOException, InterruptedException {
    String[] cmd = TestUtil.getJavaCommand(Printer.class);
    PtyProcessBuilder builder = new PtyProcessBuilder(cmd);
    PtyProcess process = builder.start();
    Assert.assertTrue(process instanceof ConPtyProcess);
    PtyTest.assertProcessTerminatedNormally(process);
  }

  public void _testHelloWorld() throws IOException, InterruptedException {
    String[] cmd = TestUtil.getJavaCommand(Printer.class);
    PtyProcessBuilder builder = new PtyProcessBuilder(cmd);
    PtyProcess process = builder.start();
    PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
    PtyTest.Gobbler stderr = PtyTest.startStderrGobbler(process);
    stdout.assertEndsWith(Printer.STDOUT);
    stderr.assertEndsWith(Printer.STDERR);
    PtyTest.assertProcessTerminatedNormally(process);
  }
}

package com.pty4j.windows;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.PtyTest;
import com.pty4j.TestUtil;
import com.pty4j.WinSize;
import com.pty4j.windows.conpty.WinConPtyProcess;
import com.pty4j.windows.winpty.WinPtyProcess;
import com.sun.jna.Platform;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import testData.ConsoleSizeReporter;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

/** Both Windows backends expose the character grid without retaining unsupported pixel metadata. */
@RunWith(Parameterized.class)
public class WindowsWindowSizeTest {
  private final boolean useConPty;

  @Parameterized.Parameters(name = "ConPTY={0}")
  public static Collection<Object[]> backends() {
    return Arrays.asList(new Object[][]{{true}, {false}});
  }

  public WindowsWindowSizeTest(boolean useConPty) {
    this.useConPty = useConPty;
  }

  @Before
  public void setUp() {
    Assume.assumeTrue(Platform.isWindows());
    TestUtil.setLocalPtyLib();
    if (useConPty) Assume.assumeTrue(WinConPtyProcess.isBundledConPtyLibraryLoaded());
  }

  @After
  public void tearDown() {
    TestUtil.useLocalNativeLib(false);
  }

  @Test
  public void testPixelDimensionsAreAlwaysUnknown() throws Exception {
    PtyProcess process = new PtyProcessBuilder().setCommand(TestUtil.getJavaCommand(ConsoleSizeReporter.class))
      .setUseWinConPty(useConPty).setConsole(false).setInitialColumns(111).setInitialRows(11).start();
    try {
      assertEquals(useConPty ? WinConPtyProcess.class : WinPtyProcess.class, process.getClass());
      PtyTest.Gobbler stdout = PtyTest.startStdoutGobbler(process);
      stdout.assertEndsWith("columns: 111, rows: 11\r\n");
      assertEquals(new WinSize(111, 11), process.getWinSize());

      WinSize[] requests = {
        new WinSize(111, 11, 1280, 720),
        new WinSize(140, 25, 65535, 32768),
        new WinSize(140, 25, 0, 720),
        new WinSize(140, 25)
      };
      for (WinSize request : requests) {
        process.setWinSize(request);
        assertEquals(new WinSize(request.getColumns(), request.getRows()), process.getWinSize());
      }

      PtyTest.writeToStdinAndFlush(process, ConsoleSizeReporter.PRINT_SIZE, true);
      stdout.assertEndsWith("columns: 140, rows: 25\r\n");
      PtyTest.writeToStdinAndFlush(process, ConsoleSizeReporter.EXIT, true);
      stdout.assertEndsWith("Exiting\r\n");
      stdout.awaitFinish();
      PtyTest.assertProcessTerminatedNormally(process);
    }
    finally {
      process.destroy();
    }
  }
}

package com.pty4j;

import com.pty4j.util.PtyUtil;
import org.junit.Assume;
import org.junit.Test;
import testData.RepeatTextWithTimeout;

import java.io.IOException;

public class FailedToLoadNativePtyLibTest {

  @Test(expected = IOException.class)
  public void testErrorMessage() throws IOException {
    if ("false".equals(System.getProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY))) {
      // When tests are running against pty4j.jar, native will be loaded from jar anyway.
      // Skip the test in this case.
      Assume.assumeTrue(false);
    }
    TestUtil.unsetLocalPtyLib();
    String[] cmd = TestUtil.getJavaCommand(RepeatTextWithTimeout.class, "2", "1000", "Hello, World");
    PtyProcess.exec(cmd);
  }
}

package com.pty4j.windows;

import com.pty4j.windows.WinPtyProcess;
import junit.framework.TestCase;

/**
 * @author traff
 */
public class WinPtyProcessTest extends TestCase {
    public void testCmdLineWithSpaces() {
        assertEquals("C:\\Python35\\python.exe \"C:\\Program Files (x86)\\JetBrains\\PyCharm 5.0.2\\helpers\\pydev\\pydevd.py\"", WinPtyProcess.joinCmdArgs(new String[]{"C:\\Python35\\python.exe", "C:\\Program Files (x86)\\JetBrains\\PyCharm 5.0.2\\helpers\\pydev\\pydevd.py"}));
    }
}

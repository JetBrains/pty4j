package com.pty4j.util;

import com.pty4j.TestUtil;
import junit.framework.TestCase;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test cases for {@link com.pty4j.util.PtyUtil}.
 */
public class PtyUtilTest extends TestCase {

    public void testNativeFolderRelativePath() {
        Path nativeFolderRelPath = Paths.get("").toAbsolutePath().relativize(TestUtil.getBuiltNativeFolder());
        System.setProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY, nativeFolderRelPath.toString());
        try {
            assertTrue(PtyUtil.resolveNativeLibrary().exists());
        } finally {
            System.clearProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY);
        }
    }
}

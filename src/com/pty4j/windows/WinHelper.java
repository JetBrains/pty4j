package com.pty4j.windows;

import com.pty4j.util.PtyUtil;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.PointerByReference;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class WinHelper {

  public static @NotNull String getCurrentDirectory(long processId) throws IOException {
    if (!Platform.isWindows()) {
      throw new IOException("Should be called on Windows OS only");
    }
    PointerByReference errorMessageRef = new PointerByReference();
    Pointer currentDirectory = Holder.INSTANCE.getCurrentDirectory(new WinDef.DWORD(processId), errorMessageRef);
    Pointer errorMessagePtr = errorMessageRef.getValue();
    if (currentDirectory != null) {
      if (errorMessagePtr != null) {
        throw new IOException("Unexpected error message: " + getStringAndFree(errorMessagePtr));
      }
      return getStringAndFree(currentDirectory);
    }
    if (errorMessagePtr == null) {
      throw new IOException("getCurrentDirectory failed without error message");
    }
    throw new IOException("getCurrentDirectory failed: " + getStringAndFree(errorMessagePtr));
  }

  private static @NotNull String getStringAndFree(@NotNull Pointer stringPtr) {
    String result = stringPtr.getWideString(0);
    Native.free(Pointer.nativeValue(stringPtr));
    return result;
  }

  private interface WinHelperNativeLibrary extends Library {
    Pointer getCurrentDirectory(WinDef.DWORD pid, PointerByReference errorMessagePtr);
  }

  private static class Holder {
    private static final WinHelperNativeLibrary INSTANCE = Native.load(
        PtyUtil.resolveNativeFile("win-helper.dll").getAbsolutePath(),
        WinHelperNativeLibrary.class
    );
  }
}

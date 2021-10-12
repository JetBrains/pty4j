package com.pty4j.windows.conpty;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

public class WinHandleOutputStream extends OutputStream {
  private final WinNT.HANDLE myWritePipe;
  private volatile boolean myClosed;

  public WinHandleOutputStream(@NotNull WinNT.HANDLE writePipe) {
    myWritePipe = writePipe;
  }

  @Override
  public void write(int b) throws IOException {
    write(new byte[]{(byte) b}, 0, 1);
  }

  @Override
  public void write(byte @NotNull [] b, int off, int len) throws IOException {
    Objects.checkFromIndexSize(off, len, b.length);
    if (len == 0) {
      return;
    }
    if (myClosed) {
      throw new IOException("Closed stdout");
    }
    byte[] buffer = Arrays.copyOfRange(b, off, off + len);
    IntByReference lpNumberOfBytesWritten = new IntByReference(0);
    if (!Kernel32.INSTANCE.WriteFile(myWritePipe, buffer, buffer.length, lpNumberOfBytesWritten, null)) {
      throw new LastErrorExceptionEx("WriteFile stdout");
    }
  }

  @Override
  public void close() throws IOException {
    if (!myClosed) {
      myClosed = true;
      if (!Kernel32.INSTANCE.CloseHandle(myWritePipe)) {
        throw new LastErrorExceptionEx("CloseHandle stdout");
      }
    }
  }
}

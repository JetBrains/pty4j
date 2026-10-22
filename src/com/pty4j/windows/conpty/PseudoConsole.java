package com.pty4j.windows.conpty;

import com.pty4j.WinSize;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class PseudoConsole {

  @SuppressWarnings("SpellCheckingInspection")
  private static final WinDef.DWORD PSEUDOCONSOLE_INHERIT_CURSOR = new WinDef.DWORD(1);

  private final WinEx.HPCON hpc;
  // This backend accepts only a character grid; its cached size has no pixel metadata.
  private WinSize myLastWinSize;
  private boolean myClosed = false;

  private static WinEx.COORDByValue getSizeCoords(int columns, int rows) {
    WinEx.COORDByValue sizeCoords = new WinEx.COORDByValue();
    sizeCoords.X = (short) columns;
    sizeCoords.Y = (short) rows;
    return sizeCoords;
  }

  public PseudoConsole(int columns, int rows, WinNT.HANDLE input, WinNT.HANDLE output,
                       boolean conPtyInheritCursor) throws LastErrorExceptionEx {
    WinEx.HPCONByReference hpcByReference = new WinEx.HPCONByReference();
    WinDef.DWORD flags = conPtyInheritCursor ? PSEUDOCONSOLE_INHERIT_CURSOR : new WinDef.DWORD(0);
    if (!ConPtyLibrary.getInstance().CreatePseudoConsole(getSizeCoords(columns, rows), input, output, flags, hpcByReference)
      .equals(WinError.S_OK)) {
      throw new LastErrorExceptionEx("CreatePseudoConsole");
    }
    hpc = hpcByReference.getValue();
    myLastWinSize = new WinSize(columns, rows);
  }

  public WinEx.HPCON getHandle() {
    return hpc;
  }

  public void resize(int columns, int rows) throws IOException {
    if (!ConPtyLibrary.getInstance().ResizePseudoConsole(hpc, getSizeCoords(columns, rows)).equals(WinError.S_OK)) {
      throw new LastErrorExceptionEx("ResizePseudoConsole");
    }
    myLastWinSize = new WinSize(columns, rows);
  }

  public @NotNull WinSize getWinSize() throws IOException {
    if (myClosed) {
      throw new IOException(WinConPtyProcess.class.getName() + ": unable to get window size for closed PseudoConsole");
    }
    return myLastWinSize;
  }

  public void close() {
    if (!myClosed) {
      myClosed = true;
      ConPtyLibrary.getInstance().ClosePseudoConsole(hpc);
    }
  }
}

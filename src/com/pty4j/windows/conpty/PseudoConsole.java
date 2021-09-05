package com.pty4j.windows.conpty;

import com.pty4j.WinSize;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.Wincon;

public class PseudoConsole implements AutoCloseable {

    private static final Kernel32 kernel32 = Kernel32.INSTANCE;

    private final WinNT.HPCON hpc;

    private static Wincon.COORD getSizeCoords(WinSize size) {
        Wincon.COORD sizeCoords = new Wincon.COORD();
        sizeCoords.X = (short) size.getColumns();
        sizeCoords.Y = (short) size.getRows();
        return sizeCoords;
    }

    public PseudoConsole(WinSize size, com.sun.jna.platform.win32.WinNT.HANDLE input, com.sun.jna.platform.win32.WinNT.HANDLE output) {
        WinNT.HPCONByReference hpcByReference = new WinNT.HPCONByReference();
        if (!kernel32.CreatePseudoConsole(
                getSizeCoords(size),
                input,
                output,
                new WinDef.DWORD(0L),
                hpcByReference).equals(WinError.S_OK)) {
            Kernel32.throwLastError();
        }

        hpc = hpcByReference.getValue();
    }

    public WinNT.HPCON getHandle() {
        return hpc;
    }

    public void resize(WinSize newSize) {
        if (!kernel32.ResizePseudoConsole(hpc, getSizeCoords(newSize)).equals(WinError.S_OK)) {
            Kernel32.throwLastError();
        }
    }

    @Override
    public void close() {
        kernel32.ClosePseudoConsole(hpc);
    }
}

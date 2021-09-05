package com.pty4j.windows.conpty;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessOptions;
import com.pty4j.WinSize;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.Wincon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConPtyProcess extends PtyProcess {

    private final Pipe inPipe = new Pipe();
    private final Pipe outPipe = new Pipe();
    private final PseudoConsole pseudoConsole;
    private final WinBase.PROCESS_INFORMATION processInformation;

    // TODO: Call this after the process termination.
    private void dispose() {
        // TODO: Drain all the output in case of termination?
        inPipe.close();
        outPipe.close();
        pseudoConsole.close();
    }

    private static WinSize getInitialSize(@Nullable Integer initialColumns, @Nullable Integer initialRows) {
        short cols = initialColumns == null ? 80 : initialColumns.shortValue();
        short rows = initialRows == null ? 25 : initialRows.shortValue();

        return new WinSize(cols, rows);
    }

    private static WinBase.PROCESS_INFORMATION startProcess(PseudoConsole pseudoConsole, String commandLine) {
        WinNT.STARTUPINFOEX startupInfo = ProcessUtils.prepareStartupInformation(pseudoConsole);
        return ProcessUtils.start(startupInfo, commandLine);
    }

    public ConPtyProcess(PtyProcessOptions options) {
        pseudoConsole = new PseudoConsole(
                getInitialSize(options.getInitialColumns(), options.getInitialRows()),
                inPipe.getReadPipe(),
                outPipe.getWritePipe());
        processInformation = startProcess(pseudoConsole, "powershell.exe"); // TODO: process commandline properly
    }

    @Override
    public boolean isRunning() {
        throw new RuntimeException("TODO");
    }

    @Override
    public void setWinSize(WinSize winSize) {
        pseudoConsole.resize(winSize);
    }

    @Override
    public @NotNull WinSize getWinSize() throws IOException {
        throw new RuntimeException("TODO");
    }

    @Override
    public int getPid() {
        return processInformation.dwProcessId.intValue();
    }

    @Override
    public OutputStream getOutputStream() {
        throw new RuntimeException("TODO");
    }

    @Override
    public InputStream getInputStream() {
        throw new RuntimeException("TODO");
    }

    @Override
    public InputStream getErrorStream() {
        throw new RuntimeException("TODO");
    }

    @Override
    public int waitFor() throws InterruptedException {
        throw new RuntimeException("TODO");
    }

    @Override
    public int exitValue() {
        throw new RuntimeException("TODO");
    }

    @Override
    public void destroy() {
        throw new RuntimeException("TODO");
        // TODO: dispose();
    }
}

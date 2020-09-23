package com.pty4j.windows;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessOptions;
import com.pty4j.WinSize;
import com.sun.jna.platform.win32.Advapi32Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

/**
 * @author traff
 */
public class WinPtyProcess extends PtyProcess {
    private final WinPty myWinPty;
    private final WinPTYInputStream myInputStream;
    private final InputStream myErrorStream;
    private final WinPTYOutputStream myOutputStream;

    private boolean myUsedInputStream = false;
    private boolean myUsedOutputStream = false;
    private boolean myUsedErrorStream = false;

    @Deprecated
    public WinPtyProcess(String[] command, String[] environment, String workingDirectory, boolean consoleMode) throws IOException {
        this(command, convertEnvironment(environment), workingDirectory, consoleMode);
    }

    private static String convertEnvironment(String[] environment) {
        StringBuilder envString = new StringBuilder();
        for (String s : environment) {
            envString.append(s).append('\0');
        }
        envString.append('\0');
        return envString.toString();
    }

    @Deprecated
    public WinPtyProcess(String[] command, String environment, String workingDirectory, boolean consoleMode) throws IOException {
        this(command, environment, workingDirectory, null, null, consoleMode, false);
    }

    public WinPtyProcess(@NotNull PtyProcessOptions options, boolean consoleMode) throws IOException {
        this(options.getCommand(),
             convertEnvironment(options.getEnvironment()),
             options.getDirectory(),
             options.getInitialColumns(),
             options.getInitialRows(),
             consoleMode,
             options.isWindowsAnsiColorEnabled());
    }

    @NotNull
    private static String convertEnvironment(@Nullable Map<String, String> environment) {
        return Advapi32Util.getEnvironmentBlock(environment != null ? environment : Collections.<String, String>emptyMap());
    }

    private WinPtyProcess(@NotNull String[] command,
                          @NotNull String environment,
                          @Nullable String workingDirectory,
                          @Nullable Integer initialColumns,
                          @Nullable Integer initialRows,
                          boolean consoleMode,
                          boolean enableAnsiColor) throws IOException {
        try {
            myWinPty = new WinPty(joinCmdArgs(command), workingDirectory, environment, consoleMode,
                                  initialColumns, initialRows, enableAnsiColor);
        } catch (WinPtyException e) {
            throw new IOException("Couldn't create PTY", e);
        }
        myInputStream = new WinPTYInputStream(myWinPty, myWinPty.getInputPipe());
        myOutputStream = new WinPTYOutputStream(myWinPty, myWinPty.getOutputPipe(), consoleMode, true);
        if (!consoleMode) {
            myErrorStream = new InputStream() {
                @Override
                public int read() {
                    return -1;
                }
            };
        } else {
            myErrorStream = new WinPTYInputStream(myWinPty, myWinPty.getErrorPipe());
        }
    }

    static String joinCmdArgs(String[] commands) {
        StringBuilder cmd = new StringBuilder();
        boolean flag = false;
        for (String s : commands) {
            if (flag) {
                cmd.append(' ');
            } else {
                flag = true;
            }

            if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0) {
                if (s.charAt(0) != '"') {
                    cmd.append('"').append(s);

                    if (s.endsWith("\\")) {
                        cmd.append("\\");
                    }
                    cmd.append('"');
                } else {
                    cmd.append(s);
                }
            } else {
                cmd.append(s);
            }
        }

        return cmd.toString();
    }

    @Override
    public boolean isRunning() {
        return myWinPty.isRunning();
    }

    @Override
    public void setWinSize(WinSize winSize) {
        myWinPty.setWinSize(winSize);
    }

    @Override
    public WinSize getWinSize() throws IOException {
        return myWinPty.getWinSize();
    }

    @Override
    public int getPid() {
        return myWinPty.getChildProcessId();
    }

    @Nullable
    public String getWorkingDirectory() throws IOException {
        return myWinPty.getWorkingDirectory();
    }

    /**
     * @return Process count attached to this console. Please note that winpty-agent.exe is always attached,
     *          so, if process is alive, the returned value is 2 or greater.
     * @throws IOException if I/O errors occurred
     */
    public int getConsoleProcessCount() throws IOException {
        return myWinPty.getConsoleProcessList();
    }

    @Override
    public synchronized OutputStream getOutputStream() {
        myUsedOutputStream = true;
        return myOutputStream;
    }

    @Override
    public synchronized InputStream getInputStream() {
        myUsedInputStream = true;
        return myInputStream;
    }

    @Override
    public synchronized InputStream getErrorStream() {
        myUsedErrorStream = true;
        return myErrorStream;
    }

    @Override
    public int waitFor() throws InterruptedException {
        return myWinPty.waitFor();
    }

    public int getChildProcessId() {
        return myWinPty.getChildProcessId();
    }

    @Override
    public int exitValue() {
        return myWinPty.exitValue();
    }

    @Override
    public synchronized void destroy() {
        myWinPty.close();

        // Close unused streams.
        if (!myUsedInputStream) {
            try {
                myInputStream.close();
            } catch (IOException e) { }
        }
        if (!myUsedOutputStream) {
            try {
                myOutputStream.close();
            } catch (IOException e) { }
        }
        if (!myUsedErrorStream) {
            try {
                myErrorStream.close();
            } catch (IOException e) { }
        }
    }
}

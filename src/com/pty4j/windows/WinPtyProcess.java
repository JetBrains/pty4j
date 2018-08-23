package com.pty4j.windows;

import com.pty4j.PtyException;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

    public WinPtyProcess(String[] command, String environment, String workingDirectory, boolean consoleMode) throws IOException {
        try {
            myWinPty = new WinPty(joinCmdArgs(command), workingDirectory, environment, consoleMode);
        } catch (PtyException e) {
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

/*
 * JPty - A small PTY interface for Java.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.pty4j.unix.freebsd;


import com.pty4j.WinSize;
import com.pty4j.unix.PtyHelpers;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.StringArray;
import com.sun.jna.ptr.IntByReference;
import jtermios.JTermios;


/**
 * Provides a {@link com.pty4j.unix.PtyHelpers.OSFacade} implementation for FreeBSD.
 */
public class OSFacadeImpl implements PtyHelpers.OSFacade {
  // INNER TYPES

  public interface C_lib extends Library {
    int execv(String command, StringArray argv);

    int execve(String command, StringArray argv, StringArray env);

    int ioctl(int fd, int cmd, PtyHelpers.winsize data);

    int kill(int pid, int signal);

    int waitpid(int pid, int[] stat, int options);

    int sigprocmask(int how, IntByReference set, IntByReference oldset);

    String strerror(int errno);

    int grantpt(int fdm);

    int unlockpt(int fdm);

    int close(int fd);

    String ptsname(int fd);

    int open(String pts_name, int o_rdwr);

    int killpg(int pid, int sig);

    int fork();

    int setsid();

    int getpid();

    int setpgid(int pid, int pgid);

    void dup2(int fd, int fileno);

    int getppid();

    void unsetenv(String s);

    void chdir(String dirpath);
  }

  public interface Linux_Util_lib extends Library {
    int login_tty(int fd);
  }

  // CONSTANTS

  private static final int TIOCGWINSZ = 0x00005413;
  private static final int TIOCSWINSZ = 0x00005414;
  
  // VARIABLES

  private static C_lib m_Clib = (C_lib)Native.loadLibrary("c", C_lib.class);

  private static Linux_Util_lib m_Utillib = (Linux_Util_lib)Native.loadLibrary("util", Linux_Util_lib.class);

  // CONSTUCTORS

  /**
   * Creates a new {@link OSFacadeImpl} instance.
   */
  public OSFacadeImpl() {
    PtyHelpers.ONLCR = 0x04;

    PtyHelpers.VINTR = 0;
    PtyHelpers.VQUIT = 1;
    PtyHelpers.VERASE = 2;
    PtyHelpers.VKILL = 3;
    PtyHelpers.VSUSP = 10;
    PtyHelpers.VREPRINT = 12;
    PtyHelpers.VWERASE = 14;

    PtyHelpers.ECHOKE = 0x01;
    PtyHelpers.ECHOCTL = 0x40;
  }

  // METHODS

  @Override
  public int execve(String command, String[] argv, String[] env) {
    StringArray argvp = (argv == null) ? new StringArray(new String[]{command}) : new StringArray(argv);
    StringArray envp = (env == null) ? null : new StringArray(env);
    return m_Clib.execve(command, argvp, envp);
  }

  @Override
  public int getWinSize(int fd, WinSize winSize) {
    int r;

    PtyHelpers.winsize ws = new PtyHelpers.winsize();
    if ((r = m_Clib.ioctl(fd, TIOCGWINSZ, ws)) < 0) {
      return r;
    }
    ws.update(winSize);

    return r;
  }

  @Override
  public int kill(int pid, int signal) {
    return m_Clib.kill(pid, signal);
  }

  @Override
  public int setWinSize(int fd, WinSize winSize) {
    PtyHelpers.winsize ws = new PtyHelpers.winsize(winSize);
    return m_Clib.ioctl(fd, TIOCSWINSZ, ws);
  }

  @Override
  public int waitpid(int pid, int[] stat, int options) {
    return m_Clib.waitpid(pid, stat, options);
  }

  @Override
  public int sigprocmask(int how, IntByReference set, IntByReference oldset) {
    return m_Clib.sigprocmask(how, set, oldset);
  }

  @Override
  public String strerror(int errno) {
    return m_Clib.strerror(errno);
  }

  @Override
  public int getpt() {
    return JTermios.open("/dev/ptmx", JTermios.O_RDWR | JTermios.O_NOCTTY);
  }

  @Override
  public int grantpt(int fd) {
    return m_Clib.grantpt(fd);
  }

  @Override
  public int unlockpt(int fd) {
    return m_Clib.unlockpt(fd);
  }

  @Override
  public int close(int fd) {
    return m_Clib.close(fd);
  }

  @Override
  public String ptsname(int fd) {
    return m_Clib.ptsname(fd);
  }

  @Override
  public int killpg(int pid, int sig) {
    return m_Clib.killpg(pid, sig);
  }

  @Override
  public int fork() {
    return m_Clib.fork();
  }

  @Override
  public int pipe(int[] pipe2) {
    return JTermios.pipe(pipe2);
  }

  @Override
  public int setsid() {
    return m_Clib.setsid();
  }

  @Override
  public void execv(String path, String[] argv) {
    StringArray argvp = (argv == null) ? new StringArray(new String[]{path}) : new StringArray(argv);
    m_Clib.execv(path, argvp);
  }

  @Override
  public int getpid() {
    return m_Clib.getpid();
  }

  @Override
  public int setpgid(int pid, int pgid) {
    return m_Clib.setpgid(pid, pgid);
  }

  @Override
  public void dup2(int fds, int fileno) {
    m_Clib.dup2(fds, fileno);
  }

  @Override
  public int getppid() {
    return m_Clib.getppid();
  }

  @Override
  public void unsetenv(String s) {
    m_Clib.unsetenv(s);
  }

  @Override
  public int login_tty(int fd) {
    return m_Utillib.login_tty(fd);
  }

  @Override
  public void chdir(String dirpath) {
    m_Clib.chdir(dirpath);
  }
}

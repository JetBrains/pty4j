package com.pty4j.unix;

import jtermios.JTermios;

/**
 * @author traff
 */
public class JnaPtyExecutor implements PtyExecutor {
  private static final int STDIN_FILENO = 0;
  private static final int STDOUT_FILENO = 1;
  private static final int STDERR_FILENO = 2;

  @Override
  public int execPty(String full_path, String[] argv, String[] envp,
                     String dirpath, int[] channels, String pts_name, int fdm, String err_pts_name, int err_fdm, boolean console) {
    int childpid;

    PtyHelpers.OSFacade m_jpty = PtyHelpers.getInstance();
    
    childpid = m_jpty.fork();

    if (childpid < 0) {
//      fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
      return -1;
    } else if (childpid == 0) { /* child */


      PtyHelpers.chdir(dirpath);

      if (channels != null) {
        if (console && m_jpty.setsid() < 0) {
//          perror("setsid()");
          return -1;
        }

        int fds = ptySlaveOpen(fdm, pts_name);
        int err_fds = -1;

        if (console) {
          err_fds = ptySlaveOpen(err_fdm, err_pts_name);
        }


        if (fds < 0 || (console && err_fds < 0)) {
//          fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
          return -1;
        }

        m_jpty.login_tty(fds);

        if (JTermios.tcsetattr(fds, JTermios.TCSANOW, PtyHelpers.createTermios()) != 0) { 
          return -1;
        }

			/* close the master, no need in the child */
        m_jpty.close(fdm);
        if (console) m_jpty.close(err_fdm);

        if (console) {
          Pty.setNoEcho(fds);

          int pid = m_jpty.getpid();
          if (m_jpty.setpgid(pid, pid) < 0) {
//            perror("setpgid()");
            return -1;
          }
        }

			/* redirections */
        m_jpty.dup2(fds, STDIN_FILENO);   /* dup stdin */
        m_jpty.dup2(fds, STDOUT_FILENO);  /* dup stdout */
        m_jpty.dup2(console ? err_fds : fds, STDERR_FILENO);  /* dup stderr */

        m_jpty.close(fds);  /* done with fds. */
        if (console) m_jpty.close(err_fds);
      }

		/* Close all the fd's in the child */
      {
//        int fdlimit = 16;// m_jpty.sysconf(_SC_OPEN_MAX); TODO
//        int fd = 3;
//
//        while (fd < fdlimit) {
//          m_jpty.close(fd++);
//        }
      }

      if (envp[0] == null) {
        m_jpty.execv(full_path, argv);
      } else {
        m_jpty.execve(full_path, argv, envp);
      }

      System.exit(127);
    } else if (childpid != 0) { /* parent */
      if (console) {
        Pty.setNoEcho(fdm);
      }
      if (channels != null) {
        channels[0] = fdm; /* Input Stream. */
        channels[1] = fdm; /* Output Stream.  */
        channels[2] = console ? err_fdm : fdm; /* Error Stream.  */
      }

      return childpid;
    }

    return -1;                  /*NOT REACHED */
  }

  public static int ptySlaveOpen(int fdm, String pts_name) {
    int fds = JTermios.open(pts_name, JTermios.O_RDWR);
    if (fds < 0) {
      JTermios.close(fdm);
      return -5;
    }

    return fds;
  }
}

#include <errno.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <stdbool.h>

extern int ptys_open(int fdm, const char *pts_name, bool acquire);

extern void set_noecho(int fd);

void restore_signal(int signum) {
    struct sigaction action;
    memset(&action, 0, sizeof(action));
    action.sa_handler = SIG_DFL;
    sigemptyset(&action.sa_mask);
    action.sa_flags = 0;
    if (sigaction(signum, &action, NULL) != 0) {
        fprintf(stderr, "%s(%d): cannot set SIG_DFL for signal %d: %s\n", __FUNCTION__, __LINE__, signum, strerror(errno));
    }
}

void restore_signals() {
    restore_signal(SIGPIPE);
    restore_signal(SIGINT);
    restore_signal(SIGQUIT);
}

int parseInt(char* str) {
  return strtol(str, NULL, 10);
}

int main (int argc, char* argv[]) {


  const char *cwd = argv[1];
  const int consoleMode = parseInt(argv[2]);
  const char *pts_name = argv[3];
  const int fdm = parseInt(argv[4]);
  const char *err_pts_name = argv[5];
  const int err_fdm = parseInt(argv[6]);
  char *file = argv[7];
  argv = &argv[7];

  chdir(cwd);

  int err_fds = -1;

  if (!consoleMode && setsid() < 0) {
    perror("setsid()");
    return -1;
  }

  int fds = ptys_open(fdm, pts_name, true);
  if (fds < 0) {
    fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
    return -1;
  }

  if (consoleMode && err_fdm >= 0) {
    err_fds = ptys_open(err_fdm, err_pts_name, false);
    if (err_fds < 0) {
      fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
      return -1;
    }
  }

  if (consoleMode) {
    set_noecho(fds);
    if (setpgid(getpid(), getpid()) < 0) {
      perror("setpgid()");
      return -1;
    }
  }

  /* redirections */
  dup2(fds, STDIN_FILENO);   /* dup stdin */
  dup2(fds, STDOUT_FILENO);  /* dup stdout */
  dup2(consoleMode && err_fds >= 0 ? err_fds : fds, STDERR_FILENO);  /* dup stderr */

  close(fds);  /* done with fds. */
  if (consoleMode && err_fds >= 0) close(err_fds);

  restore_signals();

  execvp(file, argv);
  return 0;
}

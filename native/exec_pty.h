#ifndef _EXECPTY_H
#define _EXECPTY_H

#include <sys/ioctl.h>

pid_t exec_pty(const char *path, char *const argv[], char *const envp[], const char *dirpath,
               const char *pts_name, int fdm, const char *err_pts_name, int err_fdm, int console);

int wait_for_child_process_exit(int child_pid);

int get_window_size(int fd, struct winsize *size);

int set_window_size(int fd, const struct winsize *size);

int is_valid_fd(int fd);

#endif

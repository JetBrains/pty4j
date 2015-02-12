#ifndef _EXECPTY_H
#define _EXECPTY_H

pid_t exec_pty(const char *path, char *const argv[], char *const envp[], const char *dirpath,
               const char *pts_name, int fdm, const char *err_pts_name, int err_fdm, int console);
      
      
#endif
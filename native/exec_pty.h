#ifndef _EXECPTY_H
#define _EXECPTY_H

pid_t exec_pty(const char *path, char *const argv[], char *const envp[],
      const char *dirpath, int channels[3], const char *pts_name, int fdm, int console);
      
      
#endif
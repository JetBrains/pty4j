/*******************************************************************************
 * Copyright (c) 2004, 2010 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - initial API and implementation
 *     Wind River Systems, Inc.  
 *     Mikhail Zabaluev (Nokia) - bug 82744
 *     Mikhail Sennikovsky - bug 145737
 *******************************************************************************/
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <libgen.h>
#include <stdlib.h>
#include <termios.h>

#include "exec_pty.h"

int test();

/* from pfind.c */
extern char *pfind(const char *name, char * const envp[]);

/* from openpty.c */

extern int ptys_open(int fdm, const char * pts_name);

extern void set_noecho(int fd);


pid_t exec_pty(const char *path, char *const argv[], char *const envp[],
      const char *dirpath, int channels[3], const char *pts_name, int fdm, int console)
{
	int pipe2[2];
	pid_t childpid;
	char *full_path;

	/*
	 * We use pfind() to check that the program exists and is an executable.
	 * If not pass the error up.  Also execve() wants a full path.
	 */ 
	full_path = pfind(path, envp);
	if (full_path == NULL) {
		fprintf(stderr, "Unable to find full path for \"%s\"\n", (path) ? path : "");
		return -1;
	}

	/*
	 *  Make sure we can create our pipes before forking.
	 */ 
	if (channels != NULL && console) {
		if (pipe(pipe2) < 0) { 
			fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
			free(full_path);
			return -1;
		}
	}

	childpid = fork();

	if (childpid < 0) {
		fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
		free(full_path);
		return -1;
	} else if (childpid == 0) { /* child */

		chdir(dirpath);

		if (channels != NULL) {
			int fds;

			if (!console && setsid() < 0) {
				perror("setsid()");
				return -1;
			}

			fds = ptys_open(fdm, pts_name);
			if (fds < 0) {
				fprintf(stderr, "%s(%d): returning due to error: %s\n", __FUNCTION__, __LINE__, strerror(errno));
				return -1;
			}

			/* Close the read end of pipe2 */
			if (console && close(pipe2[0]) == -1) {
				perror("close(pipe2[0]))");
			}

			/* close the master, no need in the child */
			close(fdm);

			if (console) {
				set_noecho(fds);
				if (setpgid(getpid(), getpid()) < 0) {
					perror("setpgid()");
					return -1;
				}
			}

			/* redirections */
			dup2(fds, STDIN_FILENO);   /* dup stdin */
			dup2(fds, STDOUT_FILENO);  /* dup stdout */
			if (console) {
				dup2(pipe2[1], STDERR_FILENO);  /* dup stderr */
			} else {
				dup2(fds, STDERR_FILENO);  /* dup stderr */
			}
			close(fds);  /* done with fds. */
		}

		/* Close all the fd's in the child */
		{
			int fdlimit = sysconf(_SC_OPEN_MAX);
			int fd = 3;

			while (fd < fdlimit)
				close(fd++);
		}

		if (envp[0] == NULL) {
			execv(full_path, argv);
		} else {
			execve(full_path, argv, envp);
		}

		_exit(127);

	} else if (childpid != 0) { /* parent */
		if (console) {
			set_noecho(fdm);
		}
		if (channels != NULL) {
			channels[0] = fdm; /* Input Stream. */
			channels[1] = fdm; /* Output Stream.  */
			if (console) {
				/* close the write end of pipe1 */
				if (close(pipe2[1]) == -1)
					perror("close(pipe2[1])");
				channels[2] = pipe2[0]; /* stderr Stream.  */
			} else {
				channels[2] = fdm; /* Error Stream.  */
			}
		}

		free(full_path);
		return childpid;
	}

	free(full_path);
	return -1;                  /*NOT REACHED */
}


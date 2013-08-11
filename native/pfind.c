/*******************************************************************************
 * Copyright (c) 2002, 2010 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - initial API and implementation
 *     Wind River Systems, Inc.
 *     Mikhail Sennikovsky - bug 145737
 *     Everton Rufino Constantino (IBM) - bug 237611
 *******************************************************************************/
/*
 * pfind.c - Search for a binary in $PATH.
 */

#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>

#ifndef PATH_MAX
#define PATH_MAX 1024
#endif

#define PATH_DEF "PATH="
const int path_def_len = 5; /* strlen(PATH_DEF); */

char * path_val(char * const envp[])
{
	int i;
	if (envp == NULL || envp[0] == NULL)
		return getenv("PATH" );
	
	for(i = 0; envp[i] != NULL; i++){
		char* p = envp[i];
		if(!strncmp(PATH_DEF, p, path_def_len)){
			return p + path_def_len;
		}
	}
	
	return NULL;
}

char * pfind(const char *name, char * const envp[])
{
	char *tok;
	char *sp;
	char *path;
	char fullpath[PATH_MAX+1];

	/* Sanity check.  */
	if (name == NULL) {
		fprintf(stderr, "pfind(): Null argument.\n");
		return NULL;
	}

	/* For absolute name or name with a path, check if it is an executable.  */
	if (name[0] == '/' || name[0] == '.') {
		if (access(name, X_OK) == 0) {
			return strdup(name);
		}
		return NULL;
	}

	/* Search in the PATH environment.  */
	path = path_val( envp );

	if (path == NULL || strlen(path) <= 0) {
		fprintf(stderr, "Unable to get $PATH.\n");
		return NULL;
	}

	/* The value return by getenv() is readonly */
	path = strdup(path);

	tok = strtok_r(path, ":", &sp);
	while (tok != NULL) {
		snprintf(fullpath, sizeof(fullpath) - 1, "%s/%s", tok, name);

		if (access(fullpath, X_OK) == 0) {
			free(path);
			return strdup(fullpath);
		}

		tok = strtok_r( NULL, ":", &sp );
	}

	free(path);
	return NULL;
}

#ifdef BUILD_WITH_MAIN
int main(int argc, char **argv)
{
   int i;
   char *fullpath;

   for (i=1; i<argc; i++) {
      fullpath = pfind(argv[i], NULL);
      if (fullpath == NULL)
        printf("Unable to find %s in $PATH.\n", argv[i]);
      else 
        printf("Found %s @ %s.\n", argv[i], fullpath);
   }
}
#endif

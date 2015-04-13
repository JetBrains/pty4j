#include <sys/fcntl.h>
#include <cygwin/stdlib.h>
#include <sys/unistd.h>
#include <w32api/minwindef.h>
#include <stdbool.h>
#include <pthread.h>
#include <w32api/windef.h>
#include <w32api/winbase.h>
#include <sys/wait.h>
#include "../exec_pty.h"
#include <sys/cygwin.h>
#include <locale.h>
#include <w32api/stringapiset.h>
#include <stdio.h>
#include <stdarg.h>

struct pty_t {
    int fdm;
    char slave_name[100];
};

struct thread_data_t {
    int fdm;
    HANDLE pipe;
    pthread_t tid;
    pthread_cond_t *condition;
    pthread_mutex_t *mutex;
};

static pthread_cond_t out_cond = PTHREAD_COND_INITIALIZER;
static pthread_mutex_t out_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t err_cond = PTHREAD_COND_INITIALIZER;
static pthread_mutex_t err_mutex = PTHREAD_MUTEX_INITIALIZER;

volatile bool shutting_down = false;
FILE *logFile = NULL;

void flog(const char *string, ...) {
    if (logFile != NULL) {
        va_list argptr;
        va_start(argptr, string);
        vfprintf(logFile, string, argptr);
        va_end(argptr);

        fwrite("\n", sizeof(char), 1, logFile);
    }
}

int create_pty(struct pty_t *pty) {
    pty->fdm = open("/dev/ptmx", O_RDWR|O_NOCTTY);
    if (pty->fdm < 0) {
        flog("Could not open /dev/ptmx");
        return -1;
    }
    if (grantpt(pty->fdm) < 0) {
        flog("grantpt() failed");
        close(pty->fdm);
        return -1;
    }
    if (unlockpt(pty->fdm) < 0) {
        flog("unlockpt() failed");
        close(pty->fdm);
        return -1;
    }
    char *slave_name = ptsname(pty->fdm);
    if (slave_name == NULL) {
        flog("Could not determine slave name");
        close(pty->fdm);
        return -1;
    }
    strcpy(pty->slave_name, slave_name);
    return 0;
}


void* writePipe(void *arg) {
    struct thread_data_t thread_data = *(struct thread_data_t*)arg;
    char buf[1024];
    ssize_t len = 0;
    while (!shutting_down || len > 0) {
        len = read(thread_data.fdm, buf, 1024);
        DWORD written;
        if (len > 0) {
            pthread_cond_signal(thread_data.condition);
            WriteFile(thread_data.pipe, buf, (DWORD)len, &written, NULL);
        }
    }
    return NULL;
}

void* readPipe(void *arg) {
    struct thread_data_t thread_data = *(struct thread_data_t*)arg;
    char buf[1024];
    DWORD len = 0;
    while (!shutting_down || len > 0) {
        WINBOOL res = ReadFile(thread_data.pipe, buf, 1024, &len, NULL);
        if (len > 0) write(thread_data.fdm, buf, len);
        else if (!res && GetLastError() == ERROR_BROKEN_PIPE) {
            close(thread_data.fdm);
            break;
        }
    }
    return NULL;
}

char* convert_path(char *command) {
    int len = MultiByteToWideChar(CP_UTF8, 0, command, -1, NULL, 0);
    wchar_t *wp = (wchar_t *) malloc (len * sizeof(wchar_t));
    MultiByteToWideChar(CP_UTF8, 0, command, -1, wp, len);

    char *buf = malloc(32768);
    cygwin_conv_path(CCP_WIN_W_TO_POSIX | CCP_ABSOLUTE, wp, buf, 32768);
    return buf;
}

void readOutputAndClose(struct thread_data_t thread_data) {
    static long WAIT_NS = 200 * 1000000;
    static long MAX_NS = 999999999;

    timespec_t timespec;
    bool readComplete = false;
    while (!readComplete) {
        clock_gettime(CLOCK_REALTIME, &timespec);

        timespec.tv_nsec += WAIT_NS;
        if (timespec.tv_nsec > MAX_NS) {
            timespec.tv_sec += 1;
            timespec.tv_nsec -= MAX_NS;
        }

        pthread_mutex_lock(thread_data.mutex);
        readComplete = pthread_cond_timedwait(thread_data.condition, thread_data.mutex, &timespec) != 0;
        pthread_mutex_unlock(thread_data.mutex);
    }

    close(thread_data.fdm);
    pthread_join(thread_data.tid, NULL);
    CloseHandle(thread_data.pipe);
}

int main(int argc, char* argv[], char* envp[]) {
    int arg = 1;
    char *logFileName = argv[arg++];
    if (strcmp(logFileName, "null") != 0) {
        logFile = fopen(convert_path(logFileName), "w+");
    }

    bool consoleMode = (bool)atoi(argv[arg++]);

    struct pty_t pty;
    struct pty_t err_pty;

    flog("opening pty");
    if (create_pty(&pty) < 0) return -1;
    flog("opening err_pty");
    if (create_pty(&err_pty) < 0) return -1;

    struct thread_data_t thread_data_in = {pty.fdm, CreateFile(argv[arg++], GENERIC_READ, 0, NULL, OPEN_EXISTING, 0, NULL), NULL, NULL, NULL};
    if (thread_data_in.pipe == INVALID_HANDLE_VALUE) flog("Opening in-pipe failed with %d", GetLastError());

    struct thread_data_t thread_data_out = {pty.fdm, CreateFile(argv[arg++], GENERIC_WRITE, 0, NULL, OPEN_EXISTING, 0, NULL), NULL, &out_cond, &out_mutex};
    if (thread_data_out.pipe == INVALID_HANDLE_VALUE) flog("Opening out-pipe failed with %d", GetLastError());

    char *errPipeName = argv[arg++];
    struct thread_data_t thread_data_err;
    if (consoleMode) {
        thread_data_err = (struct thread_data_t) {err_pty.fdm, CreateFile(errPipeName, GENERIC_WRITE, 0, NULL, OPEN_EXISTING, 0, NULL), NULL, &err_cond, &err_mutex};
        if (thread_data_err.pipe == INVALID_HANDLE_VALUE) flog("Opening err-pipe failed with %d", GetLastError());
    }

    char *command = argv[arg++];

    flog("converting path: %s", command);
    char *path = convert_path(command);
    flog("converted: %s", path);

    char * cargv[argc - arg + 2];
    memcpy(cargv + 1, argv + arg, sizeof(char*) * (argc - arg));
    cargv[0] = path;
    cargv[argc - arg + 1] = NULL;

    pid_t child_pid = exec_pty(path, cargv, envp, ".", pty.slave_name, pty.fdm, err_pty.slave_name, err_pty.fdm, consoleMode);
    free(path);

    flog("launched pid: %d", child_pid);

    pthread_create(&thread_data_in.tid, NULL, &readPipe, &thread_data_in);
    pthread_create(&thread_data_out.tid, NULL, &writePipe, &thread_data_out);
    if (consoleMode) {
        pthread_create(&thread_data_err.tid, NULL, &writePipe, &thread_data_err);
    }

    int status;
    waitpid(child_pid, &status, 0);

    shutting_down = true;

    // Not shutting down the stdin thread because there is no way to abort synchronous ReadFile.
    // Since the process is already dead, it doesn't matter anyway.

    readOutputAndClose(thread_data_out);
    if (consoleMode) readOutputAndClose(thread_data_err);

    if (logFile != NULL) fclose(logFile);

    return WEXITSTATUS(status);
}

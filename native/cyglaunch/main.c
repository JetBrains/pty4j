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

struct pty_t {
    int fdm;
    char slave_name[100];
};

struct thread_data_t {
    int fdm;
    HANDLE pipe;
};

volatile bool shutting_down = false;

int create_pty(struct pty_t *pty) {
    pty->fdm = open("/dev/ptmx", O_RDWR|O_NOCTTY);
    if (pty->fdm < 0)
        return -1;
    if (grantpt(pty->fdm) < 0) {
        close(pty->fdm);
        return -1;
    }
    if (unlockpt(pty->fdm) < 0) {
        close(pty->fdm);
        return -1;
    }
    char *slave_name = ptsname(pty->fdm);
    if (slave_name == NULL) {
        close(pty->fdm);
        return -1;
    }
    strcpy(pty->slave_name, slave_name);
    return 0;
}


void* writePipe(void *arg) {
    struct thread_data_t thread_data = *(struct thread_data_t*)arg;
    char buf[1024];
    while(!shutting_down) {
        ssize_t len = read(thread_data.fdm, buf, 1024);
        if (len > 0) WriteFile(thread_data.pipe, buf, (DWORD)len, NULL, NULL);
    }
    return NULL;
}

void* readPipe(void *arg) {
    struct thread_data_t thread_data = *(struct thread_data_t*)arg;
    char buf[1024];
    while(!shutting_down) {
        DWORD len;
        ReadFile(thread_data.pipe, buf, 1024, &len, NULL);
        if (len > 0) {
            write(thread_data.fdm, buf, len);
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

int main(int argc, char* argv[], char* envp[]) {
    struct pty_t pty;
    struct pty_t err_pty;

    if (create_pty(&pty) < 0) return -1;
    if (create_pty(&err_pty) < 0) return -1;

    int arg = 1;
    struct thread_data_t thread_data_in = {pty.fdm, CreateFile(argv[arg++], GENERIC_READ, 0, NULL, OPEN_EXISTING, 0, NULL)};
    struct thread_data_t thread_data_out = {pty.fdm, CreateFile(argv[arg++], GENERIC_WRITE, 0, NULL, OPEN_EXISTING, 0, NULL)};
    struct thread_data_t thread_data_err = {err_pty.fdm, CreateFile(argv[arg++], GENERIC_WRITE, 0, NULL, OPEN_EXISTING, 0, NULL)};

    char *command = argv[arg++];
    int channels[3];

    char *path = convert_path(command);

    char * cargv[argc - arg + 2];
    memcpy(cargv + 1, argv + arg, sizeof(char*) * (argc - arg));
    cargv[0] = path;
    cargv[argc - arg + 1] = NULL;

    pid_t child_pid = exec_pty(path, cargv, envp, ".", channels, pty.slave_name, pty.fdm, err_pty.slave_name, err_pty.fdm, true);
    free(path);

    pthread_t tid[3];
    pthread_create(&tid[0], NULL, &readPipe, &thread_data_in);
    pthread_create(&tid[1], NULL, &writePipe, &thread_data_out);
    pthread_create(&tid[2], NULL, &writePipe, &thread_data_err);

    int status;
    waitpid(child_pid, &status, 0);

    shutting_down = true;

    // Not shutting down the stdin thread because there is no way to abort synchronous ReadFile.
    // Since the process is already dead, it doesn't matter anyway.

    close(thread_data_out.fdm);
    pthread_join(tid[1], NULL);
    CloseHandle(thread_data_out.pipe);

    close(thread_data_err.fdm);
    pthread_join(tid[2], NULL);
    CloseHandle(thread_data_err.pipe);

    return WEXITSTATUS(status);
}
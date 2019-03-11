#include <stdio.h>

#if defined(_MSC_VER)
#include <io.h>
#define STDOUT_FILENO fileno(stdout)
#define STDERR_FILENO fileno(stderr)
#else
#include <unistd.h>
#endif

int main() {
    printf("isatty(stdout): %d, isatty(stderr): %d\n", (bool)isatty(STDOUT_FILENO), (bool)isatty(STDERR_FILENO));
    fprintf(stderr, "hello from stderr\n");

    int input;
    printf("enter int: ");
    scanf("%d", &input);
    printf("entered %d\n", input);
}

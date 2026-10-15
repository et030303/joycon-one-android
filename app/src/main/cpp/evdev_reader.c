// Evdev reader — runs under Shizuku shell context (u:r:shell:s0), which may read /dev/input.
//
//   evdev_reader list          one line per node: path TAB vendor TAB product TAB uniq TAB name
//   evdev_reader read <node>   "OK\n", then 8-byte little-endian records (u16 type, u16 code, i32 value)
//
// While reading, stdin takes one byte at a time: 'G' grabs the node (EVIOCGRAB), 'U' releases it.
// Stdin closing ends the process, which releases the grab.

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define RECORD_SIZE 8
#define BATCH_EVENTS 64

static int list_nodes(void) {
    DIR *dir = opendir("/dev/input");
    if (!dir) {
        fprintf(stderr, "ERR opendir /dev/input: %s\n", strerror(errno));
        return 1;
    }
    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;

        char path[64];
        snprintf(path, sizeof(path), "/dev/input/%s", entry->d_name);
        int fd = open(path, O_RDONLY);
        if (fd < 0) continue;

        struct input_id id;
        char name[128] = "", uniq[64] = "";
        if (ioctl(fd, EVIOCGID, &id) == 0) {
            ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
            ioctl(fd, EVIOCGUNIQ(sizeof(uniq) - 1), uniq);
            printf("%s\t%04x\t%04x\t%s\t%s\n", path, id.vendor, id.product, uniq, name);
        }
        close(fd);
    }
    closedir(dir);
    return 0;
}

static void write_all(const uint8_t *buf, size_t len) {
    while (len > 0) {
        ssize_t n = write(STDOUT_FILENO, buf, len);
        if (n <= 0) _exit(0);
        buf += n;
        len -= (size_t)n;
    }
}

static void forward_events(int fd) {
    struct input_event events[BATCH_EVENTS];
    ssize_t n = read(fd, events, sizeof(events));
    if (n <= 0) {
        if (n < 0 && (errno == EAGAIN || errno == EINTR)) return;
        _exit(0);
    }
    size_t count = (size_t)n / sizeof(struct input_event);
    uint8_t out[BATCH_EVENTS * RECORD_SIZE];
    for (size_t i = 0; i < count; i++) {
        uint8_t *r = out + i * RECORD_SIZE;
        uint16_t type = events[i].type, code = events[i].code;
        int32_t value = events[i].value;
        memcpy(r, &type, 2);
        memcpy(r + 2, &code, 2);
        memcpy(r + 4, &value, 4);
    }
    write_all(out, count * RECORD_SIZE);
}

static void handle_command(int fd) {
    char c;
    ssize_t n = read(STDIN_FILENO, &c, 1);
    if (n == 0 || (n < 0 && errno != EAGAIN && errno != EINTR)) _exit(0);
    if (n < 0) return;
    if (c == 'G' || c == 'U') ioctl(fd, EVIOCGRAB, c == 'G' ? 1 : 0);
}

static int read_node(const char *path) {
    int fd = open(path, O_RDONLY | O_NONBLOCK);
    if (fd < 0) {
        fprintf(stderr, "ERR open %s: %s\n", path, strerror(errno));
        return 1;
    }

    int ep = epoll_create1(0);
    struct epoll_event ev = { .events = EPOLLIN };
    ev.data.fd = fd;
    epoll_ctl(ep, EPOLL_CTL_ADD, fd, &ev);
    ev.data.fd = STDIN_FILENO;
    epoll_ctl(ep, EPOLL_CTL_ADD, STDIN_FILENO, &ev);

    write_all((const uint8_t *)"OK\n", 3);

    for (;;) {
        struct epoll_event ready[2];
        int n = epoll_wait(ep, ready, 2, -1);
        if (n < 0 && errno != EINTR) return 1;
        for (int i = 0; i < n; i++) {
            if (ready[i].data.fd == STDIN_FILENO) handle_command(fd);
            else forward_events(fd);
        }
    }
}

int main(int argc, char **argv) {
    if (argc == 2 && strcmp(argv[1], "list") == 0) return list_nodes();
    if (argc == 3 && strcmp(argv[1], "read") == 0) return read_node(argv[2]);
    fprintf(stderr, "usage: evdev_reader list | read <node>\n");
    return 2;
}

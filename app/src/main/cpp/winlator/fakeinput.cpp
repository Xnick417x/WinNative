#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <iostream>
#include <unordered_map>
#include <memory>
#include <fstream>
#include <algorithm>
#include <mutex>

#include <fcntl.h>
#include <dirent.h>
#include <stdio.h>
#include <sys/types.h>
#include <unistd.h>
#include <dlfcn.h>
#include <stdarg.h>
#include <string.h>
#include <stdbool.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/inotify.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <linux/input.h>

#define EXPORT __attribute__((visibility("default"))) extern "C"

std::unordered_map<int, const char *> controller_map;
static bool initialized = false;
static const char *hook_dir = nullptr;
volatile sig_atomic_t stop_flag = 0;

static int (*my_open)(const char *, int, ...) = nullptr;
static int (*my_openat)(int, const char *, int, ...) = nullptr;
static int (*my_stat)(const char *, struct stat *) = nullptr;
static int (*my_lstat)(const char *, struct stat *) = nullptr;
static int (*my_fstat)(int fd, struct stat *buf) = nullptr;
static int (*my_access)(const char *, int) = nullptr;
static int (*my_scandir)(const char *, struct dirent***, int(*)(const struct dirent *), int(*)(const struct dirent**, const struct dirent**));
static int (*my_inotify_add_watch)(int, const char *, uint32_t);
static int (*my_close)(int);

namespace Logger {
    int log_enabled;

    void init() {
        log_enabled = getenv("FAKE_EVDEV_LOG") && atoi(getenv("FAKE_EVDEV_LOG"));
    }

    void log(const char *message, ...) {
        if (!log_enabled)
            return;

        va_list args;
        va_start(args, message);
        vfprintf(stderr, message, args);
        va_end(args);

        std::cerr.flush();
    }
}

void handle_sigint(int sig) {
    (void)sig;
    stop_flag = 1;
}

void setup_signal_handler() {
    if (!initialized) {
        signal(SIGINT, handle_sigint);
        initialized = true;
    }
}

__attribute__((constructor))
static void library_init() {
    if (!hook_dir)
        hook_dir = getenv("FAKE_EVDEV_DIR") ? strdup(getenv("FAKE_EVDEV_DIR")) : strdup("/data/data/com.winnative.cmod/files/imagefs/dev/input");

    Logger::init();
}

__attribute__((visibility("hidden")))
char *from_real_to_fake_path(const char *pathname) {
    const char *event = strrchr(pathname, '/');
    if (!event) return nullptr;
    event++; // skip slash
    char *fake_path;
    asprintf(&fake_path, "%s/%s", hook_dir, event);
    return fake_path;
}

__attribute__((visibility("hidden")))
const char *get_event(const char *pathname) {
    const char *event = strrchr(pathname, '/');
    return event ? event + 1 : pathname;
}

__attribute__((visibility("hidden")))
int get_event_number(const char *event) {
    if (event && strncmp(event, "event", 5) == 0) {
        return atoi(event + 5);
    }
    return -1;
}

EXPORT int open(const char *pathname, int flags, ...) {
    if (!my_open)
        *(void **)&my_open = dlsym(RTLD_NEXT, "open");

    bool isFromInput = false;
    char *allocated_path = nullptr;
    const char *final_path = pathname;

    if (pathname) {
        if (strstr(pathname, "/dev/input/event")) {
            allocated_path = from_real_to_fake_path(pathname);
            if (allocated_path) {
                final_path = allocated_path;
                isFromInput = true;
            }
        } else if (!strcmp(pathname, "/dev/input")) {
            final_path = hook_dir;
        }
    }

    int fd;
    if (flags & O_CREAT) {
        va_list va;
        va_start(va, flags);
        mode_t mode = va_arg(va, mode_t);
        va_end(va);
        fd = my_open(final_path, flags, mode);
    } else {
        fd = my_open(final_path, flags);
    }

    if (isFromInput && fd >= 0) {
        Logger::log("Adding controller, fd %d event %s\n", fd, get_event(final_path));
        controller_map[fd] = strdup(get_event(final_path));
    }

    if (allocated_path) free(allocated_path);
    return fd;
}

EXPORT int openat(int dirfd, const char *pathname, int flags, ...) {
    if (!my_openat)
        *(void **)&my_openat = dlsym(RTLD_NEXT, "openat");

    bool isFromInput = false;
    char *allocated_path = nullptr;
    const char *final_path = pathname;

    if (pathname) {
        if (strstr(pathname, "/dev/input/event")) {
            allocated_path = from_real_to_fake_path(pathname);
            if (allocated_path) {
                final_path = allocated_path;
                isFromInput = true;
            }
        } else if (!strcmp(pathname, "/dev/input")) {
            final_path = hook_dir;
        }
    }

    int fd;
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list va;
        va_start(va, flags);
        mode = va_arg(va, mode_t);
        va_end(va);
        fd = my_openat(dirfd, final_path, flags, mode);
    } else {
        fd = my_openat(dirfd, final_path, flags);
    }

    if (isFromInput && fd >= 0) {
        Logger::log("Adding controller, fd %d event %s\n", fd, get_event(final_path));
        controller_map[fd] = strdup(get_event(final_path));
    }

    if (allocated_path) free(allocated_path);
    return fd;
}

EXPORT int access(const char *pathname, int mode) {
    if (!my_access)
        *(void **)&my_access = dlsym(RTLD_NEXT, "access");

    char *allocated_path = nullptr;
    const char *final_path = pathname;

    if (pathname) {
        if (strstr(pathname, "/dev/input/event")) {
            allocated_path = from_real_to_fake_path(pathname);
            if (allocated_path) final_path = allocated_path;
        } else if (!strcmp(pathname, "/dev/input")) {
            final_path = hook_dir;
        }
    }

    int ret = my_access(final_path, mode);
    if (allocated_path) free(allocated_path);
    return ret;
}

EXPORT int stat(const char *pathname, struct stat *statbuf) {
    if (!my_stat)
        *(void **)&my_stat = dlsym(RTLD_NEXT, "stat");

    const char *event = nullptr;
    int event_number = -1;
    char *allocated_path = nullptr;
    const char *final_path = pathname;

    if (pathname) {
        if (strstr(pathname, "/dev/input/event")) {
            allocated_path = from_real_to_fake_path(pathname);
            if (allocated_path) {
                final_path = allocated_path;
                event = get_event(final_path);
                event_number = get_event_number(event);
            }
        } else if (!strcmp(pathname, "/dev/input")) {
            final_path = hook_dir;
        }
    }

    int ret = my_stat(final_path, statbuf);

    if (ret == 0 && event_number >= 0) {
        statbuf->st_mode = (statbuf->st_mode & ~S_IFMT) | S_IFCHR;
        statbuf->st_rdev = makedev(13, 64 + event_number);
        statbuf->st_uid = 0;
        statbuf->st_gid = 0;
    }

    if (allocated_path) free(allocated_path);
    return ret;
}

EXPORT int lstat(const char *pathname, struct stat *statbuf) {
    if (!my_lstat)
        *(void **)&my_lstat = dlsym(RTLD_NEXT, "lstat");

    const char *event = nullptr;
    int event_number = -1;
    char *allocated_path = nullptr;
    const char *final_path = pathname;

    if (pathname) {
        if (strstr(pathname, "/dev/input/event")) {
            allocated_path = from_real_to_fake_path(pathname);
            if (allocated_path) {
                final_path = allocated_path;
                event = get_event(final_path);
                event_number = get_event_number(event);
            }
        } else if (!strcmp(pathname, "/dev/input")) {
            final_path = hook_dir;
        }
    }

    int ret = my_lstat(final_path, statbuf);

    if (ret == 0 && event_number >= 0) {
        statbuf->st_mode = (statbuf->st_mode & ~S_IFMT) | S_IFCHR;
        statbuf->st_rdev = makedev(13, 64 + event_number);
        statbuf->st_uid = 0;
        statbuf->st_gid = 0;
    }

    if (allocated_path) free(allocated_path);
    return ret;
}

EXPORT int fstat(int fd, struct stat *buf) {
    if (!my_fstat)
        *(void **)&my_fstat = dlsym(RTLD_NEXT, "fstat");

    int ret = my_fstat(fd, buf);

    auto controller = controller_map.find(fd);
    if (ret == 0 && controller != controller_map.end()) {
        int event_number = get_event_number(controller->second);
        buf->st_mode = (buf->st_mode & ~S_IFMT) | S_IFCHR;
        buf->st_rdev = makedev(13, 64 + event_number);
        buf->st_uid = 0;
        buf->st_gid = 0;
    }

    return ret;
}

EXPORT int scandir(const char *dirp, struct dirent ***namelist, int(*filter)(const struct dirent *), int(*compar)(const struct dirent **, const struct dirent **)) {
    if (!my_scandir)
        *(void **)&my_scandir = dlsym(RTLD_NEXT, "scandir");

    if (dirp) {
        if (!strcmp(dirp, "/dev/input")) {
            dirp = hook_dir;
        }
    }

    return my_scandir(dirp, namelist, filter, compar);
}

EXPORT int inotify_add_watch(int fd, const char *pathname, uint32_t mask) {
    if (!my_inotify_add_watch)
        *(void **)&my_inotify_add_watch = dlsym(RTLD_NEXT, "inotify_add_watch");

    char *allocated_path = nullptr;
    const char *final_path = pathname;

    if (pathname) {
        if (strstr(pathname, "/dev/input/event")) {
            allocated_path = from_real_to_fake_path(pathname);
            if (allocated_path) final_path = allocated_path;
        } else if (!strcmp(pathname, "/dev/input")) {
            final_path = hook_dir;
        }
    }

    int ret = my_inotify_add_watch(fd, final_path, mask);
    if (allocated_path) free(allocated_path);
    return ret;
}

EXPORT int ioctl(int fd, int op, ...) {
    va_list va;
    void *argp;

    va_start(va, op);
    argp = va_arg(va, void *);
    va_end(va);

    auto controller = controller_map.find(fd);
    if (controller == controller_map.end()) {
        return syscall(SYS_ioctl, fd, op, argp);
    }

    int type = (op >> 8 & 0xFF);
    int number = (op >> 0 & 0xFF);

    if (type == 0x45 && number == 0x1) {
        int version = 65536;
        memcpy(argp, (void *)&version, sizeof(int));
        return 0;
    } else if (type == 0x45 && number == 0x2) {
        struct input_id id;
        memset(&id, 0, sizeof(id));
        id.bustype = 0x03;
        id.vendor = 0x045e; // Microsoft
        id.product = 0x028e; // Xbox 360 Controller
        id.version = 0x0110;
        memcpy(argp, (void *)&id, sizeof(id));
        return 0;
    } else if (type == 0x45 && number == 0x6) {
        strcpy((char *)argp, "Microsoft X-Box 360 pad");
        return 0;
    } else if (type == 0x45 && number == 0x9) {
        return 0;
    } else if (type == 0x45 && number == 0x18) {
        char bitmask[KEY_MAX / 8] = {0};
        memcpy(argp, (void *)&bitmask, sizeof(bitmask));
        return 0;
    } else if (type == 0x45 && number == 0x20) {
        char bitmask[EV_MAX / 8] = {0};
        bitmask[EV_SYN / 8] |= (1 << (EV_SYN % 8));
        bitmask[EV_KEY / 8] |= (1 << (EV_KEY % 8));
        bitmask[EV_ABS / 8] |= (1 << (EV_ABS % 8));
        memcpy(argp, (void *)&bitmask, sizeof(bitmask));
        return 0;
    } else if (type == 0x45 && number == 0x21) {
        char bitmask[KEY_MAX / 8] = {0};
        int buttons[] = {BTN_A, BTN_B, BTN_X, BTN_Y, BTN_TL, BTN_TR, BTN_SELECT, BTN_START, BTN_THUMBL, BTN_THUMBR, BTN_MODE, BTN_TL2, BTN_TR2};
        for (int btn : buttons) {
            bitmask[btn / 8] |= (1 << (btn % 8));
        }
        memcpy(argp, (void *)&bitmask, sizeof(bitmask));
        return 0;
    } else if (type == 0x45 && number == 0x22) {
        char bitmask[REL_MAX / 8] = {0};
        memcpy(argp, (void *)&bitmask, sizeof(bitmask));
        return 0;
    } else if (type == 0x45 && number == 0x23) {
        char bitmask[ABS_MAX / 8] = {0};
        short axes[] = {ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_Z, ABS_RZ, ABS_HAT0X, ABS_HAT0Y};
        for (short axis : axes) {
            bitmask[axis / 8] |= (1 << (axis % 8));
        }
        memcpy(argp, (void *)&bitmask, sizeof(bitmask));
        return 0;
    } else if (type == 0x45 && number == 0x35) {
        return 0;
    } else if (type == 0x45 && number >= 0x40 && number <= 0x51) {
        struct input_absinfo abs_info;
        memset(&abs_info, 0, sizeof(abs_info));
        if (number >= 0x40 && number <= 0x44) { // ABS_X to ABS_RY
            abs_info.value = 0;
            abs_info.minimum = -32768;
            abs_info.maximum = 32767;
        } else if (number == 0x42 || number == 0x45) { // ABS_Z, ABS_RZ (Triggers)
            abs_info.value = 0;
            abs_info.minimum = 0;
            abs_info.maximum = 255;
        } else if (number == 0x50 || number == 0x51) { // ABS_HAT0X, ABS_HAT0Y
            abs_info.value = 0;
            abs_info.minimum = -1;
            abs_info.maximum = 1;
        }
        memcpy(argp, (void *)&abs_info, sizeof(abs_info));
        return 0;
    }

    return syscall(SYS_ioctl, fd, op, argp);
}

EXPORT int close(int fd) {
    if (!my_close)
        *(void **)&my_close = dlsym(RTLD_NEXT, "close");

    auto it = controller_map.find(fd);
    if (it != controller_map.end()) {
        Logger::log("Closing controller, fd %d event %s\n", fd, it->second);
        free((void *)it->second);
        controller_map.erase(it);
    }

    return my_close(fd);
}

EXPORT ssize_t read(int fd, void *buf, size_t count) {
    auto controller = controller_map.find(fd);

    if (controller != controller_map.end()) {
        ssize_t bytes_read = 0;
        int flags = fcntl(fd, F_GETFL);
        bool isNonBlock = flags & O_NONBLOCK;
        
        bytes_read = syscall(SYS_read, fd, buf, count);
        
        int retries = 0;
        while (bytes_read == 0 && !isNonBlock && retries < 1000) {
            setup_signal_handler();
            if (stop_flag) {
                return -1;
            }
            usleep(10000); // 10ms
            bytes_read = syscall(SYS_read, fd, buf, count);
            retries++;
        }

        return bytes_read;
    }
    return syscall(SYS_read, fd, buf, count);
}

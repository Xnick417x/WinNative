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
static int (*my_fstat)(int fd, struct stat *buf) = nullptr;
static int (*my_scandir)(const char *, struct dirent***, int(*)(const struct dirent *), int(*)(const struct dirent**, const struct dirent**));
static int (*my_close)(int);

namespace Logger {
	int log_enabled;
	void init() { log_enabled = getenv("FAKE_EVDEV_LOG") && atoi(getenv("FAKE_EVDEV_LOG")); }
	void log(const char *message, ...) {
		if (!log_enabled) return;
		va_list args; va_start(args, message);
		vfprintf(stderr, message, args); va_end(args);
		std::cerr.flush();
	}
}

void handle_sigint(int sig) { (void)sig; stop_flag = 1; }
void setup_signal_handler() { if (!initialized) { signal(SIGINT, handle_sigint); initialized = true; } }

__attribute__((constructor))
static void library_init() {
	if (!hook_dir)
		hook_dir = getenv("FAKE_EVDEV_DIR") ? strdup(getenv("FAKE_EVDEV_DIR")) : strdup("/data/data/com.winnative.cmod/files/imagefs/dev/input");
	Logger::init();
}
  
__attribute__((visibility("hidden"))) 
char *from_real_to_fake_path(const char *pathname) {
	const char *event = strrchr(pathname, '/') + 1;
	char *fake_path;
	asprintf(&fake_path, "%s/%s", hook_dir, event);
	return fake_path;
}

__attribute__((visibility("hidden")))
const char *get_event(const char *pathname) {
	const char *event = strrchr(pathname, '/') + 1;
	return event;
}

__attribute__((visibility("hidden")))
int get_event_number(const char *event) {
    if (event && strncmp(event, "event", 5) == 0) return atoi(event + 5);
    return -1;
}

EXPORT int open(const char *pathname, int flags, ...) {
    va_list va; mode_t mode; int fd; bool hasMode = flags & O_CREAT; bool isFromInput = false;
    va_start(va, flags); if (hasMode) mode = va_arg(va, mode_t); va_end(va);
	if (!my_open) *(void **)&my_open = dlsym(RTLD_NEXT, "open");
	const char *final_path = pathname; char *allocated_path = nullptr;
	if (pathname) {
		if (strstr(pathname, "/dev/input/event")) {
		    allocated_path = from_real_to_fake_path(pathname); final_path = allocated_path; isFromInput = true;
		} else if (!strcmp(pathname, "/dev/input")) final_path = hook_dir;
	}
	fd = hasMode ? my_open(final_path, flags, mode) : my_open(final_path, flags);
	if (isFromInput && fd >= 0) controller_map[fd] = strdup(get_event(final_path));
    if (allocated_path) free(allocated_path);
	return fd;
}

EXPORT int openat(int dirfd, const char *pathname, int flags, ...) {
    va_list va; mode_t mode; int fd; bool hasMode = flags & O_CREAT; bool isFromInput = false;
    va_start(va, flags); if (hasMode) mode = va_arg(va, mode_t); va_end(va);
    if (!my_openat) *(void **)&my_openat = dlsym(RTLD_NEXT, "openat");
    const char *final_path = pathname; char *allocated_path = nullptr;
    if (pathname) {
        if (strstr(pathname, "/dev/input/event")) {
            allocated_path = from_real_to_fake_path(pathname); final_path = allocated_path; isFromInput = true;
        } else if (!strcmp(pathname, "/dev/input")) final_path = hook_dir;
    }
    fd = hasMode ? my_openat(dirfd, final_path, flags, mode) : my_openat(dirfd, final_path, flags);
    if (isFromInput && fd >= 0) controller_map[fd] = strdup(get_event(final_path));
    if (allocated_path) free(allocated_path);
    return fd;
}

EXPORT int stat(const char *pathname, struct stat *statbuf) {
	if (!my_stat) *(void **)&my_stat = dlsym(RTLD_NEXT, "stat");
    int event_number = -1; char *allocated_path = nullptr; const char *final_path = pathname;
	if (pathname) {
		if (strstr(pathname, "/dev/input/event")) {
		    allocated_path = from_real_to_fake_path(pathname); final_path = allocated_path;
		    event_number = get_event_number(get_event(final_path));
		} else if (!strcmp(pathname, "/dev/input")) final_path = hook_dir;
	}
	int ret = my_stat(final_path, statbuf);
    if (ret == 0 && event_number >= 0) statbuf->st_rdev = makedev(1, event_number);
    if (allocated_path) free(allocated_path);
	return ret;
}

EXPORT int fstat(int fd, struct stat *buf) {
	if (!my_fstat) *(void **)&my_fstat = dlsym(RTLD_NEXT, "fstat");
    int ret = my_fstat(fd, buf);
    auto it = controller_map.find(fd);
    if (ret == 0 && it != controller_map.end()) buf->st_rdev = makedev(1, get_event_number(it->second));
    return ret;
}

EXPORT int scandir(const char *dirp, struct dirent ***namelist, int(*filter)(const struct dirent *), int(*compar)(const struct dirent **, const struct dirent **)) {
	if (!my_scandir) *(void **)&my_scandir = dlsym(RTLD_NEXT, "scandir");
	if (dirp && !strcmp(dirp, "/dev/input")) dirp = hook_dir;
	return my_scandir(dirp, namelist, filter, compar);
}

EXPORT int ioctl(int fd, int op, ...) {
	va_list va; void *argp; va_start(va, op); argp = va_arg(va, void *); va_end(va);
	auto it = controller_map.find(fd);
	if (it == controller_map.end()) return syscall(SYS_ioctl, fd, op, argp);

	int type = (op >> 8 & 0xFF), number = (op >> 0 & 0xFF);
	int event_number = get_event_number(it->second);

    if (type == 0x45 && number == 0x1) {
        int version = 65536; memcpy(argp, &version, sizeof(int)); return 0;
    } else if (type == 0x45 && number == 0x2) {
        struct input_id id = {0x03, (uint16_t)(0x1234 + event_number), (uint16_t)(0x5678 + event_number), 0x0110};
        memcpy(argp, &id, sizeof(id)); return 0;
    } else if (type == 0x45 && number == 0x6) {
    	char name[128]; snprintf(name, sizeof(name), "Generic HID Gamepad %d", event_number);
    	strcpy((char *)argp, name); return 0;
    } else if (type == 0x45 && number == 0x20) {
        char bitmask[EV_MAX / 8] = {0};
        bitmask[EV_SYN / 8] |= (1 << (EV_SYN % 8)); bitmask[EV_KEY / 8] |= (1 << (EV_KEY % 8)); bitmask[EV_ABS / 8] |= (1 << (EV_ABS % 8));
    	memcpy(argp, &bitmask, sizeof(bitmask)); return 0;
    } else if (type == 0x45 && number == 0x21) {
        char bitmask[KEY_MAX / 8] = {0};
        int buttons[] = {BTN_A, BTN_B, BTN_X, BTN_Y, BTN_TL, BTN_TR, BTN_SELECT, BTN_START, BTN_THUMBL, BTN_THUMBR, BTN_MODE, BTN_TL2, BTN_TR2};
        for (int btn : buttons) bitmask[btn / 8] |= (1 << (btn % 8));
        memcpy(argp, &bitmask, sizeof(bitmask)); return 0;
    } else if (type == 0x45 && number == 0x23) {
    	char bitmask[ABS_MAX / 8] = {0};
    	short axes[] = {ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_BRAKE, ABS_GAS, ABS_HAT0X, ABS_HAT0Y};
    	for (short axis : axes) bitmask[axis / 8] |= (1 << (axis % 8));
    	memcpy(argp, &bitmask, sizeof(bitmask)); return 0;
    } else if (type == 0x45 && number >= 0x40 && number <= 0x51) {
    	struct input_absinfo abs_info = {0};
    	if (number >= 0x40 && number <= 0x44) { abs_info.minimum = -32768; abs_info.maximum = 32767; }
    	else if (number == 0x49 || number == 0x4a) { abs_info.minimum = 0; abs_info.maximum = 255; }
    	else if (number >= 0x50 && number <= 0x51) { abs_info.minimum = -1; abs_info.maximum = 1; }
    	memcpy(argp, &abs_info, sizeof(abs_info)); return 0;
    }
    return syscall(SYS_ioctl, fd, op, argp);
}

EXPORT int close(int fd) {
	if (!my_close) *(void **)&my_close = dlsym(RTLD_NEXT, "close");
	auto it = controller_map.find(fd);
	if (it != controller_map.end()) { free((void *)it->second); controller_map.erase(it); }
	return my_close(fd);
}

EXPORT ssize_t read(int fd, void *buf, size_t count) {
    auto it = controller_map.find(fd);
    if (it != controller_map.end()) {
        ssize_t bytes_read = syscall(SYS_read, fd, buf, count);
        int retries = 0;
        while(bytes_read == 0 && !(fcntl(fd, F_GETFL) & O_NONBLOCK) && retries < 1000) {
            setup_signal_handler(); if (stop_flag) return -1;
            usleep(10000); bytes_read = syscall(SYS_read, fd, buf, count); retries++;
        }
        return bytes_read;
    }
    return syscall(SYS_read, fd, buf, count);
}

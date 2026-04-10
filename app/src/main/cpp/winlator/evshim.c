/* evshim.c - Multi-Controller & Dynamic SDL Virtual Joystick Shim
 * Perfect Alignment Version - Matching Reference App & PR 151
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <sched.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>
#include <sys/vfs.h>
#include <sys/statvfs.h>

/* SDL2 types */
typedef struct SDL_Joystick SDL_Joystick;
typedef struct { int major, minor, patch; } SDL_version;
typedef struct SDL_VirtualJoystickDesc {
  uint16_t version;
  uint16_t type;
  uint16_t naxes;
  uint16_t nbuttons;
  uint16_t nhats;
  uint16_t vendor_id;
  uint16_t product_id;
  uint16_t padding;
  uint32_t button_mask;
  uint32_t axis_mask;
  const char *name;
  void *userdata;
  void (*Update)(void *);
  void (*SetPlayerIndex)(void *, int);
  int (*Rumble)(void *, uint16_t, uint16_t);
  int (*RumbleTriggers)(void *, uint16_t, uint16_t);
  int (*SetLED)(void *, uint8_t, uint8_t, uint8_t);
  int (*SendEffect)(void *, const void *, int);
} SDL_VirtualJoystickDesc;

#define SDL_VIRTUAL_JOYSTICK_DESC_VERSION 1
#define SDL_JOYSTICK_TYPE_GAMECONTROLLER 1
#define SDL_INIT_JOYSTICK 0x00000200

#define LOGI(...) dprintf(STDOUT_FILENO, "EVSHIM: " __VA_ARGS__)
#define LOGE(...) dprintf(STDERR_FILENO, "EVSHIM ERROR: " __VA_ARGS__)

#define MAX_GAMEPADS 4
#define GAMEPAD_MEM_SIZE 64
#define GAMEPAD_VENDOR_ID 0x1234
#define GAMEPAD_PRODUCT_ID 0x5678
#define GAMEPAD_NAME_TEMPLATE "Generic HID Gamepad %d"

/* Shared memory layout - PACKED to match Java ByteBuffer exactly (Fix #1) */
struct __attribute__((packed)) gamepad_io {
  int16_t lx, ly, rx, ry, lt, rt; /* 0-11: axes */
  uint8_t btn[15];                /* 12-26: buttons */
  uint8_t hat;                    /* 27: hat/dpad */
  uint8_t _padding[4];            /* 28-31: padding */
  uint16_t low_freq_rumble;       /* 32-33: rumble out */
  uint16_t high_freq_rumble;      /* 34-35: rumble out */
};

struct controller_state {
  SDL_Joystick *js;
  volatile struct gamepad_io *mem;
  int mem_fd;
  int16_t last_axes[6];
  uint8_t last_btns[15];
  uint8_t last_hat;
  int active;
};

static struct controller_state ctrl[MAX_GAMEPADS] = {0};
static int vjoy_ids[MAX_GAMEPADS] = {-1, -1, -1, -1};
static void *handle = NULL;
static int g_num_players = 1;
static char g_data_path[256] = {0};

/* SDL function pointers */
static int (*p_SDL_Init)(uint32_t);
static SDL_Joystick *(*p_SDL_JoystickOpen)(int);
static int (*p_SDL_JoystickAttachVirtualEx)(const SDL_VirtualJoystickDesc *);
static int (*p_SDL_JoystickSetVirtualAxis)(SDL_Joystick *, int, int16_t);
static int (*p_SDL_JoystickSetVirtualButton)(SDL_Joystick *, int, uint8_t);
static int (*p_SDL_JoystickSetVirtualHat)(SDL_Joystick *, int, uint8_t);
static void (*p_SDL_GetVersion)(SDL_version *);

#define GETFUNCPTR(name)                                                       \
  do {                                                                         \
    if (!(p_##name = (typeof(p_##name))dlsym(handle, #name)))                  \
      LOGE("Failed to load SDL: %s\n", #name);                                 \
  } while (0)

/* Portable atomic operations - use builtins if available, else volatile */
#if defined(__GNUC__) || defined(__clang__)
#define ATOMIC_LOAD(ptr) __atomic_load_n(ptr, __ATOMIC_ACQUIRE)
#define ATOMIC_STORE(ptr, val) __atomic_store_n(ptr, val, __ATOMIC_RELEASE)
#else
#define ATOMIC_LOAD(ptr) (*(volatile typeof(*(ptr)) *)(ptr))
#define ATOMIC_STORE(ptr, val)                                                 \
  do {                                                                         \
    *(volatile typeof(*(ptr)) *)(ptr) = (val);                                 \
    __asm__ __volatile__("" ::: "memory");                                     \
  } while (0)
#endif

#define AXIS_DEADZONE 256
static inline int16_t apply_deadzone(int16_t val) {
  int16_t abs_val = val < 0 ? -val : val;
  return abs_val < AXIS_DEADZONE ? 0 : val;
}

static int OnRumble(void *userdata, uint16_t low, uint16_t high) {
  int idx = (int)(intptr_t)userdata;
  if (idx >= 0 && idx < MAX_GAMEPADS && ctrl[idx].mem) {
    volatile struct gamepad_io *mem = ctrl[idx].mem;
    ATOMIC_STORE(&mem->low_freq_rumble, low);
    ATOMIC_STORE(&mem->high_freq_rumble, high);
  }
  return 0;
}

static void *unified_updater(void *arg) {
  (void)arg;
  struct timespec fast_sleep = {0, 500000L};
  struct timespec slow_sleep = {0, 4000000L};
  int idle_count = 0;

  for (int i = 0; i < g_num_players; i++) {
    if (vjoy_ids[i] < 0 || !ctrl[i].mem) continue;
    ctrl[i].js = p_SDL_JoystickOpen(vjoy_ids[i]);
    if (!ctrl[i].js) continue;
    ctrl[i].active = 1;
  }

  for (;;) {
    int had_updates = 0;
    for (int i = 0; i < g_num_players; i++) {
      if (!ctrl[i].active) continue;

      volatile struct gamepad_io *mem = ctrl[i].mem;
      SDL_Joystick *js = ctrl[i].js;

      int16_t axes[6];
      axes[0] = apply_deadzone(ATOMIC_LOAD(&mem->lx));
      axes[1] = apply_deadzone(ATOMIC_LOAD(&mem->ly));
      axes[2] = apply_deadzone(ATOMIC_LOAD(&mem->rx));
      axes[3] = apply_deadzone(ATOMIC_LOAD(&mem->ry));
      axes[4] = ATOMIC_LOAD(&mem->lt); 
      axes[5] = ATOMIC_LOAD(&mem->rt);

      for (int a = 0; a < 6; a++) {
        if (axes[a] != ctrl[i].last_axes[a]) {
          p_SDL_JoystickSetVirtualAxis(js, a, axes[a]);
          ctrl[i].last_axes[a] = axes[a];
          had_updates = 1;
        }
      }

      for (int b = 0; b < 15; b++) {
        uint8_t btn = ATOMIC_LOAD(&mem->btn[b]);
        if (btn != ctrl[i].last_btns[b]) {
          p_SDL_JoystickSetVirtualButton(js, b, btn);
          ctrl[i].last_btns[b] = btn;
          had_updates = 1;
        }
      }

      uint8_t hat = ATOMIC_LOAD(&mem->hat);
      if (hat != ctrl[i].last_hat) {
        p_SDL_JoystickSetVirtualHat(js, 0, hat);
        ctrl[i].last_hat = hat;
        had_updates = 1;
      }
    }

    if (had_updates) {
      idle_count = 0;
      nanosleep(&fast_sleep, NULL);
    } else {
      idle_count++;
      if (idle_count > 50) {
        nanosleep(&slow_sleep, NULL);
      } else {
        nanosleep(&fast_sleep, NULL);
      }
    }
  }
  return NULL;
}

static void *watchdog_thread(void *arg) {
  (void)arg;
  struct timespec check_interval = {1, 0}; 

  while (1) {
    pthread_t tid;
    if (pthread_create(&tid, NULL, unified_updater, NULL) != 0) {
      nanosleep(&check_interval, NULL);
      continue;
    }
    void *retval;
    pthread_join(tid, &retval);
    nanosleep(&check_interval, NULL);
  }
  return NULL;
}

static void try_attach_controller(int idx) {
  if (ctrl[idx].active || !handle) return;

  char path[300];
  snprintf(path, sizeof path, "%s/gamepad%s.mem", g_data_path,
           (idx == 0) ? "" : (char[2]){'0' + idx, '\0'});

  if (access(path, F_OK) != 0) return;
  int fd = open(path, O_RDWR);
  if (fd < 0) return;

  void *mem = mmap(NULL, GAMEPAD_MEM_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
  if (mem == MAP_FAILED) {
    close(fd);
    return;
  }

  SDL_VirtualJoystickDesc d = {0};
  d.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
  d.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
  d.naxes = 6; d.nbuttons = 15; d.nhats = 1;
  d.vendor_id = GAMEPAD_VENDOR_ID; d.product_id = GAMEPAD_PRODUCT_ID;
  d.Rumble = &OnRumble; d.userdata = (void *)(intptr_t)idx;
  
  char name[64]; snprintf(name, sizeof(name), GAMEPAD_NAME_TEMPLATE, idx);
  d.name = strdup(name);

  int vjoy_id = p_SDL_JoystickAttachVirtualEx(&d);
  if (vjoy_id < 0) {
    munmap(mem, GAMEPAD_MEM_SIZE);
    close(fd);
    return;
  }

  SDL_Joystick *js = p_SDL_JoystickOpen(vjoy_id);
  if (!js) {
    munmap(mem, GAMEPAD_MEM_SIZE);
    close(fd);
    return;
  }

  ctrl[idx].mem_fd = fd;
  ctrl[idx].mem = (volatile struct gamepad_io *)mem;
  ctrl[idx].js = js;
  vjoy_ids[idx] = vjoy_id;
  ctrl[idx].active = 1;

  if (idx >= g_num_players) g_num_players = idx + 1;
}

static void *hotplug_thread(void *arg) {
  (void)arg;
  struct timespec interval = {2, 0};
  while (1) {
    nanosleep(&interval, NULL);
    for (int i = 0; i < MAX_GAMEPADS; i++) {
      if (!ctrl[i].active) try_attach_controller(i);
    }
  }
  return NULL;
}

__attribute__((constructor)) static void initialize_all_pads(void) {
  const char *libs[] = {"libSDL2-2.0.so.0", "libSDL2-2.0.so", "libSDL2.so", NULL};
  for (int i = 0; libs[i] && !handle; i++) handle = dlopen(libs[i], RTLD_LAZY | RTLD_GLOBAL);
  if (!handle) return;

  GETFUNCPTR(SDL_Init);
  GETFUNCPTR(SDL_JoystickOpen);
  GETFUNCPTR(SDL_JoystickAttachVirtualEx);
  GETFUNCPTR(SDL_JoystickSetVirtualAxis);
  GETFUNCPTR(SDL_JoystickSetVirtualButton);
  GETFUNCPTR(SDL_JoystickSetVirtualHat);
  GETFUNCPTR(SDL_GetVersion);

  if (!p_SDL_Init || !p_SDL_JoystickAttachVirtualEx) return;
  p_SDL_Init(SDL_INIT_JOYSTICK);

  const char *data_path = getenv("EVSHIM_DATA_PATH") ?: "/data/data/com.winnative.cmod/files/imagefs/tmp";
  strncpy(g_data_path, data_path, sizeof(g_data_path) - 1);
  g_num_players = getenv("EVSHIM_MAX_PLAYERS") ? atoi(getenv("EVSHIM_MAX_PLAYERS")) : 1;
  if (g_num_players > MAX_GAMEPADS) g_num_players = MAX_GAMEPADS;

  int attached = 0;
  for (int i = 0; i < g_num_players; ++i) {
    char path[256];
    snprintf(path, sizeof path, "%s/gamepad%s.mem", data_path,
             (i == 0) ? "" : (char[2]){'0' + i, '\0'});

    int fd = open(path, O_RDWR);
    if (fd < 0) continue;

    void *mem = mmap(NULL, GAMEPAD_MEM_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (mem == MAP_FAILED) {
      close(fd);
      continue;
    }

    SDL_VirtualJoystickDesc d = {0};
    d.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
    d.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
    d.naxes = 6; d.nbuttons = 15; d.nhats = 1;
    d.vendor_id = GAMEPAD_VENDOR_ID; d.product_id = GAMEPAD_PRODUCT_ID;
    d.Rumble = &OnRumble; d.userdata = (void *)(intptr_t)i;
    
    char name[64]; snprintf(name, sizeof(name), GAMEPAD_NAME_TEMPLATE, i);
    d.name = strdup(name);

    int vjoy_id = p_SDL_JoystickAttachVirtualEx(&d);
    if (vjoy_id < 0) {
      munmap(mem, GAMEPAD_MEM_SIZE);
      close(fd);
      continue;
    }
    
    SDL_Joystick *js = p_SDL_JoystickOpen(vjoy_id);
    if (!js) {
      munmap(mem, GAMEPAD_MEM_SIZE);
      close(fd);
      continue;
    }
    
    ctrl[i].mem_fd = fd;
    ctrl[i].mem = (volatile struct gamepad_io *)mem;
    ctrl[i].js = js;
    vjoy_ids[i] = vjoy_id;
    ctrl[i].active = 1;
    attached++;
  }

  if (attached > 0) {
    pthread_t watchdog_tid;
    pthread_create(&watchdog_tid, NULL, watchdog_thread, NULL);
    pthread_detach(watchdog_tid);
  }

  pthread_t hotplug_tid;
  pthread_create(&hotplug_tid, NULL, hotplug_thread, NULL);
  pthread_detach(hotplug_tid);
}

/* FUSE Bypass Hooks */
static int (*real_statfs)(const char*, struct statfs*);
static int (*real_statfs64)(const char*, struct statfs64*);
static int (*real_statvfs)(const char*, struct statvfs*);
static int (*real_statvfs64)(const char*, struct statvfs64*);
static int (*real_fstatfs)(int, struct statfs*);
static int (*real_fstatfs64)(int, struct statfs64*);
static int (*real_fstatvfs)(int, struct statvfs*);
static int (*real_fstatvfs64)(int, struct statvfs64*);

#ifndef ST_NOEXEC
#define ST_NOEXEC 8
#endif

__attribute__((visibility("default")))
int statfs(const char *path, struct statfs *buf) {
    if (!real_statfs) real_statfs = dlsym(RTLD_NEXT, "statfs");
    int res = real_statfs(path, buf);
    if (res == 0) buf->f_type = 0xEF53;
    return res;
}

__attribute__((visibility("default")))
int statfs64(const char *path, struct statfs64 *buf) {
    if (!real_statfs64) real_statfs64 = dlsym(RTLD_NEXT, "statfs64");
    int res = real_statfs64(path, buf);
    if (res == 0) buf->f_type = 0xEF53;
    return res;
}

__attribute__((visibility("default")))
int statvfs(const char *path, struct statvfs *buf) {
    if (!real_statvfs) real_statvfs = dlsym(RTLD_NEXT, "statvfs");
    int res = real_statvfs(path, buf);
    if (res == 0) buf->f_flag &= ~ST_NOEXEC;
    return res;
}

__attribute__((visibility("default")))
int statvfs64(const char *path, struct statvfs64 *buf) {
    if (!real_statvfs64) real_statvfs64 = dlsym(RTLD_NEXT, "statvfs64");
    int res = real_statvfs64(path, buf);
    if (res == 0) buf->f_flag &= ~ST_NOEXEC;
    return res;
}

__attribute__((visibility("default")))
int fstatfs(int fd, struct statfs *buf) {
    if (!real_fstatfs) real_fstatfs = dlsym(RTLD_NEXT, "fstatfs");
    int res = real_fstatfs(fd, buf);
    if (res == 0) buf->f_type = 0xEF53;
    return res;
}

__attribute__((visibility("default")))
int fstatfs64(int fd, struct statfs64 *buf) {
    if (!real_fstatfs64) real_fstatfs64 = dlsym(RTLD_NEXT, "fstatfs64");
    int res = real_fstatfs64(fd, buf);
    if (res == 0) buf->f_type = 0xEF53;
    return res;
}

__attribute__((visibility("default")))
int fstatvfs(int fd, struct statvfs *buf) {
    if (!real_fstatvfs) real_fstatvfs = dlsym(RTLD_NEXT, "fstatvfs");
    int res = real_fstatvfs(fd, buf);
    if (res == 0) buf->f_flag &= ~ST_NOEXEC;
    return res;
}

__attribute__((visibility("default")))
int fstatvfs64(int fd, struct statvfs64 *buf) {
    if (!real_fstatvfs64) real_fstatvfs64 = dlsym(RTLD_NEXT, "fstatvfs64");
    int res = real_fstatvfs64(fd, buf);
    if (res == 0) buf->f_flag &= ~ST_NOEXEC;
    return res;
}
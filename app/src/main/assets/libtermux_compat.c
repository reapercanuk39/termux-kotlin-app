/*
 * libtermux_compat.so - LD_PRELOAD shim for Termux-Kotlin compatibility
 * 
 * This library intercepts filesystem syscalls and redirects paths from
 * /data/data/com.termux/ to /data/data/com.termux.kotlin/
 * 
 * This allows upstream Termux packages with hardcoded paths to work
 * correctly in the Termux-Kotlin environment.
 * 
 * Compile with:
 *   $CC -shared -fPIC -O2 -o libtermux_compat.so libtermux_compat.c -ldl
 * 
 * Use with:
 *   export LD_PRELOAD=$PREFIX/lib/libtermux_compat.so
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <errno.h>

/* Path prefixes */
#define OLD_PREFIX "/data/data/com.termux/"
#define NEW_PREFIX "/data/data/com.termux.kotlin/"
#define OLD_PREFIX_LEN 23
#define NEW_PREFIX_LEN 30

/* Maximum path length */
#define MAX_PATH 4096

/* Thread-local buffer for path rewriting (avoids malloc in syscall path) */
static __thread char rewritten_path[MAX_PATH];

/* Debug logging (disabled by default, enable with TERMUX_COMPAT_DEBUG=1) */
static int debug_enabled = -1;

static void debug_log(const char *fmt, ...) {
    if (debug_enabled == -1) {
        const char *env = getenv("TERMUX_COMPAT_DEBUG");
        debug_enabled = (env && env[0] == '1') ? 1 : 0;
    }
    if (debug_enabled) {
        va_list args;
        va_start(args, fmt);
        fprintf(stderr, "[termux-compat] ");
        vfprintf(stderr, fmt, args);
        fprintf(stderr, "\n");
        va_end(args);
    }
}

/*
 * Rewrite path if it starts with the old prefix.
 * Returns pointer to rewritten path (thread-local buffer) or original path.
 */
static const char *maybe_rewrite_path(const char *path) {
    if (path == NULL) {
        return NULL;
    }
    
    /* Check if path starts with old prefix */
    if (strncmp(path, OLD_PREFIX, OLD_PREFIX_LEN) == 0) {
        size_t suffix_len = strlen(path + OLD_PREFIX_LEN);
        
        /* Ensure we don't overflow buffer */
        if (NEW_PREFIX_LEN + suffix_len + 1 > MAX_PATH) {
            debug_log("path too long, not rewriting: %s", path);
            return path;
        }
        
        /* Build rewritten path */
        memcpy(rewritten_path, NEW_PREFIX, NEW_PREFIX_LEN);
        memcpy(rewritten_path + NEW_PREFIX_LEN, path + OLD_PREFIX_LEN, suffix_len + 1);
        
        debug_log("rewrite: %s -> %s", path, rewritten_path);
        return rewritten_path;
    }
    
    return path;
}

/* Function pointer types for real syscalls */
typedef int (*open_fn)(const char *, int, ...);
typedef int (*openat_fn)(int, const char *, int, ...);
typedef int (*stat_fn)(const char *, struct stat *);
typedef int (*lstat_fn)(const char *, struct stat *);
typedef int (*access_fn)(const char *, int);
typedef ssize_t (*readlink_fn)(const char *, char *, size_t);
typedef int (*execve_fn)(const char *, char *const[], char *const[]);
typedef FILE *(*fopen_fn)(const char *, const char *);
typedef int (*rename_fn)(const char *, const char *);
typedef int (*unlink_fn)(const char *);
typedef int (*mkdir_fn)(const char *, mode_t);
typedef int (*rmdir_fn)(const char *);
typedef int (*chdir_fn)(const char *);
typedef int (*chmod_fn)(const char *, mode_t);
typedef int (*chown_fn)(const char *, uid_t, gid_t);
typedef int (*link_fn)(const char *, const char *);
typedef int (*symlink_fn)(const char *, const char *);

/* Cached function pointers */
static open_fn real_open = NULL;
static openat_fn real_openat = NULL;
static stat_fn real_stat = NULL;
static lstat_fn real_lstat = NULL;
static access_fn real_access = NULL;
static readlink_fn real_readlink = NULL;
static execve_fn real_execve = NULL;
static fopen_fn real_fopen = NULL;
static rename_fn real_rename = NULL;
static unlink_fn real_unlink = NULL;
static mkdir_fn real_mkdir = NULL;
static rmdir_fn real_rmdir = NULL;
static chdir_fn real_chdir = NULL;
static chmod_fn real_chmod = NULL;
static chown_fn real_chown = NULL;
static link_fn real_link = NULL;
static symlink_fn real_symlink = NULL;

/* Initialize function pointers */
static void init_real_functions(void) {
    if (real_open == NULL) {
        real_open = (open_fn)dlsym(RTLD_NEXT, "open");
        real_openat = (openat_fn)dlsym(RTLD_NEXT, "openat");
        real_stat = (stat_fn)dlsym(RTLD_NEXT, "stat");
        real_lstat = (lstat_fn)dlsym(RTLD_NEXT, "lstat");
        real_access = (access_fn)dlsym(RTLD_NEXT, "access");
        real_readlink = (readlink_fn)dlsym(RTLD_NEXT, "readlink");
        real_execve = (execve_fn)dlsym(RTLD_NEXT, "execve");
        real_fopen = (fopen_fn)dlsym(RTLD_NEXT, "fopen");
        real_rename = (rename_fn)dlsym(RTLD_NEXT, "rename");
        real_unlink = (unlink_fn)dlsym(RTLD_NEXT, "unlink");
        real_mkdir = (mkdir_fn)dlsym(RTLD_NEXT, "mkdir");
        real_rmdir = (rmdir_fn)dlsym(RTLD_NEXT, "rmdir");
        real_chdir = (chdir_fn)dlsym(RTLD_NEXT, "chdir");
        real_chmod = (chmod_fn)dlsym(RTLD_NEXT, "chmod");
        real_chown = (chown_fn)dlsym(RTLD_NEXT, "chown");
        real_link = (link_fn)dlsym(RTLD_NEXT, "link");
        real_symlink = (symlink_fn)dlsym(RTLD_NEXT, "symlink");
    }
}

/* ============================================================
 * INTERCEPTED FUNCTIONS
 * ============================================================ */

int open(const char *pathname, int flags, ...) {
    init_real_functions();
    const char *real_path = maybe_rewrite_path(pathname);
    
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode_t mode = va_arg(args, mode_t);
        va_end(args);
        return real_open(real_path, flags, mode);
    }
    return real_open(real_path, flags);
}

int openat(int dirfd, const char *pathname, int flags, ...) {
    init_real_functions();
    const char *real_path = maybe_rewrite_path(pathname);
    
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode_t mode = va_arg(args, mode_t);
        va_end(args);
        return real_openat(dirfd, real_path, flags, mode);
    }
    return real_openat(dirfd, real_path, flags);
}

int stat(const char *pathname, struct stat *statbuf) {
    init_real_functions();
    return real_stat(maybe_rewrite_path(pathname), statbuf);
}

int lstat(const char *pathname, struct stat *statbuf) {
    init_real_functions();
    return real_lstat(maybe_rewrite_path(pathname), statbuf);
}

int access(const char *pathname, int mode) {
    init_real_functions();
    return real_access(maybe_rewrite_path(pathname), mode);
}

ssize_t readlink(const char *pathname, char *buf, size_t bufsiz) {
    init_real_functions();
    return real_readlink(maybe_rewrite_path(pathname), buf, bufsiz);
}

int execve(const char *pathname, char *const argv[], char *const envp[]) {
    init_real_functions();
    return real_execve(maybe_rewrite_path(pathname), argv, envp);
}

FILE *fopen(const char *pathname, const char *mode) {
    init_real_functions();
    return real_fopen(maybe_rewrite_path(pathname), mode);
}

int rename(const char *oldpath, const char *newpath) {
    init_real_functions();
    return real_rename(maybe_rewrite_path(oldpath), maybe_rewrite_path(newpath));
}

int unlink(const char *pathname) {
    init_real_functions();
    return real_unlink(maybe_rewrite_path(pathname));
}

int mkdir(const char *pathname, mode_t mode) {
    init_real_functions();
    return real_mkdir(maybe_rewrite_path(pathname), mode);
}

int rmdir(const char *pathname) {
    init_real_functions();
    return real_rmdir(maybe_rewrite_path(pathname));
}

int chdir(const char *path) {
    init_real_functions();
    return real_chdir(maybe_rewrite_path(path));
}

int chmod(const char *pathname, mode_t mode) {
    init_real_functions();
    return real_chmod(maybe_rewrite_path(pathname), mode);
}

int chown(const char *pathname, uid_t owner, gid_t group) {
    init_real_functions();
    return real_chown(maybe_rewrite_path(pathname), owner, group);
}

int link(const char *oldpath, const char *newpath) {
    init_real_functions();
    return real_link(maybe_rewrite_path(oldpath), maybe_rewrite_path(newpath));
}

int symlink(const char *target, const char *linkpath) {
    init_real_functions();
    /* Note: target is NOT rewritten - symlinks should point to real paths */
    return real_symlink(target, maybe_rewrite_path(linkpath));
}

/* Constructor - runs when library is loaded */
__attribute__((constructor))
static void compat_init(void) {
    init_real_functions();
    debug_log("libtermux_compat.so loaded");
}

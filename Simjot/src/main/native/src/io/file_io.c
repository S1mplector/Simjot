/**
 * File I/O helpers for atomic writes and space checks.
 */

#include "simjot_native.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef _WIN32
#include <unistd.h>
#include <fcntl.h>
#include <sys/statvfs.h>
#endif

#ifndef _WIN32
static char* simjot_strdup(const char* s) {
    size_t len = strlen(s);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    memcpy(out, s, len + 1);
    return out;
}

static char* simjot_parent_dir(const char* path) {
    if (!path || *path == '\0') return NULL;
    const char* slash = strrchr(path, '/');
    if (!slash) return simjot_strdup(".");
    if (slash == path) return simjot_strdup("/");
    size_t len = (size_t)(slash - path);
    char* out = (char*)malloc(len + 1);
    if (!out) return NULL;
    memcpy(out, path, len);
    out[len] = '\0';
    return out;
}

static const char* simjot_basename(const char* path) {
    if (!path) return NULL;
    const char* slash = strrchr(path, '/');
    return slash ? slash + 1 : path;
}

int32_t simjot_atomic_write(const char* target_path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir) {
    if (!target_path || !data || data_len < 0) return 0;
    const char* base = simjot_basename(target_path);
    if (!base || *base == '\0') return 0;

    char* dir = simjot_parent_dir(target_path);
    if (!dir) return 0;

    size_t template_len = strlen(dir) + strlen(base) + 12;
    char* tmp = (char*)malloc(template_len + 1);
    if (!tmp) {
        free(dir);
        return 0;
    }
    snprintf(tmp, template_len + 1, "%s/.%s.tmpXXXXXX", dir, base);

    int fd = mkstemp(tmp);
    if (fd < 0) {
        free(tmp);
        free(dir);
        return 0;
    }

    const uint8_t* p = data;
    int32_t remaining = data_len;
    while (remaining > 0) {
        ssize_t n = write(fd, p, (size_t)remaining);
        if (n < 0) {
            close(fd);
            unlink(tmp);
            free(tmp);
            free(dir);
            return 0;
        }
        p += n;
        remaining -= (int32_t)n;
    }

    if (fsync_file) {
        (void)fsync(fd);
    }
    if (close(fd) != 0) {
        unlink(tmp);
        free(tmp);
        free(dir);
        return 0;
    }

    if (rename(tmp, target_path) != 0) {
        unlink(tmp);
        free(tmp);
        free(dir);
        return 0;
    }

    if (fsync_dir) {
        int dfd = open(dir, O_RDONLY);
        if (dfd >= 0) {
            (void)fsync(dfd);
            close(dfd);
        }
    }

    free(tmp);
    free(dir);
    return 1;
}

int32_t simjot_ensure_space(const char* path, uint64_t bytes_needed) {
    if (!path) return -1;
    struct statvfs fs;
    if (statvfs(path, &fs) != 0) {
        char* dir = simjot_parent_dir(path);
        if (!dir) return -1;
        int ok = statvfs(dir, &fs);
        free(dir);
        if (ok != 0) return -1;
    }
    unsigned long long block = fs.f_frsize ? fs.f_frsize : fs.f_bsize;
    unsigned long long avail = (unsigned long long)fs.f_bavail * block;
    return (avail < bytes_needed) ? 0 : 1;
}
#else
int32_t simjot_atomic_write(const char* target_path, const uint8_t* data, int32_t data_len, int32_t fsync_file, int32_t fsync_dir) {
    (void)target_path;
    (void)data;
    (void)data_len;
    (void)fsync_file;
    (void)fsync_dir;
    return 0;
}

int32_t simjot_ensure_space(const char* path, uint64_t bytes_needed) {
    (void)path;
    (void)bytes_needed;
    return -1;
}
#endif

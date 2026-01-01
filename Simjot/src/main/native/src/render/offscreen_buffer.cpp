/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

/**
 * @file offscreen_buffer.cpp
 * @brief Native double-buffering for smooth scrolling
 *
 * High-performance offscreen buffer management:
 * - SIMD-optimized memory operations where available
 * - Efficient scroll with memmove (avoids per-pixel copy)
 * - Alpha-blended compositing for overlay content
 * - Thread-safe handle management
 *
 * @author S1mplector
 * @copyright 2024-2025 Ilgaz Mehmetoğlu. All Rights Reserved.
 */

#include "simjot_native.h"

#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <mutex>
#include <unordered_map>
#include <atomic>

/* ═══════════════════════════════════════════════════════════════════════════
 * BUFFER STRUCTURE
 * ═══════════════════════════════════════════════════════════════════════════ */

struct OffscreenBuffer {
    uint32_t* pixels;
    int32_t width;
    int32_t height;
    size_t capacity; // in pixels, for resize optimization
};

/* ═══════════════════════════════════════════════════════════════════════════
 * GLOBAL STATE
 * ═══════════════════════════════════════════════════════════════════════════ */

static std::mutex g_buffer_mutex;
static std::unordered_map<int64_t, OffscreenBuffer*> g_buffers;
static std::atomic<int64_t> g_next_handle{1};

/* ═══════════════════════════════════════════════════════════════════════════
 * HELPER FUNCTIONS
 * ═══════════════════════════════════════════════════════════════════════════ */

static OffscreenBuffer* get_buffer(int64_t handle) {
    auto it = g_buffers.find(handle);
    return (it != g_buffers.end()) ? it->second : nullptr;
}

// Fast alpha blend: dst = src over dst
static inline uint32_t alpha_blend(uint32_t src, uint32_t dst) {
    uint32_t sa = (src >> 24) & 0xFF;
    if (sa == 0) return dst;
    if (sa == 255) return src;
    
    uint32_t da = (dst >> 24) & 0xFF;
    uint32_t sr = (src >> 16) & 0xFF;
    uint32_t sg = (src >> 8) & 0xFF;
    uint32_t sb = src & 0xFF;
    uint32_t dr = (dst >> 16) & 0xFF;
    uint32_t dg = (dst >> 8) & 0xFF;
    uint32_t db = dst & 0xFF;
    
    uint32_t inv_sa = 255 - sa;
    uint32_t out_a = sa + ((da * inv_sa) >> 8);
    uint32_t out_r = ((sr * sa) + (dr * inv_sa)) >> 8;
    uint32_t out_g = ((sg * sa) + (dg * inv_sa)) >> 8;
    uint32_t out_b = ((sb * sa) + (db * inv_sa)) >> 8;
    
    return (out_a << 24) | (out_r << 16) | (out_g << 8) | out_b;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * PUBLIC API
 * ═══════════════════════════════════════════════════════════════════════════ */

extern "C" {

int64_t simjot_buffer_create(int32_t width, int32_t height) {
    if (width <= 0 || height <= 0 || width > 16384 || height > 16384) {
        return 0;
    }
    
    size_t pixel_count = (size_t)width * (size_t)height;
    uint32_t* pixels = (uint32_t*)calloc(pixel_count, sizeof(uint32_t));
    if (!pixels) return 0;
    
    OffscreenBuffer* buf = new (std::nothrow) OffscreenBuffer();
    if (!buf) {
        free(pixels);
        return 0;
    }
    
    buf->pixels = pixels;
    buf->width = width;
    buf->height = height;
    buf->capacity = pixel_count;
    
    int64_t handle = g_next_handle.fetch_add(1);
    
    {
        std::lock_guard<std::mutex> lock(g_buffer_mutex);
        g_buffers[handle] = buf;
    }
    
    return handle;
}

int32_t simjot_buffer_resize(int64_t handle, int32_t width, int32_t height) {
    if (width <= 0 || height <= 0 || width > 16384 || height > 16384) {
        return 0;
    }
    
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    size_t new_count = (size_t)width * (size_t)height;
    
    if (new_count > buf->capacity) {
        // Need to reallocate
        uint32_t* new_pixels = (uint32_t*)calloc(new_count, sizeof(uint32_t));
        if (!new_pixels) return 0;
        
        // Copy existing content (as much as fits)
        int32_t copy_w = (width < buf->width) ? width : buf->width;
        int32_t copy_h = (height < buf->height) ? height : buf->height;
        
        for (int32_t y = 0; y < copy_h; y++) {
            memcpy(new_pixels + y * width,
                   buf->pixels + y * buf->width,
                   copy_w * sizeof(uint32_t));
        }
        
        free(buf->pixels);
        buf->pixels = new_pixels;
        buf->capacity = new_count;
    } else {
        // Reuse existing allocation, just clear if growing
        if (width > buf->width || height > buf->height) {
            memset(buf->pixels, 0, new_count * sizeof(uint32_t));
            
            // Copy existing content
            int32_t copy_w = (width < buf->width) ? width : buf->width;
            int32_t copy_h = (height < buf->height) ? height : buf->height;
            
            // Temporary copy for overlap safety
            uint32_t* temp = (uint32_t*)malloc(copy_w * copy_h * sizeof(uint32_t));
            if (temp) {
                for (int32_t y = 0; y < copy_h; y++) {
                    memcpy(temp + y * copy_w,
                           buf->pixels + y * buf->width,
                           copy_w * sizeof(uint32_t));
                }
                for (int32_t y = 0; y < copy_h; y++) {
                    memcpy(buf->pixels + y * width,
                           temp + y * copy_w,
                           copy_w * sizeof(uint32_t));
                }
                free(temp);
            }
        }
    }
    
    buf->width = width;
    buf->height = height;
    return 1;
}

void simjot_buffer_destroy(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    auto it = g_buffers.find(handle);
    if (it != g_buffers.end()) {
        OffscreenBuffer* buf = it->second;
        free(buf->pixels);
        delete buf;
        g_buffers.erase(it);
    }
}

void simjot_buffer_clear(int64_t handle, uint32_t argb) {
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return;
    
    size_t count = (size_t)buf->width * (size_t)buf->height;
    
    if (argb == 0) {
        memset(buf->pixels, 0, count * sizeof(uint32_t));
    } else {
        // Fill with color
        uint32_t* p = buf->pixels;
        for (size_t i = 0; i < count; i++) {
            p[i] = argb;
        }
    }
}

int32_t simjot_buffer_write(int64_t handle, const int32_t* pixels,
                            int32_t src_width, int32_t src_height,
                            int32_t dst_x, int32_t dst_y) {
    if (!pixels || src_width <= 0 || src_height <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    // Clip to buffer bounds
    int32_t start_x = (dst_x < 0) ? -dst_x : 0;
    int32_t start_y = (dst_y < 0) ? -dst_y : 0;
    int32_t end_x = src_width;
    int32_t end_y = src_height;
    
    if (dst_x + end_x > buf->width) end_x = buf->width - dst_x;
    if (dst_y + end_y > buf->height) end_y = buf->height - dst_y;
    
    if (start_x >= end_x || start_y >= end_y) return 1; // Nothing to copy
    
    int32_t copy_width = end_x - start_x;
    
    for (int32_t y = start_y; y < end_y; y++) {
        int32_t src_row = y;
        int32_t dst_row = dst_y + y;
        if (dst_row < 0 || dst_row >= buf->height) continue;
        
        const int32_t* src = pixels + src_row * src_width + start_x;
        uint32_t* dst = buf->pixels + dst_row * buf->width + dst_x + start_x;
        
        memcpy(dst, src, copy_width * sizeof(uint32_t));
    }
    
    return 1;
}

int32_t simjot_buffer_read(int64_t handle, int32_t* out_pixels,
                           int32_t src_x, int32_t src_y,
                           int32_t width, int32_t height) {
    if (!out_pixels || width <= 0 || height <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    // Clip to buffer bounds
    int32_t start_x = (src_x < 0) ? -src_x : 0;
    int32_t start_y = (src_y < 0) ? -src_y : 0;
    int32_t end_x = width;
    int32_t end_y = height;
    
    if (src_x + end_x > buf->width) end_x = buf->width - src_x;
    if (src_y + end_y > buf->height) end_y = buf->height - src_y;
    
    // Clear output first (for out-of-bounds areas)
    memset(out_pixels, 0, (size_t)width * (size_t)height * sizeof(int32_t));
    
    if (start_x >= end_x || start_y >= end_y) return 1;
    
    int32_t copy_width = end_x - start_x;
    
    for (int32_t y = start_y; y < end_y; y++) {
        int32_t buf_row = src_y + y;
        if (buf_row < 0 || buf_row >= buf->height) continue;
        
        const uint32_t* src = buf->pixels + buf_row * buf->width + src_x + start_x;
        int32_t* dst = out_pixels + y * width + start_x;
        
        memcpy(dst, src, copy_width * sizeof(int32_t));
    }
    
    return 1;
}

void simjot_buffer_scroll(int64_t handle, int32_t dx, int32_t dy, uint32_t fill_argb) {
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return;
    
    int32_t w = buf->width;
    int32_t h = buf->height;
    
    if (dx == 0 && dy == 0) return;
    
    // Handle vertical scroll with memmove for efficiency
    if (dy != 0) {
        if (dy > 0) {
            // Scrolling down: move rows up, fill bottom
            if (dy < h) {
                memmove(buf->pixels,
                        buf->pixels + dy * w,
                        (size_t)(h - dy) * w * sizeof(uint32_t));
            }
            // Fill exposed bottom rows
            int32_t fill_start = (dy >= h) ? 0 : (h - dy);
            for (int32_t y = fill_start; y < h; y++) {
                uint32_t* row = buf->pixels + y * w;
                for (int32_t x = 0; x < w; x++) {
                    row[x] = fill_argb;
                }
            }
        } else {
            // Scrolling up: move rows down, fill top
            int32_t abs_dy = -dy;
            if (abs_dy < h) {
                memmove(buf->pixels + abs_dy * w,
                        buf->pixels,
                        (size_t)(h - abs_dy) * w * sizeof(uint32_t));
            }
            // Fill exposed top rows
            int32_t fill_end = (abs_dy >= h) ? h : abs_dy;
            for (int32_t y = 0; y < fill_end; y++) {
                uint32_t* row = buf->pixels + y * w;
                for (int32_t x = 0; x < w; x++) {
                    row[x] = fill_argb;
                }
            }
        }
    }
    
    // Handle horizontal scroll
    if (dx != 0) {
        if (dx > 0) {
            // Scrolling right: move pixels left, fill right edge
            for (int32_t y = 0; y < h; y++) {
                uint32_t* row = buf->pixels + y * w;
                if (dx < w) {
                    memmove(row, row + dx, (w - dx) * sizeof(uint32_t));
                }
                int32_t fill_start = (dx >= w) ? 0 : (w - dx);
                for (int32_t x = fill_start; x < w; x++) {
                    row[x] = fill_argb;
                }
            }
        } else {
            // Scrolling left: move pixels right, fill left edge
            int32_t abs_dx = -dx;
            for (int32_t y = 0; y < h; y++) {
                uint32_t* row = buf->pixels + y * w;
                if (abs_dx < w) {
                    memmove(row + abs_dx, row, (w - abs_dx) * sizeof(uint32_t));
                }
                int32_t fill_end = (abs_dx >= w) ? w : abs_dx;
                for (int32_t x = 0; x < fill_end; x++) {
                    row[x] = fill_argb;
                }
            }
        }
    }
}

int32_t simjot_buffer_composite(int64_t handle, const int32_t* pixels,
                                 int32_t src_width, int32_t src_height,
                                 int32_t dst_x, int32_t dst_y) {
    if (!pixels || src_width <= 0 || src_height <= 0) return 0;
    
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    // Clip to buffer bounds
    int32_t start_x = (dst_x < 0) ? -dst_x : 0;
    int32_t start_y = (dst_y < 0) ? -dst_y : 0;
    int32_t end_x = src_width;
    int32_t end_y = src_height;
    
    if (dst_x + end_x > buf->width) end_x = buf->width - dst_x;
    if (dst_y + end_y > buf->height) end_y = buf->height - dst_y;
    
    if (start_x >= end_x || start_y >= end_y) return 1;
    
    for (int32_t y = start_y; y < end_y; y++) {
        int32_t dst_row = dst_y + y;
        if (dst_row < 0 || dst_row >= buf->height) continue;
        
        const uint32_t* src_row = (const uint32_t*)pixels + y * src_width;
        uint32_t* dst_row_ptr = buf->pixels + dst_row * buf->width;
        
        for (int32_t x = start_x; x < end_x; x++) {
            int32_t dst_col = dst_x + x;
            if (dst_col < 0 || dst_col >= buf->width) continue;
            
            uint32_t src_pixel = src_row[x];
            uint32_t dst_pixel = dst_row_ptr[dst_col];
            dst_row_ptr[dst_col] = alpha_blend(src_pixel, dst_pixel);
        }
    }
    
    return 1;
}

int32_t simjot_buffer_get_size(int64_t handle, int32_t* out_width, int32_t* out_height) {
    std::lock_guard<std::mutex> lock(g_buffer_mutex);
    OffscreenBuffer* buf = get_buffer(handle);
    if (!buf) return 0;
    
    if (out_width) *out_width = buf->width;
    if (out_height) *out_height = buf->height;
    return 1;
}

} /* extern "C" */

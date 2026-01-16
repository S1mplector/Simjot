/*
 * SIMJOT - MIT License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE.md for full terms.
 */

#include "simjot_native.h"

#ifdef __APPLE__
#import <Cocoa/Cocoa.h>
#import <CoreGraphics/CoreGraphics.h>
#import <CoreVideo/CoreVideo.h>
#import <objc/message.h>
#endif

extern "C" float simjot_macos_get_primary_refresh_rate(void) {
#ifdef __APPLE__
    double rate = 0.0;
    @autoreleasepool {
        NSScreen* screen = [NSScreen mainScreen];
        if (screen) {
            SEL sel = NSSelectorFromString(@"maximumFramesPerSecond");
            if ([screen respondsToSelector:sel]) {
                NSInteger fps = ((NSInteger (*)(id, SEL))objc_msgSend)(screen, sel);
                if (fps > 0) rate = (double)fps;
            }
        }
    }

    if (rate <= 1.0) {
        CGDirectDisplayID display = CGMainDisplayID();
        CGDisplayModeRef mode = CGDisplayCopyDisplayMode(display);
        if (mode) {
            double fps = CGDisplayModeGetRefreshRate(mode);
            CGDisplayModeRelease(mode);
            if (fps > rate) rate = fps;
        }
    }

    if (rate <= 1.0) {
        CVDisplayLinkRef link = NULL;
        if (CVDisplayLinkCreateWithCGDisplay(CGMainDisplayID(), &link) == kCVReturnSuccess && link) {
            CVTime period = CVDisplayLinkGetNominalOutputVideoRefreshPeriod(link);
            if (period.timeValue > 0 && period.timeScale > 0) {
                rate = (double)period.timeScale / (double)period.timeValue;
            }
            CVDisplayLinkRelease(link);
        }
    }

    if (rate <= 1.0) rate = 60.0;
    return (float)rate;
#else
    return 0.0f;
#endif
}

extern "C" int32_t simjot_macos_is_low_power_mode(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSProcessInfo* info = [NSProcessInfo processInfo];
        SEL sel = NSSelectorFromString(@"isLowPowerModeEnabled");
        if ([info respondsToSelector:sel]) {
            BOOL enabled = ((BOOL (*)(id, SEL))objc_msgSend)(info, sel);
            return enabled ? 1 : 0;
        }
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_reduce_motion_enabled(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSWorkspace* ws = [NSWorkspace sharedWorkspace];
        SEL sel = NSSelectorFromString(@"accessibilityDisplayShouldReduceMotion");
        if ([ws respondsToSelector:sel]) {
            BOOL enabled = ((BOOL (*)(id, SEL))objc_msgSend)(ws, sel);
            return enabled ? 1 : 0;
        }
    }
#endif
    return 0;
}

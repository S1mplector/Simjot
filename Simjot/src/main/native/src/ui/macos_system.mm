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
#import <IOKit/ps/IOPowerSources.h>
#import <IOKit/ps/IOPSKeys.h>
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

extern "C" int32_t simjot_macos_get_thermal_state(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSProcessInfo* info = [NSProcessInfo processInfo];
        SEL sel = NSSelectorFromString(@"thermalState");
        if ([info respondsToSelector:sel]) {
            NSInteger state = ((NSInteger (*)(id, SEL))objc_msgSend)(info, sel);
            return (int32_t)state;
        }
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_get_accent_color(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSColor* rgb = nil;
        SEL accentSel = NSSelectorFromString(@"controlAccentColor");
        if ([NSColor respondsToSelector:accentSel]) {
            rgb = ((NSColor* (*)(id, SEL))objc_msgSend)([NSColor class], accentSel);
        }
        if (rgb) {
            rgb = [rgb colorUsingColorSpace:[NSColorSpace sRGBColorSpace]];
        }
        if (!rgb) {
            SEL fallbackSel = NSSelectorFromString(@"selectedContentBackgroundColor");
            NSColor* fallback = nil;
            if ([NSColor respondsToSelector:fallbackSel]) {
                fallback = ((NSColor* (*)(id, SEL))objc_msgSend)([NSColor class], fallbackSel);
            } else {
                fallback = [NSColor selectedControlColor];
            }
            if (fallback) {
                rgb = [fallback colorUsingColorSpace:[NSColorSpace sRGBColorSpace]];
            }
        }
        if (!rgb) return 0;

        CGFloat r = 0, g = 0, b = 0, a = 1;
        [rgb getRed:&r green:&g blue:&b alpha:&a];

        int ri = (int)(r * 255.0 + 0.5);
        int gi = (int)(g * 255.0 + 0.5);
        int bi = (int)(b * 255.0 + 0.5);
        int ai = (int)(a * 255.0 + 0.5);

        if (ri < 0) ri = 0; else if (ri > 255) ri = 255;
        if (gi < 0) gi = 0; else if (gi > 255) gi = 255;
        if (bi < 0) bi = 0; else if (bi > 255) bi = 255;
        if (ai < 0) ai = 0; else if (ai > 255) ai = 255;

        return (int32_t)((ai << 24) | (ri << 16) | (gi << 8) | bi);
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_is_on_battery(void) {
#ifdef __APPLE__
    @autoreleasepool {
        CFTypeRef info = IOPSCopyPowerSourcesInfo();
        if (!info) return 0;
        CFArrayRef sources = IOPSCopyPowerSourcesList(info);
        if (!sources) {
            CFRelease(info);
            return 0;
        }
        int32_t on_battery = 0;
        CFIndex count = CFArrayGetCount(sources);
        for (CFIndex i = 0; i < count; i++) {
            CFTypeRef ps = CFArrayGetValueAtIndex(sources, i);
            CFDictionaryRef desc = IOPSGetPowerSourceDescription(info, ps);
            if (!desc) continue;
            CFStringRef state = (CFStringRef)CFDictionaryGetValue(desc, CFSTR(kIOPSPowerSourceStateKey));
            if (state && CFStringCompare(state, CFSTR(kIOPSBatteryPowerValue), 0) == kCFCompareEqualTo) {
                on_battery = 1;
                break;
            }
        }
        CFRelease(sources);
        CFRelease(info);
        return on_battery;
    }
#endif
    return 0;
}

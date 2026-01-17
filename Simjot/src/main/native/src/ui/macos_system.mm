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
#include <cstring>
#endif

#ifdef __APPLE__
enum {
    SIMJOT_ICLOUD_EXISTS = 1 << 0,
    SIMJOT_ICLOUD_UBIQUITOUS = 1 << 1,
    SIMJOT_ICLOUD_DOWNLOADED = 1 << 2,
    SIMJOT_ICLOUD_DOWNLOADING = 1 << 3,
    SIMJOT_ICLOUD_UPLOADING = 1 << 4,
    SIMJOT_ICLOUD_CONFLICT = 1 << 5
};

static int simjot_path_is_under(NSString* path, NSString* base) {
    if (!path || !base) return 0;
    NSString* p = [path stringByStandardizingPath];
    NSString* b = [base stringByStandardizingPath];
    if (!p || !b) return 0;
    if ([p isEqualToString:b]) return 1;
    if (![b hasSuffix:@"/"]) {
        b = [b stringByAppendingString:@"/"];
    }
    return [p hasPrefix:b] ? 1 : 0;
}

static int simjot_is_icloud_path_fallback(NSString* path) {
    if (!path) return 0;
    NSFileManager* fm = [NSFileManager defaultManager];
    NSString* base = nil;
    id token = nil;
    if ([fm respondsToSelector:@selector(ubiquityIdentityToken)]) {
        token = [fm ubiquityIdentityToken];
    }
    if (token) {
        SEL sel = NSSelectorFromString(@"URLForUbiquityContainerIdentifier:");
        if ([fm respondsToSelector:sel]) {
            NSURL* ubiq = ((NSURL* (*)(id, SEL, NSString*))objc_msgSend)(fm, sel, nil);
            if (ubiq) {
                NSURL* docs = [ubiq URLByAppendingPathComponent:@"Documents"];
                base = [docs path];
            }
        }
    }
    if (!base) {
        base = [NSHomeDirectory() stringByAppendingPathComponent:@"Library/Mobile Documents/com~apple~CloudDocs"];
    }
    return simjot_path_is_under(path, base);
}
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

extern "C" int32_t simjot_macos_get_icloud_path(char* out, int32_t out_len) {
#ifdef __APPLE__
    @autoreleasepool {
        NSFileManager* fm = [NSFileManager defaultManager];
        id token = [fm ubiquityIdentityToken];
        if (!token) return 0; // iCloud not signed in

        NSString* basePath = nil;
        NSURL* ubiq = [fm URLForUbiquityContainerIdentifier:nil];
        if (ubiq) {
            NSURL* docs = [ubiq URLByAppendingPathComponent:@"Documents"];
            basePath = [docs path];
        }
        if (!basePath) {
            basePath = [NSHomeDirectory() stringByAppendingPathComponent:@"Library/Mobile Documents/com~apple~CloudDocs"];
        }
        if (!basePath) return 0;

        NSString* simjotPath = [basePath stringByAppendingPathComponent:@"Simjot"];
        NSData* data = [simjotPath dataUsingEncoding:NSUTF8StringEncoding];
        if (!data) return 0;

        int32_t len = (int32_t)[data length];
        if (out && out_len > 0) {
            int32_t copy_len = (len < out_len - 1) ? len : out_len - 1;
            memcpy(out, [data bytes], (size_t)copy_len);
            out[copy_len] = '\0';
        }
        return len;
    }
#else
    (void)out;
    (void)out_len;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_is_icloud_path(const char* path) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return 0;
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath) return 0;
        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return 0;
        NSNumber* isUbiq = nil;
        NSError* error = nil;
        if (![url getResourceValue:&isUbiq forKey:NSURLIsUbiquitousItemKey error:&error]) {
            return simjot_is_icloud_path_fallback(nsPath);
        }
        if (isUbiq && [isUbiq boolValue]) return 1;
        return simjot_is_icloud_path_fallback(nsPath);
    }
#else
    (void)path;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_icloud_is_available(void) {
#ifdef __APPLE__
    @autoreleasepool {
        NSFileManager* fm = [NSFileManager defaultManager];
        SEL sel = NSSelectorFromString(@"ubiquityIdentityToken");
        if ([fm respondsToSelector:sel]) {
            id token = ((id (*)(id, SEL))objc_msgSend)(fm, sel);
            return token ? 1 : 0;
        }
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_icloud_item_status(const char* path) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return 0;
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath) return 0;
        NSFileManager* fm = [NSFileManager defaultManager];
        BOOL isDir = NO;
        if (![fm fileExistsAtPath:nsPath isDirectory:&isDir]) return 0;

        int32_t flags = SIMJOT_ICLOUD_EXISTS;
        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return flags;

        NSNumber* isUbiq = nil;
        NSError* error = nil;
        if ([url getResourceValue:&isUbiq forKey:NSURLIsUbiquitousItemKey error:&error]) {
            if (isUbiq && [isUbiq boolValue]) flags |= SIMJOT_ICLOUD_UBIQUITOUS;
        } else if (simjot_is_icloud_path_fallback(nsPath)) {
            flags |= SIMJOT_ICLOUD_UBIQUITOUS;
        }

        if (flags & SIMJOT_ICLOUD_UBIQUITOUS) {
            NSNumber* downloaded = nil;
            if ([url getResourceValue:&downloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
                if (downloaded && [downloaded boolValue]) flags |= SIMJOT_ICLOUD_DOWNLOADED;
            }
            NSNumber* downloading = nil;
            if ([url getResourceValue:&downloading forKey:NSURLUbiquitousItemIsDownloadingKey error:nil]) {
                if (downloading && [downloading boolValue]) flags |= SIMJOT_ICLOUD_DOWNLOADING;
            }
            NSNumber* uploading = nil;
            if ([url getResourceValue:&uploading forKey:NSURLUbiquitousItemIsUploadingKey error:nil]) {
                if (uploading && [uploading boolValue]) flags |= SIMJOT_ICLOUD_UPLOADING;
            }
            NSNumber* conflicts = nil;
            if ([url getResourceValue:&conflicts forKey:NSURLUbiquitousItemHasUnresolvedConflictsKey error:nil]) {
                if (conflicts && [conflicts boolValue]) flags |= SIMJOT_ICLOUD_CONFLICT;
            }
            NSString* status = nil;
            if ([url getResourceValue:&status forKey:NSURLUbiquitousItemDownloadingStatusKey error:nil]) {
                if ([status isEqualToString:NSURLUbiquitousItemDownloadingStatusCurrent] ||
                    [status isEqualToString:NSURLUbiquitousItemDownloadingStatusDownloaded]) {
                    flags |= SIMJOT_ICLOUD_DOWNLOADED;
                }
            }
        }

        return flags;
    }
#else
    (void)path;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_icloud_start_download(const char* path) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return 0;
        NSString* nsPath = [NSString stringWithUTF8String:path];
        if (!nsPath) return 0;
        NSFileManager* fm = [NSFileManager defaultManager];
        if (![fm fileExistsAtPath:nsPath]) return 0;
        NSURL* url = [NSURL fileURLWithPath:nsPath];
        if (!url) return 0;

        NSNumber* isUbiq = nil;
        if ([url getResourceValue:&isUbiq forKey:NSURLIsUbiquitousItemKey error:nil]) {
            if (!isUbiq || ![isUbiq boolValue]) return 0;
        } else if (!simjot_is_icloud_path_fallback(nsPath)) {
            return 0;
        }

        NSNumber* downloaded = nil;
        if ([url getResourceValue:&downloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
            if (downloaded && [downloaded boolValue]) return 1;
        }

        SEL sel = NSSelectorFromString(@"startDownloadingUbiquitousItemAtURL:error:");
        if ([fm respondsToSelector:sel]) {
            NSError* error = nil;
            BOOL ok = ((BOOL (*)(id, SEL, NSURL*, NSError**))objc_msgSend)(fm, sel, url, &error);
            return ok ? 1 : 0;
        }
    }
#endif
    return 0;
}

extern "C" int32_t simjot_macos_icloud_prefetch_dir(const char* path, int32_t max_items, int32_t max_depth) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return 0;
        NSString* rootPath = [NSString stringWithUTF8String:path];
        if (!rootPath) return 0;

        NSFileManager* fm = [NSFileManager defaultManager];
        BOOL isDir = NO;
        if (![fm fileExistsAtPath:rootPath isDirectory:&isDir] || !isDir) return 0;

        NSURL* rootUrl = [NSURL fileURLWithPath:rootPath];
        if (!rootUrl) return 0;

        NSArray* keys = @[
            NSURLIsUbiquitousItemKey,
            NSURLUbiquitousItemIsDownloadedKey,
            NSURLUbiquitousItemIsDownloadingKey,
            NSURLIsDirectoryKey
        ];

        NSDirectoryEnumerator* enumerator = [fm enumeratorAtURL:rootUrl
                                     includingPropertiesForKeys:keys
                                                        options:(NSDirectoryEnumerationSkipsHiddenFiles |
                                                                 NSDirectoryEnumerationSkipsPackageDescendants)
                                                   errorHandler:^BOOL(NSURL* url, NSError* error) {
            (void)url;
            (void)error;
            return YES;
        }];
        if (!enumerator) return 0;

        NSUInteger rootDepth = [[rootPath pathComponents] count];
        int32_t requested = 0;
        for (NSURL* url in enumerator) {
            if (!url) continue;
            NSString* itemPath = [url path];
            if (itemPath && max_depth > 0) {
                NSUInteger depth = [[itemPath pathComponents] count];
                if (depth > rootDepth + (NSUInteger)max_depth) {
                    [enumerator skipDescendants];
                    continue;
                }
            }

            NSNumber* isUbiq = nil;
            if (![url getResourceValue:&isUbiq forKey:NSURLIsUbiquitousItemKey error:nil]) {
                continue;
            }
            if (!isUbiq || ![isUbiq boolValue]) continue;

            NSNumber* downloaded = nil;
            if ([url getResourceValue:&downloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
                if (downloaded && [downloaded boolValue]) {
                    continue;
                }
            }

            NSNumber* downloading = nil;
            if ([url getResourceValue:&downloading forKey:NSURLUbiquitousItemIsDownloadingKey error:nil]) {
                if (downloading && [downloading boolValue]) {
                    continue;
                }
            }

            SEL sel = NSSelectorFromString(@"startDownloadingUbiquitousItemAtURL:error:");
            if ([fm respondsToSelector:sel]) {
                NSError* error = nil;
                BOOL ok = ((BOOL (*)(id, SEL, NSURL*, NSError**))objc_msgSend)(fm, sel, url, &error);
                if (ok) requested++;
            }

            if (max_items > 0 && requested >= max_items) break;
        }

        return requested;
    }
#else
    (void)path;
    (void)max_items;
    (void)max_depth;
    return 0;
#endif
}

extern "C" int32_t simjot_macos_icloud_prefetch_query(const char* path, int32_t max_items, int32_t timeout_ms) {
#ifdef __APPLE__
    @autoreleasepool {
        if (!path || !*path) return -1;
        NSString* rootPath = [NSString stringWithUTF8String:path];
        if (!rootPath) return -1;

        NSFileManager* fm = [NSFileManager defaultManager];
        id token = nil;
        if ([fm respondsToSelector:@selector(ubiquityIdentityToken)]) {
            token = [fm ubiquityIdentityToken];
        }
        if (!token) return -1;

        BOOL isDir = NO;
        if (![fm fileExistsAtPath:rootPath isDirectory:&isDir] || !isDir) return -1;

        if (!simjot_is_icloud_path_fallback(rootPath)) return -1;

        NSURL* rootUrl = [NSURL fileURLWithPath:rootPath];
        if (!rootUrl) return -1;

        NSMetadataQuery* query = [[NSMetadataQuery alloc] init];
        if (!query) return -1;

        [query setSearchScopes:@[rootUrl]];
        NSPredicate* predicate = [NSPredicate predicateWithFormat:@"%K == FALSE",
                                  NSMetadataUbiquitousItemIsDownloadedKey];
        [query setPredicate:predicate];

        __block BOOL done = NO;
        NSNotificationCenter* nc = [NSNotificationCenter defaultCenter];
        id finishObserver = [nc addObserverForName:NSMetadataQueryDidFinishGatheringNotification
                                            object:query
                                             queue:nil
                                        usingBlock:^(NSNotification* note) {
            (void)note;
            done = YES;
        }];
        id updateObserver = [nc addObserverForName:NSMetadataQueryDidUpdateNotification
                                            object:query
                                             queue:nil
                                        usingBlock:^(NSNotification* note) {
            (void)note;
        }];

        if (![query startQuery]) {
            [nc removeObserver:finishObserver];
            if (updateObserver) [nc removeObserver:updateObserver];
#if !__has_feature(objc_arc)
            [query release];
#endif
            return -1;
        }

        int32_t timeout = timeout_ms > 0 ? timeout_ms : 1200;
        NSDate* end = [NSDate dateWithTimeIntervalSinceNow:timeout / 1000.0];
        while (!done && [end timeIntervalSinceNow] > 0) {
            [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode
                                     beforeDate:[NSDate dateWithTimeIntervalSinceNow:0.05]];
        }

        [query disableUpdates];
        NSArray* results = [query results];
        int32_t requested = 0;
        for (NSMetadataItem* item in results) {
            if (!item) continue;
            if (max_items > 0 && requested >= max_items) break;

            NSURL* url = [item valueForAttribute:NSMetadataItemURLKey];
            if (![url isKindOfClass:[NSURL class]]) continue;

            NSNumber* isDirNum = nil;
            if ([url getResourceValue:&isDirNum forKey:NSURLIsDirectoryKey error:nil]) {
                if (isDirNum && [isDirNum boolValue]) {
                    continue;
                }
            }

            NSNumber* downloaded = nil;
            if ([url getResourceValue:&downloaded forKey:NSURLUbiquitousItemIsDownloadedKey error:nil]) {
                if (downloaded && [downloaded boolValue]) continue;
            }

            NSNumber* downloading = nil;
            if ([url getResourceValue:&downloading forKey:NSURLUbiquitousItemIsDownloadingKey error:nil]) {
                if (downloading && [downloading boolValue]) continue;
            }

            SEL sel = NSSelectorFromString(@"startDownloadingUbiquitousItemAtURL:error:");
            if ([fm respondsToSelector:sel]) {
                NSError* error = nil;
                BOOL ok = ((BOOL (*)(id, SEL, NSURL*, NSError**))objc_msgSend)(fm, sel, url, &error);
                if (ok) requested++;
            }
        }

        [query stopQuery];
        [query enableUpdates];
        [nc removeObserver:finishObserver];
        if (updateObserver) [nc removeObserver:updateObserver];
#if !__has_feature(objc_arc)
        [query release];
#endif
        return requested;
    }
#else
    (void)path;
    (void)max_items;
    (void)timeout_ms;
    return -1;
#endif
}

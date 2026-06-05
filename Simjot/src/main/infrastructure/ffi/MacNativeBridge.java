/*
 * SIMJOT - No Derivatives License
 * 
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 * 
 * See LICENSE for full terms.
 */

package main.infrastructure.ffi;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Lightweight bridge for macOS-only native utilities not yet wrapped in NativeLibrary.
 */
public final class MacNativeBridge {
    private static final Linker LINKER = Linker.nativeLinker();

    private static volatile boolean handlesInitialized;
    private static MethodHandle spotlightSearchHandle;
    private static MethodHandle quickLookThumbnailHandle;
    private static MethodHandle bookmarkCreateHandle;
    private static MethodHandle bookmarkResolveHandle;
    private static MethodHandle bookmarkStartHandle;
    private static MethodHandle bookmarkStopHandle;
    private static MethodHandle powerAssertionBeginHandle;
    private static MethodHandle powerAssertionEndHandle;

    private MacNativeBridge() {}

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac");
    }

    private static MethodHandle optionalHandle(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        if (lookup == null) return null;
        try {
            MemorySegment symbol = lookup.find(name).orElse(null);
            if (symbol == null) return null;
            return LINKER.downcallHandle(symbol, descriptor);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static synchronized void ensureHandles() {
        if (handlesInitialized) return;

        SymbolLookup lookup = NativeAccess.symbolLookup();
        if (lookup != null) {
            spotlightSearchHandle = optionalHandle(
                    lookup,
                    "simjot_macos_spotlight_search",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT));

            quickLookThumbnailHandle = optionalHandle(
                    lookup,
                    "simjot_macos_quicklook_thumbnail_png",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT));

            bookmarkCreateHandle = optionalHandle(
                    lookup,
                    "simjot_macos_bookmark_create",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT));

            bookmarkResolveHandle = optionalHandle(
                    lookup,
                    "simjot_macos_bookmark_resolve",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS));

            bookmarkStartHandle = optionalHandle(
                    lookup,
                    "simjot_macos_bookmark_start_access",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS));

            bookmarkStopHandle = optionalHandle(
                    lookup,
                    "simjot_macos_bookmark_stop_access",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));

            powerAssertionBeginHandle = optionalHandle(
                    lookup,
                    "simjot_macos_power_assertion_begin",
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));

            powerAssertionEndHandle = optionalHandle(
                    lookup,
                    "simjot_macos_power_assertion_end",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        }

        handlesInitialized = true;
    }

    public static List<String> spotlightSearchPaths(List<String> roots,
                                                    String query,
                                                    String extensionsCsv,
                                                    int maxResults,
                                                    int timeoutMs) {
        if (!isMac() || roots == null || roots.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        ensureHandles();
        if (spotlightSearchHandle == null) return Collections.emptyList();

        StringBuilder rootBuilder = new StringBuilder();
        for (String root : roots) {
            if (root == null || root.isBlank()) continue;
            if (!rootBuilder.isEmpty()) rootBuilder.append('\n');
            rootBuilder.append(root);
        }
        if (rootBuilder.isEmpty()) return Collections.emptyList();

        String extText = (extensionsCsv == null || extensionsCsv.isBlank()) ? ".note,.txt,.ntk,.poem,.rtf" : extensionsCsv;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rootsSeg = arena.allocateFrom(rootBuilder.toString());
            MemorySegment querySeg = arena.allocateFrom(query);
            MemorySegment extSeg = arena.allocateFrom(extText);

            int required = (int) spotlightSearchHandle.invokeExact(
                    rootsSeg,
                    querySeg,
                    extSeg,
                    Math.max(1, maxResults),
                    Math.max(100, timeoutMs),
                    MemorySegment.NULL,
                    0);
            if (required <= 0) return Collections.emptyList();

            int cap = Math.max(required + 1, 4096);
            MemorySegment outSeg = arena.allocate(cap);
            int len = (int) spotlightSearchHandle.invokeExact(
                    rootsSeg,
                    querySeg,
                    extSeg,
                    Math.max(1, maxResults),
                    Math.max(100, timeoutMs),
                    outSeg,
                    cap);

            if (len <= 0) return Collections.emptyList();
            int usable = Math.min(len, cap - 1);
            if (usable <= 0) return Collections.emptyList();

            byte[] bytes = outSeg.asSlice(0, usable).toArray(ValueLayout.JAVA_BYTE);
            String payload = new String(bytes, StandardCharsets.UTF_8).trim();
            if (payload.isEmpty()) return Collections.emptyList();

            List<String> paths = new ArrayList<>();
            for (String line : payload.split("\\n")) {
                if (line == null || line.isBlank()) continue;
                paths.add(line.trim());
            }
            return paths;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    public static BufferedImage quickLookThumbnail(File file, int maxEdge) {
        if (!isMac() || file == null || !file.exists() || file.isDirectory()) return null;
        ensureHandles();
        if (quickLookThumbnailHandle == null) return null;

        int edge = Math.max(32, Math.min(1024, maxEdge));

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathSeg = arena.allocateFrom(file.getAbsolutePath());

            int required = (int) quickLookThumbnailHandle.invokeExact(pathSeg, edge, MemorySegment.NULL, 0);
            if (required <= 0) return null;

            int cap = Math.max(required + 1, 8192);
            MemorySegment outSeg = arena.allocate(cap);
            int len = (int) quickLookThumbnailHandle.invokeExact(pathSeg, edge, outSeg, cap);
            if (len <= 0) return null;

            int usable = Math.min(len, cap - 1);
            if (usable <= 0) return null;

            byte[] png = outSeg.asSlice(0, usable).toArray(ValueLayout.JAVA_BYTE);
            try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
                return ImageIO.read(in);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static byte[] createSecurityBookmark(String path) {
        if (!isMac() || path == null || path.isBlank()) return null;
        ensureHandles();
        if (bookmarkCreateHandle == null) return null;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pathSeg = arena.allocateFrom(path);

            int required = (int) bookmarkCreateHandle.invokeExact(pathSeg, MemorySegment.NULL, 0);
            if (required <= 0) return null;

            MemorySegment outSeg = arena.allocate(required);
            int len = (int) bookmarkCreateHandle.invokeExact(pathSeg, outSeg, required);
            if (len <= 0) return null;

            int usable = Math.min(len, required);
            return outSeg.asSlice(0, usable).toArray(ValueLayout.JAVA_BYTE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String resolveSecurityBookmark(byte[] bookmarkData) {
        if (!isMac() || bookmarkData == null || bookmarkData.length == 0) return null;
        ensureHandles();
        if (bookmarkResolveHandle == null) return null;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bookmarkSeg = arena.allocate(bookmarkData.length);
            bookmarkSeg.copyFrom(MemorySegment.ofArray(bookmarkData));

            int outCap = 4096;
            MemorySegment outPath = arena.allocate(outCap);
            MemorySegment outStale = arena.allocate(ValueLayout.JAVA_INT);

            int len = (int) bookmarkResolveHandle.invokeExact(
                    bookmarkSeg,
                    bookmarkData.length,
                    outPath,
                    outCap,
                    outStale);
            if (len <= 0) return null;

            if (len >= outCap) {
                outCap = len + 1;
                outPath = arena.allocate(outCap);
                len = (int) bookmarkResolveHandle.invokeExact(
                        bookmarkSeg,
                        bookmarkData.length,
                        outPath,
                        outCap,
                        outStale);
                if (len <= 0) return null;
            }

            int usable = Math.min(len, outCap - 1);
            byte[] pathBytes = outPath.asSlice(0, usable).toArray(ValueLayout.JAVA_BYTE);
            return new String(pathBytes, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static long startSecurityBookmarkAccess(byte[] bookmarkData) {
        if (!isMac() || bookmarkData == null || bookmarkData.length == 0) return 0L;
        ensureHandles();
        if (bookmarkStartHandle == null) return 0L;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bookmarkSeg = arena.allocate(bookmarkData.length);
            bookmarkSeg.copyFrom(MemorySegment.ofArray(bookmarkData));
            MemorySegment outToken = arena.allocate(ValueLayout.JAVA_LONG);

            int result = (int) bookmarkStartHandle.invokeExact(bookmarkSeg, bookmarkData.length, outToken);
            if (result <= 0) return 0L;
            return outToken.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static boolean stopSecurityBookmarkAccess(long token) {
        if (!isMac() || token <= 0L) return false;
        ensureHandles();
        if (bookmarkStopHandle == null) return false;

        try {
            return ((int) bookmarkStopHandle.invokeExact(token)) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static long beginPowerAssertion(String reason) {
        if (!isMac()) return 0L;
        ensureHandles();
        if (powerAssertionBeginHandle == null) return 0L;

        String why = (reason == null || reason.isBlank()) ? "Simjot operation" : reason;

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment reasonSeg = arena.allocateFrom(why);
            MemorySegment outToken = arena.allocate(ValueLayout.JAVA_LONG);
            int ok = (int) powerAssertionBeginHandle.invokeExact(reasonSeg, outToken);
            if (ok <= 0) return 0L;
            return outToken.get(ValueLayout.JAVA_LONG, 0);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static boolean endPowerAssertion(long token) {
        if (!isMac() || token <= 0L) return false;
        ensureHandles();
        if (powerAssertionEndHandle == null) return false;

        try {
            return ((int) powerAssertionEndHandle.invokeExact(token)) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }
}

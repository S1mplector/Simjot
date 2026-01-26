/*
 * SIMJOT - MIT License
 *
 * Copyright (c) 2024-2025 Ilgaz Mehmetoğlu.
 *
 * See LICENSE.md for full terms.
 */

package main.infrastructure.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Simjot mood log storage in a compact binary format (.moods).
 * Falls back to legacy mood_log.txt for migration when needed.
 */
public final class MoodFile {
    private MoodFile() {}

    public static final String MOODS_FILENAME = "mood_log.moods";
    public static final String LEGACY_FILENAME = "mood_log.txt";

    private static final byte[] FILE_MAGIC = "SIMJMOOD".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SEG_MAGIC = "MSEG".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_SIZE = 64;
    private static final int VERSION = 1;

    private static final Object IO_LOCK = new Object();

    private static final DateTimeFormatter LEGACY_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter[] LEGACY_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE
    };

    public static final class MoodRecord {
        public final LocalDateTime timestamp;
        public final int composite;
        public final int[] details; // length 8 or null

        public MoodRecord(LocalDateTime timestamp, int composite, int[] details) {
            this.timestamp = timestamp;
            this.composite = composite;
            this.details = details;
        }

        public boolean hasDetails() {
            if (details == null || details.length < 8) return false;
            for (int v : details) {
                if (v >= 0) return true;
            }
            return false;
        }

        public String legacyTimestampKey() {
            if (timestamp == null) return "";
            return timestamp.format(LEGACY_TS);
        }
    }

    public static File getMoodsFile() {
        try {
            return new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), MOODS_FILENAME);
        } catch (Throwable t) {
            return new File(System.getProperty("user.home"), "." + MOODS_FILENAME);
        }
    }

    public static File getLegacyFile() {
        try {
            return new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), LEGACY_FILENAME);
        } catch (Throwable t) {
            return new File(System.getProperty("user.home"), ".simjot_mood_log.txt");
        }
    }

    public static File getPrimaryFile() {
        File moods = getMoodsFile();
        if (isValidMoodFile(moods)) return moods;
        File legacy = getLegacyFile();
        return legacy;
    }

    public static void appendNow(int composite) {
        append(LocalDateTime.now(), composite, null);
    }

    public static void appendNow(int composite, int[] details) {
        append(LocalDateTime.now(), composite, details);
    }

    public static void append(LocalDateTime timestamp, int composite, int[] details) {
        if (timestamp == null) return;
        int clamped = clamp(composite);
        int[] det = normalizeDetails(details);
        MoodRecord rec = new MoodRecord(timestamp, clamped, det);

        synchronized (IO_LOCK) {
            ensureMoodsFile();
            File moods = getMoodsFile();
            if (!isValidMoodFile(moods)) return;
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(moods, true))) {
                writeSegment(out, rec.timestamp.toLocalDate(), Collections.singletonList(rec));
            } catch (IOException ignored) {}
        }
    }

    public static List<MoodRecord> readAllRecords() {
        synchronized (IO_LOCK) {
            ensureMoodsFile();
            File moods = getMoodsFile();
            if (isValidMoodFile(moods)) {
                List<MoodRecord> records = readMoodsFile(moods);
                if (!records.isEmpty()) return records;
            }
            File legacy = getLegacyFile();
            if (legacy.exists()) return readLegacyFile(legacy);
            return Collections.emptyList();
        }
    }

    public static void removeRecordsByTimestamp(Set<String> timestamps) {
        if (timestamps == null || timestamps.isEmpty()) return;
        synchronized (IO_LOCK) {
            List<MoodRecord> all = readAllRecords();
            if (all.isEmpty()) return;
            List<MoodRecord> keep = new ArrayList<>(all.size());
            boolean removed = false;
            for (MoodRecord r : all) {
                String key = r.legacyTimestampKey();
                if (!timestamps.contains(key)) {
                    keep.add(r);
                } else {
                    removed = true;
                }
            }
            if (removed) {
                writeNewFile(getMoodsFile(), keep);
            }
        }
    }

    public static void removeRecordsByTimestampAndValue(Map<String, Set<Integer>> moodKeys) {
        if (moodKeys == null || moodKeys.isEmpty()) return;
        synchronized (IO_LOCK) {
            List<MoodRecord> all = readAllRecords();
            if (all.isEmpty()) return;
            List<MoodRecord> keep = new ArrayList<>(all.size());
            boolean removed = false;
            for (MoodRecord r : all) {
                String key = r.legacyTimestampKey();
                Set<Integer> values = moodKeys.get(key);
                if (values != null && values.contains(r.composite)) {
                    removed = true;
                    continue;
                }
                keep.add(r);
            }
            if (removed) {
                writeNewFile(getMoodsFile(), keep);
            }
        }
    }

    private static void ensureMoodsFile() {
        File moods = getMoodsFile();
        if (isValidMoodFile(moods)) return;

        File legacy = getLegacyFile();
        if (legacy.exists() && legacy.length() > 0) {
            List<MoodRecord> legacyRecords = readLegacyFile(legacy);
            writeNewFile(moods, legacyRecords);
            return;
        }

        if (!moods.exists()) {
            writeNewFile(moods, Collections.emptyList());
        }
    }

    private static boolean isValidMoodFile(File file) {
        if (file == null || !file.exists() || file.length() < HEADER_SIZE) return false;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] magic = new byte[FILE_MAGIC.length];
            if (in.read(magic) != FILE_MAGIC.length) return false;
            for (int i = 0; i < FILE_MAGIC.length; i++) {
                if (magic[i] != FILE_MAGIC[i]) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static List<MoodRecord> readMoodsFile(File file) {
        if (file == null || !file.exists()) return Collections.emptyList();
        List<MoodRecord> out = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            if (!consumeHeader(in)) return out;
            while (true) {
                byte[] magic = new byte[4];
                int read = in.read(magic);
                if (read < 0) break;
                if (read != 4) break;
                if (!matches(magic, SEG_MAGIC)) break;
                int version = readShortLE(in);
                int flags = readShortLE(in);
                int entryCount = readIntLE(in);
                int dateYmd = readIntLE(in);
                int payloadLen = readIntLE(in);
                int crc = readIntLE(in);
                if (payloadLen <= 0 || entryCount <= 0) {
                    if (payloadLen > 0) {
                        skipFully(in, payloadLen);
                    }
                    continue;
                }
                byte[] payload = new byte[payloadLen];
                readFully(in, payload);
                if (crc != 0 && crc != (int) computeCrc32(payload)) {
                    break;
                }
                parsePayload(out, payload, entryCount, dateYmd, flags);
            }
        } catch (IOException ignored) {}
        return out;
    }

    private static void parsePayload(List<MoodRecord> out, byte[] payload, int entryCount, int dateYmd, int flags)
            throws IOException {
        LocalDate date = dateFromYmd(dateYmd);
        if (date == null) return;
        int seconds = 0;
        try (InputStream in = new ByteArrayInputStream(payload)) {
            for (int i = 0; i < entryCount; i++) {
                int delta = readVarInt(in);
                seconds = Math.max(0, seconds + delta);
                int composite = readUnsignedByte(in);
                int mask = readUnsignedByte(in);
                int[] details = null;
                if (mask != 0) {
                    details = new int[8];
                    for (int k = 0; k < 8; k++) {
                        int v = readUnsignedByte(in);
                        details[k] = ((mask >> k) & 0x1) == 1 ? v : -1;
                    }
                }
                LocalDateTime ts = date.atStartOfDay().plusSeconds(Math.min(86399, seconds));
                out.add(new MoodRecord(ts, composite, details));
            }
        }
    }

    private static boolean consumeHeader(InputStream in) throws IOException {
        byte[] magic = new byte[FILE_MAGIC.length];
        if (in.read(magic) != FILE_MAGIC.length) return false;
        if (!matches(magic, FILE_MAGIC)) return false;
        int ver = readShortLE(in);
        readShortLE(in); // flags
        int headerSize = readIntLE(in);
        readLongLE(in); // created
        int versionLen = readIntLE(in);
        if (versionLen > 0) {
            byte[] buf = new byte[Math.min(versionLen, Math.max(0, headerSize - 28))];
            readFully(in, buf);
        }
        int toSkip = Math.max(0, headerSize - 28 - Math.max(0, versionLen));
        if (toSkip > 0) skipFully(in, toSkip);
        return ver >= 1;
    }

    private static void writeNewFile(File moods, List<MoodRecord> records) {
        if (moods == null) return;
        try {
            List<MoodRecord> sorted = new ArrayList<>(records);
            sorted.sort(Comparator.comparing(r -> r.timestamp));
            Map<LocalDate, List<MoodRecord>> byDate = new LinkedHashMap<>();
            for (MoodRecord r : sorted) {
                if (r.timestamp == null) continue;
                LocalDate date = r.timestamp.toLocalDate();
                byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(r);
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (OutputStream out = new BufferedOutputStream(buffer)) {
                writeHeader(out);
                for (Map.Entry<LocalDate, List<MoodRecord>> entry : byDate.entrySet()) {
                    writeSegment(out, entry.getKey(), entry.getValue());
                }
            }
            FileIO.atomicWrite(moods.toPath(), buffer.toByteArray(), true, true);
        } catch (IOException ignored) {}
    }

    private static void writeHeader(OutputStream out) throws IOException {
        out.write(FILE_MAGIC);
        writeShortLE(out, VERSION);
        writeShortLE(out, 0); // flags
        writeIntLE(out, HEADER_SIZE);
        writeLongLE(out, System.currentTimeMillis());
        byte[] versionBytes = new byte[0];
        writeIntLE(out, versionBytes.length);
        if (versionBytes.length > 0) out.write(versionBytes);
        int pad = HEADER_SIZE - 28 - versionBytes.length;
        for (int i = 0; i < pad; i++) out.write(0);
    }

    private static void writeSegment(OutputStream out, LocalDate date, List<MoodRecord> records) throws IOException {
        if (records == null || records.isEmpty()) return;
        List<MoodRecord> valid = new ArrayList<>();
        for (MoodRecord r : records) {
            if (r != null && r.timestamp != null) valid.add(r);
        }
        if (valid.isEmpty()) return;
        valid.sort(Comparator.comparing(r -> r.timestamp));
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        int prevSeconds = 0;
        for (MoodRecord r : valid) {
            int seconds = secondsOfDay(r.timestamp);
            int delta = Math.max(0, seconds - prevSeconds);
            prevSeconds = seconds;
            writeVarInt(payload, delta);
            payload.write(clamp(r.composite));
            int[] details = normalizeDetails(r.details);
            int mask = 0;
            if (details != null) {
                for (int i = 0; i < 8; i++) {
                    if (details[i] >= 0) mask |= (1 << i);
                }
            }
            payload.write(mask & 0xFF);
            if (mask != 0) {
                for (int i = 0; i < 8; i++) {
                    int v = details != null ? details[i] : -1;
                    payload.write(clamp(v < 0 ? 0 : v));
                }
            }
        }
        byte[] payloadBytes = payload.toByteArray();
        int payloadLen = payloadBytes.length;
        CRC32 crc32 = new CRC32();
        crc32.update(payloadBytes);
        int crc = (int) crc32.getValue();

        out.write(SEG_MAGIC);
        writeShortLE(out, VERSION);
        writeShortLE(out, 0); // flags
        writeIntLE(out, valid.size());
        writeIntLE(out, ymdFromDate(date));
        writeIntLE(out, payloadLen);
        writeIntLE(out, crc);
        out.write(payloadBytes);
    }

    private static List<MoodRecord> readLegacyFile(File legacy) {
        if (legacy == null || !legacy.exists()) return Collections.emptyList();
        List<MoodRecord> out = new ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(legacy))) {
            String line;
            while ((line = br.readLine()) != null) {
                MoodRecord rec = parseLegacyLine(line);
                if (rec != null) out.add(rec);
            }
        } catch (IOException ignored) {}
        return out;
    }

    private static MoodRecord parseLegacyLine(String line) {
        if (line == null || line.isBlank()) return null;
        String[] parts = line.split(",", -1);
        if (parts.length < 2) return null;
        LocalDateTime ts = parseTimestamp(parts[0].trim());
        if (ts == null) return null;
        int composite = parseMoodValue(parts[1].trim());
        int[] details = null;
        if (parts.length >= 10) {
            details = new int[8];
            for (int i = 0; i < 8; i++) {
                details[i] = parseMoodValue(parts[i + 2].trim());
            }
        }
        return new MoodRecord(ts, composite, details);
    }

    private static LocalDateTime parseTimestamp(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(s);
            return d.atStartOfDay();
        } catch (Exception ignored) {}
        for (DateTimeFormatter fmt : LEGACY_FORMATS) {
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static int parseMoodValue(String s) {
        try {
            return clamp(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            if (":)".equals(s) || "😊".equals(s) || "😀".equals(s)) return 100;
            if (":/".equals(s) || "😐".equals(s)) return 50;
            if (":(".equals(s) || "😢".equals(s) || "😞".equals(s)) return 0;
            return 50;
        }
    }

    private static int[] normalizeDetails(int[] details) {
        if (details == null) return null;
        int[] out = new int[8];
        for (int i = 0; i < 8; i++) out[i] = -1;
        int limit = Math.min(details.length, 8);
        for (int i = 0; i < limit; i++) out[i] = details[i];
        return out;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static int secondsOfDay(LocalDateTime ts) {
        return Math.max(0, Math.min(86399, ts.getHour() * 3600 + ts.getMinute() * 60 + ts.getSecond()));
    }

    private static int ymdFromDate(LocalDate date) {
        if (date == null) return 0;
        return date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
    }

    private static LocalDate dateFromYmd(int ymd) {
        if (ymd <= 0) return null;
        int y = ymd / 10000;
        int m = (ymd / 100) % 100;
        int d = ymd % 100;
        try {
            return LocalDate.of(y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeShortLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static void writeIntLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void writeLongLE(OutputStream out, long v) throws IOException {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >>> 8) & 0xFF));
        out.write((int) ((v >>> 16) & 0xFF));
        out.write((int) ((v >>> 24) & 0xFF));
        out.write((int) ((v >>> 32) & 0xFF));
        out.write((int) ((v >>> 40) & 0xFF));
        out.write((int) ((v >>> 48) & 0xFF));
        out.write((int) ((v >>> 56) & 0xFF));
    }

    private static int readShortLE(InputStream in) throws IOException {
        int b1 = readUnsignedByte(in);
        int b2 = readUnsignedByte(in);
        return (b2 << 8) | b1;
    }

    private static int readIntLE(InputStream in) throws IOException {
        int b1 = readUnsignedByte(in);
        int b2 = readUnsignedByte(in);
        int b3 = readUnsignedByte(in);
        int b4 = readUnsignedByte(in);
        return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    private static long readLongLE(InputStream in) throws IOException {
        long b1 = readUnsignedByte(in);
        long b2 = readUnsignedByte(in);
        long b3 = readUnsignedByte(in);
        long b4 = readUnsignedByte(in);
        long b5 = readUnsignedByte(in);
        long b6 = readUnsignedByte(in);
        long b7 = readUnsignedByte(in);
        long b8 = readUnsignedByte(in);
        return (b8 << 56) | (b7 << 48) | (b6 << 40) | (b5 << 32)
                | (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    private static int readUnsignedByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException();
        return b & 0xFF;
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        int len = buf.length;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r < 0) throw new EOFException();
            off += r;
        }
    }

    private static void skipFully(InputStream in, int len) throws IOException {
        long remaining = len;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) throw new EOFException();
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static boolean matches(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    private static long computeCrc32(byte[] payload) {
        CRC32 crc = new CRC32();
        crc.update(payload);
        return crc.getValue();
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int shift = 0;
        int result = 0;
        while (shift < 32) {
            int b = readUnsignedByte(in);
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
        }
        throw new IOException("Varint too long");
    }
}

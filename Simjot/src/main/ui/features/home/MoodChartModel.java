package main.ui.features.home;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import main.infrastructure.io.AppDirectories;
import main.core.service.NotebookStore;
import main.infrastructure.backup.NotebookInfo;

final class MoodChartModel {
    static final class EntryRef { final File file; final LocalDateTime ts; EntryRef(File f, LocalDateTime t){ this.file=f; this.ts=t; } }
    static final class Details { final LocalDateTime ts; final int joy, calm, gratitude, energy, sadness, anger, anxiety, stress; Details(LocalDateTime ts,int joy,int calm,int gratitude,int energy,int sadness,int anger,int anxiety,int stress){ this.ts=ts; this.joy=joy; this.calm=calm; this.gratitude=gratitude; this.energy=energy; this.sadness=sadness; this.anger=anger; this.anxiety=anxiety; this.stress=stress; } }

    private final java.util.List<LocalDate> dayList = new ArrayList<>();
    private final java.util.List<Double> avgMoodList = new ArrayList<>();
    private final java.util.Map<LocalDate, java.util.List<File>> entriesByDate = new java.util.HashMap<>();
    private final java.util.Map<LocalDate, java.util.List<LocalDateTime>> moodTimesByDate = new java.util.HashMap<>();
    private final java.util.Map<LocalDate, java.util.List<EntryRef>> entryTimesByDate = new java.util.HashMap<>();
    private final java.util.Map<LocalDate, java.util.List<Details>> detailsByDate = new java.util.HashMap<>();

    boolean load(int rangeIndex){
        dayList.clear(); avgMoodList.clear();
        entriesByDate.clear(); moodTimesByDate.clear(); entryTimesByDate.clear(); detailsByDate.clear();
        int daysLimit = switch(rangeIndex){ case 0 -> 7; case 1 -> 30; case 2 -> 90; case 3 -> 365; default -> Integer.MAX_VALUE; };
        LocalDate today = LocalDate.now();
        java.util.Map<LocalDate, java.util.List<Integer>> map = new java.util.HashMap<>();
        File moodFile = new File(AppDirectories.folder(AppDirectories.Type.MOOD_DATA), "mood_log.txt");
        if (moodFile.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(moodFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        LocalDate date; LocalDateTime dateTime = null;
                        try {
                            date = LocalDate.parse(parts[0]);
                        } catch (Exception ex) {
                            try {
                                DateTimeFormatter alt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
                                dateTime = LocalDateTime.parse(parts[0], alt);
                                date = dateTime.toLocalDate();
                            } catch (Exception ex2) {
                                continue;
                            }
                        }
                        String moodStr = parts[1].trim();
                        int pct;
                        try { pct = Math.max(0, Math.min(100, Integer.parseInt(moodStr))); }
                        catch (NumberFormatException nfe) { pct = ":)".equals(moodStr) ? 100 : ":/".equals(moodStr) ? 50 : 0; }
                        map.computeIfAbsent(date, d -> new ArrayList<>()).add(pct);
                        if (dateTime != null) moodTimesByDate.computeIfAbsent(date, d -> new ArrayList<>()).add(dateTime);

                        // Parse detailed emotions if present: ts,composite,joy,calm,gratitude,energy,sadness,anger,anxiety,stress
                        if (parts.length >= 10) {
                            try {
                                int joy = Integer.parseInt(parts[2].trim());
                                int calm = Integer.parseInt(parts[3].trim());
                                int gratitude = Integer.parseInt(parts[4].trim());
                                int energy = Integer.parseInt(parts[5].trim());
                                int sadness = Integer.parseInt(parts[6].trim());
                                int anger = Integer.parseInt(parts[7].trim());
                                int anxiety = Integer.parseInt(parts[8].trim());
                                int stress = Integer.parseInt(parts[9].trim());
                                detailsByDate.computeIfAbsent(date, d -> new ArrayList<>()).add(new Details(dateTime, joy, calm, gratitude, energy, sadness, anger, anxiety, stress));
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
        if (map.isEmpty()) return false;
        java.util.List<LocalDate> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        LocalDate start = (daysLimit == Integer.MAX_VALUE) ? keys.get(0) : today.minusDays(Math.max(0, daysLimit - 1));
        LocalDate end = today;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dayList.add(d);
            java.util.List<Integer> l = map.get(d);
            if (l == null || l.isEmpty()) avgMoodList.add(null);
            else avgMoodList.add(l.stream().mapToInt(x -> x).average().orElse(0));
        }
        try {
            NotebookStore store = new NotebookStore();
            java.util.List<NotebookInfo> nbs = store.list();
            for (NotebookInfo nb : nbs) {
                if (nb == null || nb.getType() != NotebookInfo.Type.JOURNAL) continue;
                File folder = nb.getFolder();
                if (folder == null) continue;
                File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
                if (files == null) continue;
                for (File f : files) {
                    LocalDate d; LocalDateTime ts;
                    try {
                        String name = f.getName();
                        if (name.matches("\\d{8}_\\d{6}.*\\.txt")) {
                            String ymd = name.substring(0, 8);
                            String hms = name.substring(9, 15);
                            ts = LocalDateTime.parse(ymd+"_"+hms, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                            d = ts.toLocalDate();
                        } else {
                            ts = java.time.Instant.ofEpochMilli(f.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                            d = ts.toLocalDate();
                        }
                    } catch (Throwable ignored) { continue; }
                    entriesByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(f);
                    entryTimesByDate.computeIfAbsent(d, k -> new ArrayList<>()).add(new EntryRef(f, ts));
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    java.util.List<LocalDate> getDays(){ return dayList; }
    java.util.List<Double> getValues(){ return avgMoodList; }
    java.util.Map<LocalDate, java.util.List<File>> getEntriesByDate(){ return entriesByDate; }
    java.util.Map<LocalDate, java.util.List<LocalDateTime>> getMoodTimesByDate(){ return moodTimesByDate; }
    java.util.Map<LocalDate, java.util.List<EntryRef>> getEntryTimesByDate(){ return entryTimesByDate; }
    java.util.Map<LocalDate, java.util.List<Details>> getDetailsByDate(){ return detailsByDate; }

    Details getLatestDetailsFor(LocalDate d){
        java.util.List<Details> l = detailsByDate.get(d);
        if (l == null || l.isEmpty()) return null;
        Details best = null;
        for (Details it : l) {
            if (best == null) best = it;
            else if (it.ts != null && best.ts != null && it.ts.isAfter(best.ts)) best = it;
        }
        return best != null ? best : l.get(l.size()-1);
    }
}

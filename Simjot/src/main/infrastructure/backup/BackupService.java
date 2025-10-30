package main.infrastructure.backup;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import main.core.service.SettingsStore;
import main.infrastructure.io.AppDirectories;

/**
 * Background service that decides when to run backups based on user settings.
 */
public final class BackupService {
    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final long ONE_DAY_MS = 86_400_000L;
    private static final long ONE_WEEK_MS = 7 * ONE_DAY_MS;
    private static final long ONE_MONTH_MS = 30 * ONE_DAY_MS; // approx

    private static final BackupService INSTANCE = new BackupService();

    public static BackupService get() { return INSTANCE; }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BackupService");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> task;

    private BackupService() {}

    /** Start or restart periodic checks based on current settings. */
    public synchronized void start() {
        stop();
        SettingsStore store = SettingsStore.get();
        BackupManager.Frequency freq = BackupManager.parseFrequency(store.getBackupFrequency());
        if (freq == BackupManager.Frequency.OFF) return; // nothing to schedule
        // Check hourly to avoid CPU wake-ups; actual due decision based on last epoch.
        task = scheduler.scheduleAtFixedRate(this::checkAndRunIfDue, ONE_HOUR_MS, ONE_HOUR_MS, TimeUnit.MILLISECONDS);
        // Also perform an immediate check shortly after start
        scheduler.schedule(this::checkAndRunIfDue, 15, TimeUnit.SECONDS);
    }

    /** Stop the periodic checks. */
    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    /** Check current settings and time, run a backup if due. */
    public void checkAndRunIfDue() {
        try {
            SettingsStore store = SettingsStore.get();
            BackupManager.Frequency freq = BackupManager.parseFrequency(store.getBackupFrequency());
            if (freq == BackupManager.Frequency.OFF) { stop(); return; }

            long now = System.currentTimeMillis();
            long last = store.getLastBackupEpochMillis();
            long interval = switch (freq) {
                case DAILY -> ONE_DAY_MS;
                case WEEKLY -> ONE_WEEK_MS;
                case MONTHLY -> ONE_MONTH_MS;
                default -> Long.MAX_VALUE;
            };
            if (now - last >= interval) {
                runBackupNow();
            }
        } catch (Throwable ignored) {}
    }

    /** Force a backup attempt if settings are enabled, typically on exit. */
    public void triggerOnExit() {
        try {
            SettingsStore store = SettingsStore.get();
            boolean always = store.isBackupOnExitAlways();
            if (always) {
                runBackupNow();
                return;
            }
            if (BackupManager.parseFrequency(store.getBackupFrequency()) == BackupManager.Frequency.OFF) return;
            long now = System.currentTimeMillis();
            long last = store.getLastBackupEpochMillis();
            String freqStr = store.getBackupFrequency();
            long interval = (
                    "Weekly".equalsIgnoreCase(freqStr) ? ONE_WEEK_MS :
                    "Monthly".equalsIgnoreCase(freqStr) ? ONE_MONTH_MS : ONE_DAY_MS
            );
            if (now - last >= interval) {
                runBackupNow();
            }
        } catch (Throwable ignored) {}
    }

    /** Manual trigger regardless of frequency setting. Useful for "Backup Now" button. */
    public void triggerNow() {
        try { runBackupNow(); } catch (Throwable ignored) {}
    }

    private void runBackupNow() {
        try {
            File src = AppDirectories.getRoot();
            SettingsStore store = SettingsStore.get();
            String customDest = store.getBackupDestinationPath();
            File backupRoot = (customDest == null || customDest.isBlank())
                    ? new File(AppDirectories.folder(AppDirectories.Type.SETTINGS), "backups")
                    : new File(customDest);
            int keep = Math.max(1, store.getBackupKeepCount());
            int pruneDays = Math.max(0, store.getBackupPruneDays());
            boolean includeMood = store.isBackupIncludeMood();
            boolean includeSettings = store.isBackupIncludeSettings();
            boolean includeWallpapers = store.isBackupIncludeWallpapers();
            boolean verify = store.isBackupVerify();

            if (!backupRoot.exists()) backupRoot.mkdirs();
            BackupManager.performBackup(
                    src,
                    backupRoot,
                    keep,
                    pruneDays,
                    includeMood,
                    includeSettings,
                    includeWallpapers,
                    verify
            );

            store.setLastBackupEpochMillis(System.currentTimeMillis());
            store.save();
        } catch (Throwable ignored) {}
    }
}

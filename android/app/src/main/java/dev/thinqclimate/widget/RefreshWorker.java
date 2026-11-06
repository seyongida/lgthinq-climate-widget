package dev.thinqclimate.widget;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import dev.thinqclimate.core.Climate;
import java.util.concurrent.TimeUnit;

public final class RefreshWorker extends Worker {
    static final Object LOCK = new Object();
    public RefreshWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c,p); }
    @NonNull @Override public Result doWork() {
        Store store = new Store(getApplicationContext());
        final Climate.Config config;
        final String id;
        final String generation;
        synchronized (LOCK) {
            try { config = store.config(); }
            catch (Exception e) { store.error("설정 오류 · 앱에서 다시 연결해 주세요."); ClimateWidget.renderAll(getApplicationContext()); return Result.success(); }
            long now = System.currentTimeMillis();
            if (config == null || store.id().isEmpty() || (now >= store.attempt() && now-store.attempt() < 60_000)) {
                ClimateWidget.renderAll(getApplicationContext()); return Result.success();
            }
            id = store.id(); generation = store.generation(); store.attempt(now);
        }
        // Do not hold a UI-shared lock during network IO. Generation rejects late results after switching/disconnecting.
        Climate.Reading reading = null;
        String error = null;
        try { reading = new ThinQ(config).reading(id); }
        catch (Exception e) { error = e instanceof java.io.IOException ? e.getMessage() : "설정 오류 · 앱에서 다시 연결해 주세요."; }
        synchronized (LOCK) {
            if (generation.equals(store.generation())) {
                try { if (reading != null) store.reading(reading); else store.error(error); }
                catch (Exception e) { store.error("센서 정보 저장에 실패했습니다."); }
            }
            ClimateWidget.renderAll(getApplicationContext());
        }
        return Result.success(); // Next periodic tick retries. No aggressive retry loop on auth/rate errors.
    }
    static void schedule(Context c) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("climate-periodic", ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(RefreshWorker.class,30,TimeUnit.MINUTES).setConstraints(constraints).build());
    }
    static void refresh(Context c) {
        WorkManager.getInstance(c).enqueueUniqueWork("climate-now",ExistingWorkPolicy.KEEP,
            new OneTimeWorkRequest.Builder(RefreshWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }
}

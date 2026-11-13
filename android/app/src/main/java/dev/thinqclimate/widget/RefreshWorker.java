package dev.thinqclimate.widget;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import dev.thinqclimate.core.Climate;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class RefreshWorker extends Worker {
    static final Object LOCK = new Object();
    public RefreshWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c,p); }
    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        Store store = new Store(context);
        final Climate.Config config;
        final String generation;
        final List<Climate.Device> devices;
        if (store.demo()) {
            store.fillDemo(System.currentTimeMillis());
            ClimateWidget.renderAll(context); CycleWidget.renderAll(context); CycleNotifier.sync(context);
            checkAfterNextFinish(context, store);
            return Result.success();
        }
        synchronized (LOCK) {
            try { config = store.config(); }
            catch (Exception e) {
                for (Climate.Device d : store.devices()) store.error(d.id(),"연결 설정을 확인해 주세요");
                ClimateWidget.renderAll(context); return Result.success();
            }
            if (config == null) return Result.success();
            generation = store.generation(); devices = store.devices();
        }
        ThinQ api = new ThinQ(config);
        // Names can change in the ThinQ app, so refresh the list before reading states.
        try {
            List<Climate.Device> found = api.devices();
            synchronized (LOCK) {
                if (generation.equals(store.generation()) && !found.isEmpty()) {
                    store.catalog(found);
                    List<Climate.Device> merged = Climate.merge(store.devices(), found);
                    if (!merged.equals(store.devices())) store.renameDevices(merged);
                }
            }
        } catch (Exception ignored) { /* Keep the stored names until the next run. */ }
        final List<Climate.Device> current;
        synchronized (LOCK) { current = store.devices(); }
        for (Climate.Device d : current) {
            if (isStopped()) break;
            synchronized (LOCK) {
                if (!generation.equals(store.generation())) break;
                long now=System.currentTimeMillis(), attempt=store.attempt(d.id());
                if(now>=attempt && now-attempt<60_000) continue;
                store.attempt(d.id(),now);
            }
            Object state = null; String error = null;
            try { state = api.state(d.id()); }
            catch (Exception e) { error = e instanceof java.io.IOException ? e.getMessage() : "상태를 가져오지 못했습니다"; }
            synchronized (LOCK) {
                try { store.acceptStatus(generation,d.id(),state,System.currentTimeMillis(),error); }
                catch (Exception e) { if(generation.equals(store.generation())) store.error(d.id(),"센서 정보 저장 실패"); }
            }
            ClimateWidget.renderAll(context);
        }
        if (!isStopped()) refreshEnergy(store, api, current);
        ClimateWidget.renderAll(context);
        CycleWidget.renderAll(context);
        CycleNotifier.sync(context);
        checkAfterNextFinish(context, store);
        return Result.success();
    }
    /** Electricity use changes once a day, so each device is asked at most every 6 hours. */
    private void refreshEnergy(Store store, ThinQ api, List<Climate.Device> devices) {
        long now = System.currentTimeMillis();
        dev.thinqclimate.core.Energy.Window w = dev.thinqclimate.core.Energy.window(java.time.LocalDate.now());
        for (Climate.Device d : devices) {
            if (isStopped()) return;
            dev.thinqclimate.core.Energy.Usage old = store.energy(d.id());
            if (old != null && old.label().equals(w.label()) && now - old.fetchedAt() < 6 * 3_600_000L) continue;
            if (store.energySkip(d.id()) > now) continue;
            try { store.energy(d.id(), api.energy(d.id(), w)); }
            catch (ThinQ.ApiException e) {
                if (e.unsupported()) { store.energySkip(d.id(), now + 7 * 86_400_000L); store.clearEnergy(d.id()); }
                else if (e.notAllowed()) { for (Climate.Device x : devices) store.energySkip(x.id(), now + 86_400_000L); return; }
            } catch (Exception ignored) { /* Try again on a later run. */ }
        }
    }
    /** Checks again shortly after the soonest expected finish, so a finished cycle is noticed promptly. */
    static void checkAfterNextFinish(Context c, Store store) {
        long now = System.currentTimeMillis(), next = Long.MAX_VALUE;
        for (Climate.Device d : store.devices()) {
            dev.thinqclimate.core.Home.Status s = store.status(d.id()); if (s == null) continue;
            for (dev.thinqclimate.core.Home.Cycle cycle : s.cycles()) { Long end = s.endsAt(cycle); if (end != null && end > now) next = Math.min(next, end); }
        }
        if (next == Long.MAX_VALUE) return;
        WorkManager.getInstance(c).enqueueUniqueWork("climate-cycle-end", ExistingWorkPolicy.REPLACE,
            new OneTimeWorkRequest.Builder(RefreshWorker.class).setInitialDelay(next - now + 120_000, TimeUnit.MILLISECONDS)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }
    static void schedule(Context c) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("climate-periodic", ExistingPeriodicWorkPolicy.KEEP,
            new PeriodicWorkRequest.Builder(RefreshWorker.class,30,TimeUnit.MINUTES).setConstraints(constraints).build());
    }
    static void refresh(Context c) {
        WorkManager.getInstance(c).enqueueUniqueWork("climate-now",ExistingWorkPolicy.APPEND_OR_REPLACE,
            new OneTimeWorkRequest.Builder(RefreshWorker.class)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    }
}

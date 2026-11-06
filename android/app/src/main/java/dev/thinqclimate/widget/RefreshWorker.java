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
        for (Climate.Device d : devices) {
            if (isStopped()) break;
            synchronized (LOCK) {
                if (!generation.equals(store.generation())) break;
                long now=System.currentTimeMillis(), attempt=store.attempt(d.id());
                if(now>=attempt && now-attempt<60_000) continue;
                store.attempt(d.id(),now);
            }
            Climate.Reading reading = null; String error = null;
            try { reading = api.reading(d.id()); }
            catch (Exception e) { error = e instanceof java.io.IOException ? e.getMessage() : "센서 정보를 가져오지 못했습니다"; }
            synchronized (LOCK) {
                try { store.accept(generation,d.id(),reading,error); }
                catch (Exception e) { if(generation.equals(store.generation())) store.error(d.id(),"센서 정보 저장 실패"); }
            }
            ClimateWidget.renderAll(context);
        }
        ClimateWidget.renderAll(context);
        return Result.success();
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

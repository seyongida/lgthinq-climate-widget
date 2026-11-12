package dev.thinqclimate.widget;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import dev.thinqclimate.core.Climate;
import dev.thinqclimate.core.Home;
import java.util.*;

/**
 * Android's counterpart of the iOS Live Activity: an ongoing notification with a countdown for each running
 * program, visible on the lock screen. Only programs that really finished say so; anything else is removed.
 */
final class CycleNotifier {
    private static final String CHANNEL = "cycles";
    private CycleNotifier() {}
    static boolean permitted(Context c) {
        return Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    static void sync(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "가전 남은 시간", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("작동 중인 가전의 남은 시간을 잠금 화면과 알림창에 보여 줍니다.");
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
        Store store = new Store(c);
        boolean enabled = store.liveUpdates() && permitted(c);
        Set<String> shown = store.shownCycles(), next = new HashSet<>(), finished = new HashSet<>();
        PendingIntent open = PendingIntent.getActivity(c, 8, new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Map<String, String> titles = new HashMap<>();
        for (Climate.Device d : store.devices()) {
            Home.Status s = store.status(d.id()); if (s == null) continue;
            for (Home.Cycle cycle : s.cycles()) {
                String key = d.id() + "|" + (cycle.location() == null ? "" : cycle.location());
                String title = d.name() + (cycle.location() == null ? "" : " " + cycle.location());
                titles.put(key, title);
                if (cycle.phase() == Home.Phase.DONE) finished.add(key);
                if (!enabled || (cycle.phase() != Home.Phase.RUNNING && cycle.phase() != Home.Phase.PAUSED)) continue;
                Notification.Builder b = new Notification.Builder(c, CHANNEL).setSmallIcon(Ui.typeIcon(d.type())).setColor(Ui.GREEN)
                    .setContentTitle(title).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC).setContentIntent(open);
                Long end = s.endsAt(cycle);
                if (end != null && end > System.currentTimeMillis()) {
                    b.setContentText(cycle.label() + " · " + Ui.time(end) + " 완료 예정").setWhen(end).setShowWhen(true)
                        .setUsesChronometer(true).setChronometerCountDown(true);
                } else {
                    b.setContentText(cycle.remaining() == null ? cycle.label() : cycle.label() + " · " + Home.duration(cycle.remaining()) + " 남음").setShowWhen(false);
                }
                if (cycle.total() != null && cycle.remaining() != null) b.setProgress(cycle.total(), cycle.total() - cycle.remaining(), false);
                nm.notify(key.hashCode(), b.build());
                next.add(key);
            }
        }
        for (String key : shown) {
            if (next.contains(key)) continue;
            if (enabled && finished.contains(key)) {
                nm.notify(key.hashCode(), new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_check).setColor(Ui.GREEN)
                    .setContentTitle(titles.getOrDefault(key, "가전")).setContentText("끝났어요").setAutoCancel(true)
                    .setTimeoutAfter(15 * 60_000L).setContentIntent(open).build());
            } else nm.cancel(key.hashCode());
        }
        store.shownCycles(next);
    }
}

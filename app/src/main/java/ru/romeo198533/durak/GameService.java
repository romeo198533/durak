package ru.romeo198533.durak;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Партия по проводу живёт в службе, а не на экране.
 *
 * Человек уходит с экрана стола — свернул игру, погасил телефон, уехал в другое
 * окно, — а партия продолжается: за столом его ждёт живой соперник, и связь не
 * должна рваться оттого, что окно закрыли. Экран к партии можно вернуться, а
 * партия к экрану не привязана вовсе.
 *
 * Потоков для этого мало: Android умеет прибрать приложение в фоне, и вместе с
 * ним умрёт и связь. Поэтому, пока партия идёт, здесь поднята служба переднего
 * плана — с ней система приложение не убивает, а в шторке видно, что игра идёт,
 * и туда же можно вернуться одним касанием.
 *
 * Служба ничего не делает сама: она только держит приложение живым и показывает
 * возврат за стол. Вся партия — в {@link NetGame}, и его она не касается.
 */
public class GameService extends Service {

    private static final String CHANNEL = "durak-table";

    private static final int NOTE = 1;

    /** Поднять службу: с этой минуты приложение в фоне не прибирают. */
    public static void hold(Context context) {
        context.startForegroundService(new Intent(context, GameService.class));
    }

    /**
     * Снять службу: партия кончилась или экран ушёл совсем.
     *
     * Звать можно сколько угодно раз — повторное снятие ничего не делает.
     */
    public static void release(Context context) {
        context.stopService(new Intent(context, GameService.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Привязываться к службе нечем и незачем: она держит партию, а не
        // разговаривает с экраном. Экран берёт партию сам, из NetGame.
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        channel(this);
        try {
            startForeground(NOTE, note(this));
        } catch (RuntimeException refused) {
            // Система отказала в переднем плане — например, нашла в партии не
            // то, что разрешила. Терять из-за этого партию нельзя: связь живёт
            // в своих потоках, и играть можно и без службы. Хуже будет только
            // то, что Android волен прибрать приложение в фоне, — об этом и
            // предупредит журнал. А службу снять надо: поднятая, но не
            // передняя, она через несколько секунд уронила бы приложение сама.
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Партию ведёт не служба, а её потоки. Если система всё же прибрала
        // приложение и подняла службу заново, поднимать её без партии незачем.
        if (NetGame.live() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    /** Уведомление: что идёт партия и как вернуться за стол. */
    private static Notification note(Context context) {
        Intent back = new Intent(context, NetGameActivity.class);
        back.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent open = PendingIntent.getActivity(context, 0, back,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL)
                : new Notification.Builder(context);

        return builder
                .setContentTitle("Идёт партия в дурака")
                .setContentText("Нажми, чтобы вернуться за стол")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    /**
     * Канал уведомлений — тихий.
     *
     * Партия идёт молча: обо всём, что за столом, говорит голос игры, а не
     * шторка. Звук уведомления перебивал бы его на каждом ходу.
     */
    private static void channel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(CHANNEL, "Партия в дурака",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Пока партия идёт, игра остаётся в фоне");
        manager.createNotificationChannel(channel);
    }
}

package ru.romeo198533.durak;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Блютус: то, чем один телефон находит другой.
 *
 * Отсюда партия не видна вовсе — здесь только радио. Что с ним делать, решает
 * {@link Host} и {@link Guest}: они получают готовый {@link Link} и говорят по
 * нему ровно так же, как говорили по каналу в одной памяти.
 *
 * Спаривают телефоны в системных настройках, а не в игре. Так игра не
 * показывает незнакомых устройств и не просит разрешения на поиск — а
 * разрешений на Блютус у Android 12 и новее и без того хватает. Плата за это
 * одна: перед первой партией телефоны нужно свести в настройках, и игра об
 * этом скажет.
 *
 * Все вызовы, которые система пускает только с разрешением, помечены
 * {@code @SuppressLint("MissingPermission")}: проверка стоит не здесь, а в
 * {@link #allowed}, и звать их раньше неё нельзя.
 */
public final class Radio {

    /**
     * Признак игры: по нему один телефон узнаёт другого.
     *
     * Он зашит в приложение и не меняется никогда — иначе две версии игры не
     * нашли бы друг друга. Число выбрано произвольно; это не пароль и не
     * секрет, а просто имя службы, под которым игра видна на телефоне.
     */
    private static final UUID GAME = UUID.fromString("6f2a1c40-8a3b-4d7e-9c15-2b8f0d3a5e71");

    /** Как называется наша служба в системном списке. */
    private static final String SERVICE = "Дурак";

    /** Разрешение, которое Android 12 и новее спрашивает отдельно от всех. */
    public static final String CONNECT = Manifest.permission.BLUETOOTH_CONNECT;

    private Radio() {
    }

    public static BluetoothAdapter adapter(Context context) {
        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    /** Есть ли в телефоне блютус. */
    public static boolean present(Context context) {
        return adapter(context) != null;
    }

    /**
     * Выдано ли разрешение.
     *
     * До Android 12 такого разрешения нет — оно даётся при установке вместе со
     * всеми остальными, и спрашивать нечего.
     */
    public static boolean allowed(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return activity.checkSelfPermission(CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    /** Спросить разрешение. Ответ придёт в onRequestPermissionsResult. */
    public static void ask(Activity activity, int code) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.requestPermissions(new String[]{CONNECT}, code);
        }
    }

    /** Включён ли блютус. Спрашивать можно только с разрешением на руках. */
    @SuppressLint("MissingPermission")
    public static boolean on(Context context) {
        BluetoothAdapter adapter = adapter(context);
        return adapter != null && adapter.isEnabled();
    }

    /** Попросить систему включить блютус: диалог она показывает сама. */
    @SuppressLint("MissingPermission")
    public static Intent enableRequest() {
        return new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    }

    /**
     * Спаренные устройства — те, с которыми телефон уже знаком.
     *
     * Список отсортирован по именам, а не отдан в том порядке, в каком его
     * держит система: знакомое устройство ищут в списке каждый раз заново, и
     * порядок должен быть одним и тем же.
     */
    @SuppressLint("MissingPermission")
    public static List<BluetoothDevice> paired(Context context) {
        List<BluetoothDevice> out = new ArrayList<>();
        BluetoothAdapter adapter = adapter(context);
        if (adapter == null) return out;

        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded != null) out.addAll(bonded);

        out.sort((left, right) -> name(left).compareTo(name(right)));
        return out;
    }

    /** Имя устройства, а если система его не отдала — адрес. */
    @SuppressLint("MissingPermission")
    public static String name(BluetoothDevice device) {
        String name = device.getName();
        return name == null || name.isEmpty() ? device.getAddress() : name;
    }

    /**
     * Открыть стол: ждать, пока кто-нибудь подключится.
     *
     * Звать из своего потока: {@code accept} здесь ждёт. Экран в это время
     * живёт своей жизнью и говорит «ждём игрока».
     */
    @SuppressLint("MissingPermission")
    public static Waiting listen(BluetoothAdapter adapter) throws IOException {
        return new Waiting(adapter.listenUsingRfcommWithServiceRecord(SERVICE, GAME));
    }

    /**
     * Подключиться к столу.
     *
     * Служба выбирается по тому же признаку, по которому её открыли: среди
     * чужих служб на том конце телефон сам находит нужную.
     */
    @SuppressLint("MissingPermission")
    public static Link connect(BluetoothDevice device) throws IOException {
        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(GAME);
        try {
            socket.connect();
        } catch (IOException failed) {
            // Неудачное соединение оставляет сокет открытым, а он держит
            // силы телефона до конца партии. Закрываем сразу.
            try {
                socket.close();
            } catch (IOException ignored) {
                // закрываем после неудачи — второй беды не будет
            }
            throw failed;
        }
        return new BluetoothLink(socket, name(device));
    }

    /** Открытый на прослушивание стол: ждёт, пока кто-нибудь подключится. */
    public static final class Waiting {

        private final BluetoothServerSocket server;

        Waiting(BluetoothServerSocket server) {
            this.server = server;
        }

        /** Дождаться подключившегося. Звать из своего потока. */
        @SuppressLint("MissingPermission")
        public Link accept() throws IOException {
            BluetoothSocket socket = server.accept();
            return new BluetoothLink(socket, name(socket.getRemoteDevice()));
        }

        /**
         * Перестать ждать: {@code accept} проснётся и откажет.
         *
         * Так закрывают стол, если игрок ушёл с экрана, не дождавшись
         * второго, — иначе поток остался бы висеть навсегда.
         */
        public void close() {
            try {
                server.close();
            } catch (IOException ignored) {
                // уже закрыт — тем лучше
            }
        }
    }
}

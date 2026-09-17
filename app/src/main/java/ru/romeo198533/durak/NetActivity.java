package ru.romeo198533.durak;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;

/**
 * Игра вдвоём: открыть стол или сесть за чужой.
 *
 * Сначала разрешения и включённый Блютус — без них телефоны друг друга не
 * видят, и молчать об этом нельзя: нажатие выглядело бы как «кнопка не
 * работает». Поэтому экран сам говорит, чего не хватает, и сам же об этом
 * просит систему.
 *
 * Спаривают телефоны в системных настройках, а не здесь. Так игра не
 * показывает незнакомых устройств и не просит разрешения на поиск: она берёт
 * готовый список спаренных. Плата одна — перед первой партией телефоны нужно
 * свести в настройках, и экран об этом скажет прямо.
 *
 * Всё, что здесь происходит, — только связь. Партия заводится снаружи
 * ({@link NetGame}) и уезжает на экран стола: экрану стола всё равно, откуда
 * пришёл канал.
 */
public class NetActivity extends Activity {

    /** Просьба о разрешении на Блютус. */
    private static final int ASK_CONNECT = 1;

    /** Просьба включить Блютус. */
    private static final int ASK_ON = 2;

    private Button createButton;
    private Button joinButton;
    private TextView status;

    /** Открытый стол, пока его ждут: закрыть его можно только отсюда. */
    private volatile Radio.Waiting waiting;

    /** Экран ушёл: ждать больше некого, а связь придётся закрыть. */
    private volatile boolean left;

    /**
     * Просили ли уже систему о разрешении или о включении.
     *
     * Системный диалог уводит экран в паузу, а возвращение из него снова зовёт
     * {@link #ready} — и без этой заметки экран просил бы по кругу, пока
     * владелец не сдастся. Спрашиваем один раз за заход: не согласился — так
     * и написано на экране, и вернуться можно, зайдя снова.
     */
    private boolean asked;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Игра вдвоём");

        LinearLayout root = Skin.column(this);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 24));

        status = Skin.text(this, "", 17);
        root.addView(status, Skin.weight(1f));

        createButton = Skin.button(this, "Создать стол", "Создать стол. "
                + "Второй телефон подключится к тебе", 22);
        createButton.setOnClickListener(view -> create());
        root.addView(createButton, spaced());

        joinButton = Skin.button(this, "Подключиться", "Подключиться к столу "
                + "на другом телефоне", 22);
        joinButton.setOnClickListener(view -> join());
        root.addView(joinButton, spaced());

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        left = false;
        ready();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Экран ушёл — ждать на открытом столе больше некого: иначе поток
        // остался бы висеть на закрытом сокете до конца работы телефона.
        left = true;
        closeWaiting();
    }

    @Override
    protected void onDestroy() {
        closeWaiting();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] names, int[] granted) {
        super.onRequestPermissionsResult(code, names, granted);
        if (code == ASK_CONNECT) ready();
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == ASK_ON) ready();
    }

    // ----- чего не хватает -----

    /** Проверить, чем играть, и сказать об этом вслух. */
    private void ready() {
        if (!Radio.present(this)) {
            both(false);
            status.setText("В этом телефоне нет Блютуса. Играть вдвоём не получится — "
                    + "играй с программой.");
            return;
        }
        if (!Radio.allowed(this)) {
            both(false);
            status.setText("Разреши доступ к Блютусу: без него телефоны друг друга "
                    + "не видят.");
            if (!asked) {
                asked = true;
                Radio.ask(this, ASK_CONNECT);
            }
            return;
        }
        if (!Radio.on(this)) {
            both(false);
            status.setText("Блютус выключен. Включи его: без него телефоны друг "
                    + "друга не видят.");
            if (!asked) {
                asked = true;
                startActivityForResult(Radio.enableRequest(), ASK_ON);
            }
            return;
        }
        both(true);
        status.setText("Создай стол и жди второго игрока — или подключись к столу, "
                + "который открыли на другом телефоне.");
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams out = Skin.wide();
        out.setMargins(0, Skin.dp(this, 6), 0, Skin.dp(this, 6));
        return out;
    }

    private void both(boolean on) {
        createButton.setEnabled(on);
        joinButton.setEnabled(on);
    }

    /** Можно ли вообще пытаться связаться: разрешения и Блютус на месте. */
    private boolean allowed() {
        return Radio.present(this) && Radio.allowed(this) && Radio.on(this);
    }

    // ----- создать стол -----

    private void create() {
        if (!allowed()) {
            ready();
            return;
        }
        both(false);
        status.setText("Стол открыт. Жду, пока второй телефон подключится.");
        Ui.say(this, "Стол открыт. Жду второго игрока.");

        new Thread(() -> {
            try {
                Radio.Waiting open = Radio.listen(Radio.adapter(this));
                waiting = open;
                Link guest = open.accept();
                if (left) {
                    guest.close();
                    return;
                }
                // Раздача идёт здесь, а не на экране стола: стол уже должен
                // быть готов, когда второй телефон к нему подключился.
                ready(NetGame.open(Prefs.deck(this), System.nanoTime(), guest));
            } catch (IOException | RuntimeException stopped) {
                if (left) return;
                runOnUiThread(() -> failed("Стол открыть не удалось."));
            }
        }, "стол").start();
    }

    // ----- подключиться -----

    private void join() {
        if (!allowed()) {
            ready();
            return;
        }

        final List<BluetoothDevice> devices = Radio.paired(this);
        if (devices.isEmpty()) {
            Ask.explain(this, "Спаренных телефонов нет. Сведи телефоны в настройках "
                    + "Блютуса телефона — там же, где подключают наушники, — "
                    + "а потом возвращайся: игра берёт готовый список.");
            return;
        }

        String[] names = new String[devices.size()];
        for (int i = 0; i < names.length; i++) names[i] = Radio.name(devices.get(i));

        Ask.choose(this, "К какому телефону подключиться", names, -1,
                which -> connect(devices.get(which), names[which]));
    }

    private void connect(BluetoothDevice device, String name) {
        both(false);
        status.setText("Подключаюсь к " + name + "…");
        Ui.say(this, "Подключаюсь к " + name);

        new Thread(() -> {
            try {
                Link link = Radio.connect(device);
                if (left) {
                    link.close();
                    return;
                }
                ready(NetGame.join(link));
            } catch (IOException | RuntimeException stopped) {
                if (left) return;
                runOnUiThread(() -> failed("К " + name + " подключиться не удалось. "
                        + "Проверь, что на том телефоне стол уже открыт."));
            }
        }, "подключение").start();
    }

    // ----- что вышло -----

    /**
     * Связь есть — отдаём её экрану стола.
     *
     * Партия ложится в общую руку ({@link NetGame#leave}) и забирается уже
     * оттуда: сокет через Intent не передать. Положить её обязаны до ухода с
     * этого экрана — иначе связь пропадёт вместе с ним.
     */
    private void ready(NetGame game) {
        runOnUiThread(() -> {
            NetGame.leave(game);
            startActivity(new Intent(this, NetGameActivity.class));
        });
    }

    private void failed(String why) {
        both(true);
        status.setText(why);
        Ui.say(this, why);
    }

    private void closeWaiting() {
        Radio.Waiting open = waiting;
        waiting = null;
        if (open != null) open.close();
    }
}

package ru.romeo198533.durak;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Игра вместе: открыть стол или сесть за чужой.
 *
 * Чем связываться, решает владелец — Блютусом или вай-фаем. Блютус есть в любом
 * телефоне и не просит ни роутера, ни сети, но телефоны приходится свести в
 * системных настройках: игра берёт готовый список спаренных, а не показывает
 * незнакомые устройства и не просит разрешения на поиск. Вай-фай спаривания не
 * требует вовсе — довольно одной сети на двоих, — зато сеть эта должна быть.
 * Выбор здесь кнопкой и остаётся выбранным: за столом об этом думать поздно.
 *
 * Сначала разрешения и включённый Блютус — без них телефоны друг друга не
 * видят, и молчать об этом нельзя: нажатие выглядело бы как «кнопка не
 * работает». Поэтому экран сам говорит, чего не хватает, и сам же об этом
 * просит систему.
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

    /** Сколько ждать, пока объявление стола дойдёт до сети. */
    private static final long ANNOUNCE_WAIT_MS = 10000;

    /** Сколько искать чужие столы, прежде чем показать, что нашлось. */
    private static final long SEARCH_MS = 5000;

    private Button linkButton;
    private Button createButton;
    private Button joinButton;
    private TextView status;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Открытый стол, пока его ждут: закрыть его можно только отсюда. */
    private volatile Radio.Waiting waiting;

    /** То же самое, но по вай-фаю. */
    private volatile Lan.Waiting lanWaiting;

    /** Поиск чужих столов по вай-фаю: система держит его до перезапуска. */
    private volatile Lan.Discovery discovery;

    /** Что нашлось по вай-фаю. Трогается только из главного потока. */
    private final List<Lan.Discovered> found = new ArrayList<>();

    /** Экран ушёл: ждать больше некого, а связь придётся закрыть. */
    private volatile boolean left;

    /** Рабочие потоки: уходя с экрана, их надо разбудить. */
    private final List<Thread> workers = new ArrayList<>();

    /**
     * Просили ли уже систему о разрешении и о включении.
     *
     * Системный диалог уводит экран в паузу, а возвращение из него снова зовёт
     * {@link #ready} — и без этих заметок экран просил бы по кругу, пока
     * владелец не сдастся. Спрашиваем один раз за заход: не согласился — так и
     * написано на экране, и вернуться можно, зайдя снова.
     *
     * Заметок две, а не одна: разрешение и включение — разные просьбы, и общая
     * заметка на двоих означала бы, что вторая не прозвучит вовсе.
     */
    private boolean askedConnect;
    private boolean askedOn;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Игра вместе");

        LinearLayout root = Skin.column(this);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 24));

        status = Skin.text(this, "", 17);
        root.addView(status, Skin.weight(1f));

        linkButton = Skin.button(this, "", 18);
        linkButton.setOnClickListener(view -> switchLink());
        root.addView(linkButton, spaced());

        createButton = Skin.button(this, "Создать стол", "Создать стол. "
                + "Другие телефоны подключатся к тебе", 22);
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
        handler.removeCallbacksAndMessages(null);
        for (Thread worker : workers) worker.interrupt();
        workers.clear();
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

    // ----- чем играем -----

    /** Сменить связь: за столом об этом думать поздно, решают здесь. */
    private void switchLink() {
        int next = Prefs.link(this) == Prefs.LINK_WIFI
                ? Prefs.LINK_BLUETOOTH : Prefs.LINK_WIFI;
        Prefs.setLink(this, next);
        ready();
        String name = Prefs.LINK_NAMES[next];
        status.setText("Связь: " + name + ".");
        Ui.say(this, "Связь: " + name);
    }

    /** Надпись на кнопке связи — всегда про то, чем играют сейчас. */
    private void showLink() {
        int link = Prefs.link(this);
        String now = Prefs.LINK_NAMES[link];
        String other = Prefs.LINK_NAMES[link == Prefs.LINK_WIFI
                ? Prefs.LINK_BLUETOOTH : Prefs.LINK_WIFI];

        String words = "Связь: " + now;
        if (!now.equals(linkButton.getText().toString())) linkButton.setText(words);
        String spoken = words + ". Нажми, чтобы переключить на " + other;
        if (!spoken.equals(linkButton.getContentDescription())) {
            linkButton.setContentDescription(spoken);
        }
    }

    /** Есть ли в телефоне то, чем собираемся играть. */
    private boolean allowed() {
        // Вай-фаю разрешений не нужно: сеть либо есть, либо её нет, и об этом
        // скажет сама связь. Спрашивать у системы нечего.
        if (Prefs.link(this) == Prefs.LINK_WIFI) return true;
        return Radio.present(this) && Radio.allowed(this) && Radio.on(this);
    }

    // ----- чего не хватает -----

    /** Проверить, чем играть, и сказать об этом вслух. */
    private void ready() {
        showLink();

        if (Prefs.link(this) == Prefs.LINK_WIFI) {
            both(true);
            status.setText("Вай-фай. Создай стол и жди игроков — или найди стол, "
                    + "который открыли на другом телефоне. Телефоны должны быть "
                    + "в одной сети.");
            return;
        }

        if (!Radio.present(this)) {
            both(false);
            status.setText("В этом телефоне нет Блютуса. Выбери вай-фай — или играй "
                    + "с программой.");
            return;
        }
        if (!Radio.allowed(this)) {
            both(false);
            status.setText("Разреши доступ к Блютусу: без него телефоны друг друга "
                    + "не видят.");
            if (!askedConnect) {
                askedConnect = true;
                Radio.ask(this, ASK_CONNECT);
            }
            return;
        }
        if (!Radio.on(this)) {
            both(false);
            status.setText("Блютус выключен. Включи его: без него телефоны друг "
                    + "друга не видят.");
            if (!askedOn) {
                askedOn = true;
                startActivityForResult(Radio.enableRequest(), ASK_ON);
            }
            return;
        }
        both(true);
        status.setText("Блютус. Создай стол и жди игроков — или подключись к столу, "
                + "который открыли на другом телефоне. Телефоны должны быть "
                + "спарены заранее, в настройках.");
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

    // ----- создать стол -----

    private void create() {
        if (!allowed()) {
            ready();
            return;
        }
        Ask.choose(this, "Сколько за столом", new String[]{"На двоих", "На троих"}, -1,
                which -> openTable(which + 1));
    }

    /** @param guests сколько чужих телефонов ждём: 1 — партия на двоих, 2 — на троих. */
    private void openTable(int guests) {
        both(false);
        status.setText(guests == 1
                ? "Стол открыт. Жду второго игрока."
                : "Стол открыт. Жду двоих игроков.");
        Ui.say(this, guests == 1
                ? "Стол открыт. Жду второго игрока."
                : "Стол открыт. Жду двоих игроков.");

        final int link = Prefs.link(this);
        final String name = Prefs.name(this);

        worker(() -> {
            Link[] guestsLinks = null;
            try {
                // Раздача идёт здесь, а не на экране стола: стол уже должен
                // быть готов, когда к нему подключился последний игрок.
                guestsLinks = link == Prefs.LINK_WIFI
                        ? openWifi(guests)
                        : openBluetooth(guests);
                if (left) {
                    closeAll(guestsLinks);
                    return;
                }
                ready(NetGame.open(Prefs.deck(NetActivity.this), System.nanoTime(),
                        name, guestsLinks));
            } catch (IOException | RuntimeException stopped) {
                closeAll(guestsLinks);
                if (left) return;
                runOnUiThread(() -> failed("Стол открыть не удалось. "
                        + "Попробуй ещё раз."));
            }
        }, "стол");
    }

    /**
     * Ждать гостей по вай-фаю.
     *
     * Стол занимает порт сразу, но объявляется в сети не сразу — система
     * подтверждает это отдельно, и до подтверждения стол виден только на своём
     * телефоне. Поэтому ждём подтверждения, а уже потом зовём гостей.
     */
    private Link[] openWifi(int guests) throws IOException {
        final CountDownLatch announced = new CountDownLatch(1);
        final boolean[] visible = {false};

        Lan.Waiting open = Lan.open(this, Lan.deviceName(), new Lan.Ready() {
            @Override
            public void ready(String name) {
                visible[0] = true;
                announced.countDown();
            }

            @Override
            public void failed(String why) {
                announced.countDown();
            }
        });
        lanWaiting = open;

        try {
            boolean told;
            try {
                told = announced.await(ANNOUNCE_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                throw new IOException("стол не открылся");
            }
            if (!told || !visible[0]) throw new IOException("стол не виден в сети");

            return open.accept(guests);
        } finally {
            // Больше никто не придёт: за столом столько мест, сколько заказано.
            // Закрывать обязательно и здесь, а не только при уходе с экрана:
            // объявленный стол, оставшийся в сети, висел бы у соседей
            // призраком — найти можно, подключиться нечем, а лишний гость
            // подключался бы к нему и ждал ответа, которого уже не будет.
            open.close();
            if (lanWaiting == open) lanWaiting = null;
        }
    }

    /** Ждать гостей по Блютусу: по одному, сколько заказано. */
    private Link[] openBluetooth(int guests) throws IOException {
        Radio.Waiting open = Radio.listen(Radio.adapter(this));
        waiting = open;
        try {
            Link[] links = new Link[guests];
            for (int i = 0; i < guests; i++) links[i] = open.accept();
            return links;
        } finally {
            // Больше никто не придёт: за столом столько мест, сколько заказано.
            open.close();
            if (waiting == open) waiting = null;
        }
    }

    // ----- подключиться -----

    private void join() {
        if (!allowed()) {
            ready();
            return;
        }
        if (Prefs.link(this) == Prefs.LINK_WIFI) findWifi(); else findBluetooth();
    }

    private void findBluetooth() {
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
                which -> connectBluetooth(devices.get(which), names[which]));
    }

    /**
     * Искать чужие столы по вай-фаю.
     *
     * Поиск не отдаёт список разом: находки приходят по одной, и сколько их
     * будет — заранее не известно. Поэтому даём сети время и показываем то, что
     * успело найтись: сидеть над пустым списком, пока там что-то появится,
     * значило бы гадать, ищет игра или уже нет.
     */
    private void findWifi() {
        both(false);
        found.clear();
        status.setText("Ищу столы в сети…");
        Ui.say(this, "Ищу столы в сети");

        discovery = Lan.discover(this, new Lan.Finder() {
            @Override
            public void table(Lan.Discovered table) {
                found.add(table);
            }

            @Override
            public void gone(String name) {
                // Стол закрыли, пока мы искали: показывать его незачем — по
                // нему всё равно не подключиться.
                for (int i = found.size() - 1; i >= 0; i--) {
                    if (found.get(i).name().equals(name)) found.remove(i);
                }
            }

            @Override
            public void failed(String why) {
                // О неудаче поиска скажет его итог: своих слов у поиска нет.
            }
        });

        handler.postDelayed(this::offerWifi, SEARCH_MS);
    }

    /** Что нашлось — показать; не нашлось ничего — предложить поискать ещё. */
    private void offerWifi() {
        if (left) return;

        if (found.isEmpty()) {
            stopDiscovery();
            both(true);
            String said = "Столов не нашлось. Проверь, что оба телефона в одной сети "
                    + "и на том открыт стол.";
            status.setText(said);
            Ui.say(this, said);
            Ask.confirm(this, "Столов не нашлось. Искать ещё?", "Искать", "Отмена",
                    this::findWifi);
            return;
        }

        final List<Lan.Discovered> tables = new ArrayList<>(found);
        String[] names = new String[tables.size()];
        for (int i = 0; i < names.length; i++) names[i] = tables.get(i).name();

        Ask.choose(this, "К какому столу подключиться", names, -1,
                which -> connectWifi(tables.get(which)));
    }

    private void connectBluetooth(BluetoothDevice device, String name) {
        both(false);
        status.setText("Подключаюсь к " + name + "…");
        Ui.say(this, "Подключаюсь к " + name);

        final String mine = Prefs.name(this);
        worker(() -> {
            try {
                Link link = Radio.connect(device);
                if (left) {
                    link.close();
                    return;
                }
                ready(NetGame.join(link, mine));
            } catch (IOException | RuntimeException stopped) {
                if (left) return;
                runOnUiThread(() -> failed("К " + name + " подключиться не удалось. "
                        + "Проверь, что на том телефоне стол уже открыт."));
            }
        }, "подключение");
    }

    private void connectWifi(Lan.Discovered table) {
        stopDiscovery();
        both(false);
        status.setText("Подключаюсь к " + table.name() + "…");
        Ui.say(this, "Подключаюсь к " + table.name());

        final String mine = Prefs.name(this);
        worker(() -> {
            try {
                Link link = Lan.connect(table);
                if (left) {
                    link.close();
                    return;
                }
                ready(NetGame.join(link, mine));
            } catch (IOException | RuntimeException stopped) {
                if (left) return;
                runOnUiThread(() -> failed("К этому столу подключиться не удалось. "
                        + "Проверь, что он ещё открыт и оба телефона в одной сети."));
            }
        }, "подключение");
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
            stopDiscovery();

            // Прежняя партия, если она откуда-то осталась, закрывается здесь же:
            // две живые связи разом — это два стола, и вернуться можно только за
            // один, а второй остался бы держать телефон.
            NetGame previous = NetGame.live();
            if (previous != null) {
                previous.stop();
                NetGame.forget(previous);
            }

            NetGame.leave(game);
            startActivity(new Intent(this, NetGameActivity.class));
        });
    }

    private void failed(String why) {
        both(true);
        status.setText(why);
        Ui.say(this, why);
    }

    /** Завести рабочий поток: его придётся будить, когда экран уйдёт. */
    private void worker(Runnable work, String name) {
        Thread thread = new Thread(work, name);
        thread.setDaemon(true);
        workers.add(thread);
        thread.start();
    }

    private void closeAll(Link[] links) {
        if (links == null) return;
        for (Link link : links) {
            if (link != null) link.close();
        }
    }

    private void stopDiscovery() {
        Lan.Discovery running = discovery;
        discovery = null;
        if (running != null) running.stop();
    }

    private void closeWaiting() {
        stopDiscovery();
        handler.removeCallbacksAndMessages(null);

        Radio.Waiting radio = waiting;
        waiting = null;
        if (radio != null) radio.close();

        Lan.Waiting lan = lanWaiting;
        lanWaiting = null;
        if (lan != null) lan.close();
    }
}

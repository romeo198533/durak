package ru.romeo198533.durak;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Голос и речь: какой движок говорит, как он звучит и что вообще говорится.
 *
 * Взято из «Говорящего таймера для кухни» почти дословно: там этот экран уже
 * отлажен на живых телефонах, и повторять эти грабли незачем. Разница одна —
 * проба читает не блюдо, а карту: «семь пик, туз бубей».
 *
 * Сверху — кнопка с выбранным синтезатором: нажатие открывает список
 * установленных в телефоне, выбор сразу звучит пробной фразой. Рядом
 * проговаривание: говорить всё подряд или отвечать только на касание. Ниже
 * скорость, тон и громкость. Каждое значение показано кнопкой: нажатие на неё
 * читает ту же пробу, поэтому «поменял» и «услышал» — одно действие.
 *
 * Проговаривание стоит здесь, а не в общем списке настроек: оно про то же, про
 * речь, и в списке висело бы отдельным пунктом рядом с голосом, к которому и
 * так относится.
 *
 * Движки перечисляются отдельным синтезатором, который тут же и опускается:
 * иначе список установленных движков не получить.
 */
public class SpeechActivity extends Activity {

    /** Сколько ждать списка движков, прежде чем сказать, что его нет. */
    private static final long ENGINE_WAIT_MS = 3000L;

    private static final String[] SPEAK_NAMES = {
            "Проговаривать всё",
            "Только по касанию"};

    /** Проба: те же слова, что звучат за столом. */
    private static final String PROBE = "Ходишь: семь пик. Козырь — туз бубей.";

    /**
     * Системный экран синтезатора.
     *
     * Отдельной константы для него в Android нет, а имя действия у разных
     * прошивок своё, поэтому пробуем по очереди и честно говорим, если не вышло.
     */
    private static final String[] SYSTEM_TTS_SCREENS = {
            "com.android.settings.TTS_SETTINGS",
            "android.settings.TTS_SETTINGS",
    };

    private Button engineButton;
    private Button systemButton;
    private Button speakButton;
    private TextView engineHint;
    private Button speedValue;
    private Button speedDown;
    private Button speedUp;
    private Button pitchValue;
    private Button pitchDown;
    private Button pitchUp;
    private Button volumeValue;
    private Button volumeDown;
    private Button volumeUp;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Voice probe;
    private TextToSpeech lister;
    private boolean listFailed;

    /** Что нашлось в телефоне: пакет движка и его название, по порядку. */
    private final List<String> enginePackages = new ArrayList<>();
    private final List<String> engineLabels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Голос и речь");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Skin.column(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 24));

        engineButton = big(root);
        engineButton.setOnClickListener(view -> chooseEngine());
        engineHint = Skin.text(this, "", 15);
        root.addView(engineHint, Skin.wide());

        systemButton = big(root);
        systemButton.setText("Настройки синтезатора в телефоне");
        systemButton.setContentDescription("Настройки синтезатора в телефоне. "
                + "Там ставят и включают движки");
        systemButton.setOnClickListener(view -> openSystemTts());

        speakButton = big(root);
        speakButton.setOnClickListener(view -> pickSpeak());

        speedValue = big(root);
        LinearLayout speedRow = pairs(root);
        speedDown = pair(speedRow, "Медленнее", () -> bumpSpeed(false));
        speedUp = pair(speedRow, "Быстрее", () -> bumpSpeed(true));

        pitchValue = big(root);
        LinearLayout pitchRow = pairs(root);
        pitchDown = pair(pitchRow, "Ниже", () -> bumpPitch(false));
        pitchUp = pair(pitchRow, "Выше", () -> bumpPitch(true));

        volumeValue = big(root);
        LinearLayout volumeRow = pairs(root);
        volumeDown = pair(volumeRow, "Тише", () -> bumpVolume(false));
        volumeUp = pair(volumeRow, "Громче", () -> bumpVolume(true));

        TextView note = Skin.text(this, "Нажатие на значение читает пробную фразу: "
                + "меняют его соседние кнопки, а тут слышно, что получилось.", 14);
        note.setPadding(0, Skin.dp(this, 12), 0, 0);
        root.addView(note, Skin.wide());

        scroll.addView(root, Skin.wide());
        setContentView(scroll);

        // Нажатие на значение — не «изменить», а «послушать».
        speedValue.setOnClickListener(view -> probeSay(PROBE));
        pitchValue.setOnClickListener(view -> probeSay(PROBE));
        volumeValue.setOnClickListener(view -> probeSay(PROBE));

        loadEngines();
    }

    @Override
    protected void onResume() {
        super.onResume();
        update();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (lister != null) {
            lister.shutdown();
            lister = null;
        }
        if (probe != null) {
            probe.shutdown();
            probe = null;
        }
        super.onDestroy();
    }

    private Button big(LinearLayout root) {
        Button out = Skin.button(this, "", 17);
        LinearLayout.LayoutParams params = Skin.wide();
        params.setMargins(0, Skin.dp(this, 6), 0, Skin.dp(this, 6));
        root.addView(out, params);
        return out;
    }

    /** Полоса под парой кнопок. */
    private LinearLayout pairs(LinearLayout root) {
        LinearLayout out = Skin.row(this);
        root.addView(out, Skin.wide());
        return out;
    }

    /** Кнопка пары «в одну сторону»: ползунок слепой рукой не выставить. */
    private Button pair(LinearLayout row, String text, Runnable action) {
        Button out = Skin.button(this, text, 16);
        out.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = Skin.weight(1f);
        params.setMargins(Skin.dp(this, 2), 0, Skin.dp(this, 2), Skin.dp(this, 8));
        row.addView(out, params);
        return out;
    }

    // ----- движки -----

    /**
     * Список установленных синтезаторов.
     *
     * Готового способа «дай список, не поднимая движок» нет, поэтому поднимаем
     * временный синтезатор, забираем у него список и опускаем. Если движка в
     * телефоне нет вовсе, ответа не будет никогда — на этот случай ждём три
     * секунды и говорим об этом прямо, а не оставляем экран пустым.
     *
     * Без блока queries с TTS_SERVICE в манифесте этот список вернул бы один
     * движок по умолчанию — и выбор синтезатора выглядел бы пустым.
     */
    private void loadEngines() {
        lister = new TextToSpeech(this, status -> {
            TextToSpeech tts = lister;
            if (status == TextToSpeech.SUCCESS && tts != null) {
                collect(tts);
            } else {
                listFailed = true;
            }
            releaseLister();
            runOnUiThread(this::fillEngines);
        });
        handler.postDelayed(() -> {
            if (enginePackages.isEmpty() && !listFailed) {
                listFailed = true;
                releaseLister();
                fillEngines();
            }
        }, ENGINE_WAIT_MS);
    }

    private void collect(TextToSpeech tts) {
        try {
            List<TextToSpeech.EngineInfo> engines = tts.getEngines();
            if (engines != null) {
                for (TextToSpeech.EngineInfo info : engines) {
                    if (info == null || info.name == null) continue;
                    add(info.name, info.label);
                }
            }
            if (enginePackages.isEmpty()) {
                String fallback = tts.getDefaultEngine();
                if (fallback != null && !fallback.isEmpty()) add(fallback, null);
            }
        } catch (Exception broken) {
            // Список движков на некоторых прошивках отдаётся с ошибкой: не роняем
            // экран, а показываем хотя бы «как в системе».
            listFailed = true;
        }
    }

    private void add(String packageName, String label) {
        if (enginePackages.contains(packageName)) return;
        enginePackages.add(packageName);
        engineLabels.add(label == null || label.trim().isEmpty() ? packageName : label.trim());
    }

    private void releaseLister() {
        if (lister != null) {
            lister.shutdown();
            lister = null;
        }
    }

    private void fillEngines() {
        if (isFinishing() || isDestroyed()) return;
        if (listFailed && enginePackages.isEmpty()) {
            engineHint.setText("Список синтезаторов получить не удалось. "
                    + "Голос будет как в системе.");
            return;
        }
        String chosen = Prefs.engine(this);
        boolean found = chosen.isEmpty();
        for (String packageName : enginePackages) {
            if (packageName.equals(chosen)) found = true;
        }
        engineHint.setText(found
                ? "Выбранный синтезатор говорит и за столом, и в этом списке."
                : "Выбранный синтезатор «" + Prefs.engineLabel(this)
                        + "» больше не установлен. Выбери другой.");
    }

    /** Список движков: пункт «как в системе» и всё, что установлено. */
    private void chooseEngine() {
        List<String> names = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        names.add("Как в системе");
        packages.add("");
        for (int i = 0; i < enginePackages.size(); i++) {
            names.add(engineLabels.get(i));
            packages.add(enginePackages.get(i));
        }

        if (enginePackages.isEmpty()) {
            // Выбирать пока не из чего, но диалог всё равно открываем: в нём есть
            // «как в системе», а молчание на нажатие хуже всего.
            Ui.say(this, "Список синтезаторов ещё не готов.");
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Синтезатор речи")
                .setSingleChoiceItems(names.toArray(new String[0]),
                        packages.indexOf(Prefs.engine(this)),
                        (list, which) -> {
                            list.dismiss();
                            pickEngine(packages.get(which), names.get(which));
                        })
                .setNegativeButton("Отмена", null)
                .create();
        dialog.show();
    }

    private void pickEngine(String packageName, String label) {
        Prefs.setEngine(this, packageName, packageName.isEmpty() ? "" : label);
        update();
        fillEngines();
        // Голос у каждого движка свой, подменить его на лету нельзя: прежний
        // синтезатор опускаем и поднимаем новый.
        if (probe != null) {
            probe.shutdown();
            probe = null;
        }
        probeSay("Синтезатор: " + label + ". " + PROBE);
    }

    /** Системный экран синтезатора: там ставят и включают движки. */
    private void openSystemTts() {
        for (String action : SYSTEM_TTS_SCREENS) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (ActivityNotFoundException | SecurityException missing) {
                // Экран называется иначе — пробуем следующее имя.
            }
        }
        Ui.say(this, "Экран настроек синтезатора на этом телефоне открыть не удалось.");
    }

    // ----- скорость, тон, громкость -----

    private void bumpSpeed(boolean up) {
        int value = up ? Speech.speedUp(Prefs.speed(this)) : Speech.speedDown(Prefs.speed(this));
        Prefs.setSpeed(this, value);
        update();
        probeSay("Скорость: " + Speech.speedName(value) + ". " + PROBE);
    }

    private void bumpPitch(boolean up) {
        int value = up ? Speech.pitchUp(Prefs.pitch(this)) : Speech.pitchDown(Prefs.pitch(this));
        Prefs.setPitch(this, value);
        update();
        probeSay("Тон: " + Speech.pitchName(value) + ". " + PROBE);
    }

    private void bumpVolume(boolean up) {
        int value = up
                ? Speech.volumeUp(Prefs.volume(this))
                : Speech.volumeDown(Prefs.volume(this));
        Prefs.setVolume(this, value);
        update();
        probeSay("Громкость: " + Speech.volumeName(value) + ". " + PROBE);
    }

    /**
     * Что вообще говорится за столом.
     *
     * «Только по касанию» — для игры при людях: тогда о своём ходе сообщает
     * короткая вибрация, и никто, кроме владельца, её не слышит.
     */
    private void pickSpeak() {
        Ask.choose(this, "Что проговаривать", SPEAK_NAMES,
                Prefs.speakAll(this) ? 0 : 1, which -> {
                    Prefs.setSpeakAll(this, which == 0);
                    update();
                    Ui.say(this, SPEAK_NAMES[which]);
                });
    }

    private void update() {
        String label = Prefs.engineLabel(this);
        engineButton.setText("Синтезатор: " + label);
        engineButton.setContentDescription("Синтезатор: " + label
                + ". Нажми, чтобы открыть список установленных");

        String speak = SPEAK_NAMES[Prefs.speakAll(this) ? 0 : 1];
        speakButton.setText("Проговаривание: " + speak);
        speakButton.setContentDescription("Проговаривание: " + speak
                + ". Нажми, чтобы сменить");

        int speed = Prefs.speed(this);
        value(speedValue, "Скорость", Speech.speedName(speed));
        speedDown.setEnabled(speed > Speech.SPEED_MIN);
        speedUp.setEnabled(speed < Speech.SPEED_MAX);

        int pitch = Prefs.pitch(this);
        value(pitchValue, "Тон", Speech.pitchName(pitch));
        pitchDown.setEnabled(pitch > Speech.PITCH_MIN);
        pitchUp.setEnabled(pitch < Speech.PITCH_MAX);

        int volume = Prefs.volume(this);
        value(volumeValue, "Громкость", Speech.volumeName(volume));
        volumeDown.setEnabled(volume > Speech.VOLUME_MIN);
        volumeUp.setEnabled(volume < Speech.VOLUME_MAX);
    }

    private void value(Button button, String name, String text) {
        button.setText(name + ": " + text);
        button.setContentDescription(name + ": " + text + ". Нажми, чтобы послушать");
    }

    // ----- проверка -----

    private Voice probe() {
        if (probe == null) probe = new Voice(this, true);
        return probe;
    }

    /** Правка вступила в силу — говорим уже новым голосом. */
    private void probeSay(String text) {
        Voice voice = probe();
        voice.applySettings();
        voice.say(text);
    }
}

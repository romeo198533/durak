package ru.romeo198533.durak;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

/**
 * Голос и вибрация.
 *
 * Синтезатор речи готовится не сразу — движок поднимается отдельным процессом,
 * и первый вызов отвечает через секунду-другую. Раздача попадает ровно на этот
 * холодный старт, поэтому фразы, сказанные до готовности, не теряются, а ждут в
 * очереди и звучат, как только движок ожил.
 *
 * Вибрация заменяет голос там, где говорить нельзя: когда проговаривание
 * выключено и за столом есть кто-то ещё, о своём ходе сообщает короткий толчок —
 * его чувствует только владелец.
 *
 * Взято из «Говорящего таймера для кухни»: там этот класс отлажен на живых
 * телефонах, и переписывать его заново — верный способ потерять эти грабли.
 */
public final class Voice {

    /** Кому докладывать, что голос заговорил или закончил. */
    public interface Listener {
        void speaking(boolean speaking);
    }

    /** Короткая отметка — два толчка. */
    public static final long[] BUZZ_BOUNDARY = {0, 120, 100, 120};

    /** Отметка времени — один короткий. */
    public static final long[] BUZZ_TICK = {0, 60};

    /** Партия кончена — длинная дробь, её ни с чем не спутать. */
    public static final long[] BUZZ_FINISH = {0, 200, 150, 200, 150, 500};

    /**
     * Сколько примерно звучит фраза — по её длине.
     *
     * Нужно на случай, когда движок не сказал «договорил»: по этому сроку
     * приглушение снимается само. Русская речь — около четырнадцати знаков в
     * секунду, дальше запас на разгон движка.
     */
    private static final long SPEECH_BASE_MS = 1200L;
    private static final long SPEECH_PER_CHAR_MS = 90L;
    private static final long SPEECH_MAX_MS = 15000L;

    private final Context context;
    private final boolean speak;
    private final Queue<String> pending = new ArrayDeque<>();
    private TextToSpeech tts;
    private boolean ready;
    private boolean silent;

    /** Какой движок поднят сейчас: смена движка требует пересоздать синтезатор. */
    private String engine = "";

    /** Кому докладывать о начале и конце фразы. Ставится один раз при создании. */
    private Listener listener;
    private boolean speaking;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Снять приглушение, если движок так и не сказал, что договорил. */
    private final Runnable speechDone = () -> notifySpeaking(false);

    /** @param speak проговаривать ли вслух: выключено — остаётся только вибрация. */
    public Voice(Context context, boolean speak) {
        this.context = context.getApplicationContext();
        this.speak = speak;
        if (speak) start();
    }

    /** Кому сообщать о начале и конце фразы: звук на это время приглушается. */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void notifySpeaking(boolean value) {
        if (speaking == value) return;
        speaking = value;
        Listener current = listener;
        if (current != null) current.speaking(value);
    }

    private void start() {
        engine = Prefs.engine(context);
        // Выбранный владельцем движок или, если он не выбирал, тот, что выбран
        // в настройках телефона: у слепого пользователя это обычно один и тот
        // же синтезатор, что говорит экранным диктором.
        tts = engine.isEmpty()
                ? new TextToSpeech(context, this::onReady)
                : new TextToSpeech(context, this::onReady, engine);
    }

    /** Движок поднялся: настраиваем язык, звук и отдаём накопленные фразы. */
    private void onReady(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) return;
        int result = tts.setLanguage(new Locale("ru", "RU"));
        if (result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault());
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                notifySpeaking(true);
            }

            @Override
            public void onDone(String utteranceId) {
                speechFinished();
            }

            @Override
            public void onError(String utteranceId) {
                speechFinished();
            }

            // Движки зовут ошибку и по-старому, и по-новому: ловим оба вида,
            // иначе на ином телефоне звук остался бы приглушённым навсегда.
            @Override
            public void onError(String utteranceId, int errorCode) {
                speechFinished();
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                speechFinished();
            }
        });
        applySound();
        ready = true;
        synchronized (pending) {
            while (!pending.isEmpty()) speakNow(pending.poll());
        }
    }

    /** Скорость и тон — свойства самого движка: иначе их не задать. */
    private void applySound() {
        if (tts == null) return;
        tts.setSpeechRate(Speech.factor(Prefs.speed(context)));
        tts.setPitch(Speech.factor(Prefs.pitch(context)));
    }

    /**
     * Перечитать настройки голоса.
     *
     * Нужно тому, у кого свой синтезатор: он поднят при своём начале и о правке
     * на экране «Синтезатор речи» сам не узнает. Смена движка требует пересоздать
     * синтезатор — чужой голос на лету не подменить.
     */
    public void applySettings() {
        if (!speak) return;
        if (!Prefs.engine(context).equals(engine)) {
            shutdown();
            start();
            return;
        }
        if (ready) applySound();
    }

    /**
     * Сказать фразу. Пока движок не готов — фраза встаёт в очередь.
     *
     * @return возьмётся ли фраза: false — голос выключен или приглушён, и сказать
     *         её некому.
     */
    public boolean say(String text) {
        if (silent || !speak || text == null || text.isEmpty()) return false;
        synchronized (pending) {
            if (!ready) {
                if (pending.size() < 8) pending.add(text);
                return true;
            }
        }
        speakNow(text);
        return true;
    }

    /** Есть ли кому говорить: голос включён и не приглушён. */
    public boolean speaks() {
        return speak && !silent;
    }

    /**
     * Говорит ли голос сейчас или собирается.
     *
     * По этому решают, можно ли оборвать его ради срочного: оборвать фразу на
     * полуслове — значит не сказать самого главного. Считается и то, что ждёт в
     * очереди: движок поднимается не сразу, и фраза, сказанная на холодном
     * старте, ещё не начала звучать, но её уже нельзя терять.
     */
    public boolean talking() {
        synchronized (pending) {
            if (!pending.isEmpty()) return true;
        }
        return speaking;
    }

    private void speakNow(String text) {
        if (tts == null || silent) return;
        // Громкость читаем каждый раз, а не при настройке движка: она
        // единственная из трёх, которую движок запоминать не умеет.
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,
                Speech.factor(Prefs.volume(context)));
        // Звук уходит в тишину прямо сейчас, не дожидаясь отзыва движка:
        // отзовётся он или нет — фраза должна быть слышна.
        notifySpeaking(true);
        handler.removeCallbacks(speechDone);
        handler.postDelayed(speechDone, estimateMs(text));
        tts.speak(text, TextToSpeech.QUEUE_ADD, params, "durak");
    }

    /** Движок сказал, что фраза кончилась (или что не смог её сказать). */
    private void speechFinished() {
        handler.removeCallbacks(speechDone);
        notifySpeaking(false);
    }

    /** Сколько отведено фразе: по её длине, с запасом и потолком. */
    private static long estimateMs(String text) {
        long ms = SPEECH_BASE_MS + SPEECH_PER_CHAR_MS * text.length();
        return Math.min(ms, SPEECH_MAX_MS);
    }

    /**
     * Оборвать текущую фразу, но не замолкать насовсем.
     *
     * Нужно в момент, когда говорят о готовом: недосказанное «осталось десять
     * секунд» к делу уже не относится, а следом должно прозвучать «готово».
     */
    public void stopSpeaking() {
        synchronized (pending) {
            pending.clear();
        }
        if (tts != null) tts.stop();
        speechFinished();
    }

    /** Замолчать немедленно. */
    public void hush() {
        silent = true;
        synchronized (pending) {
            pending.clear();
        }
        if (tts != null) tts.stop();
        speechFinished();
    }

    public void shutdown() {
        ready = false;
        engine = "";
        speechFinished();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    /** Короткая вибрация без голоса. */
    public void buzz(long[] pattern) {
        vibrate(context, pattern);
    }

    public static void vibrate(Context context, long[] pattern) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
    }
}

package ru.romeo198533.durak;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Настройки игры.
 *
 * Всё, что Валера просил вынести на экран настроек, живёт здесь: голос,
 * проговаривание, внешний вид стола и правила. Экран настроек только
 * показывает и правит эти числа — ни одно из них не хранится в самом экране,
 * иначе настройка пропадала бы при повороте телефона.
 *
 * Названия ключей короткие и латинские: они уходят в файл на диске, и менять
 * их потом нельзя — иначе у того, кто уже играл, настройки обнулятся.
 */
public final class Prefs {

    private static final String FILE = "durak";

    private static final String KEY_ENGINE = "tts_engine";
    private static final String KEY_ENGINE_NAME = "tts_engine_name";
    private static final String KEY_SPEED = "tts_speed";
    private static final String KEY_PITCH = "tts_pitch";
    private static final String KEY_VOLUME = "tts_volume";

    private static final String KEY_SPEAK_ALL = "speak_all";
    private static final String KEY_MY_SIZE = "my_size";
    private static final String KEY_FOE_SIZE = "foe_size";
    private static final String KEY_TABLE_COLOR = "table_color";
    private static final String KEY_CARD_COLOR = "card_color";
    private static final String KEY_SUIT_COLOR = "suit_color";
    private static final String KEY_RANK_COLOR = "rank_color";

    private static final String KEY_DECK = "deck";
    private static final String KEY_PASSING = "passing";
    private static final String KEY_FOES = "foes";
    private static final String KEY_NAME = "my_name";
    private static final String KEY_LINK = "link";

    /** Размеры карт по возрастанию: от мелкого до очень крупного. */
    public static final int[] MY_SIZE_SP = {18, 26, 36, 48};
    public static final int[] FOE_SIZE_SP = {12, 16, 22, 30};
    public static final String[] SIZE_NAMES = {
            "мелкий", "средний", "крупный", "очень крупный"};

    /**
     * Сколько соперников за столом: один, двое или трое.
     *
     * За столом на троих и больше круг длиннее — подкидывает не один
     * заходящий, а каждый по очереди, — и правила одни и те же, поэтому
     * выбор простой: сколько программ посадить напротив.
     */
    public static final int[] FOES_ALLOWED = {1, 2, 3};
    public static final String[] FOES_NAMES = {"один", "двое", "трое"};

    private Prefs() {
    }

    private static SharedPreferences file(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ----- голос -----

    /** Пакет выбранного синтезатора. Пусто — «как в системе». */
    public static String engine(Context context) {
        return file(context).getString(KEY_ENGINE, "");
    }

    public static String engineName(Context context) {
        return file(context).getString(KEY_ENGINE_NAME, "");
    }

    public static void setEngine(Context context, String packageName, String label) {
        file(context).edit()
                .putString(KEY_ENGINE, packageName)
                .putString(KEY_ENGINE_NAME, label)
                .apply();
    }

    /**
     * Как назвать выбранный синтезатор вслух.
     *
     * Имени пакета здесь быть не должно: синтезатор без человеческого названия
     * прозвучал бы как «com.google.android.tts» — по буквам и без толку. Это
     * фраза для слуха, а не для глаза.
     */
    public static String engineLabel(Context context) {
        String name = engineName(context).trim();
        if (!name.isEmpty()) return name;
        return engine(context).isEmpty() ? "как в системе" : "выбранный синтезатор";
    }

    public static int speed(Context context) {
        return file(context).getInt(KEY_SPEED, Speech.SPEED_DEFAULT);
    }

    public static void setSpeed(Context context, int value) {
        file(context).edit().putInt(KEY_SPEED, value).apply();
    }

    public static int pitch(Context context) {
        return file(context).getInt(KEY_PITCH, Speech.PITCH_DEFAULT);
    }

    public static void setPitch(Context context, int value) {
        file(context).edit().putInt(KEY_PITCH, value).apply();
    }

    public static int volume(Context context) {
        return file(context).getInt(KEY_VOLUME, Speech.VOLUME_DEFAULT);
    }

    public static void setVolume(Context context, int value) {
        file(context).edit().putInt(KEY_VOLUME, value).apply();
    }

    // ----- проговаривание -----

    /**
     * Говорить ли всё, что происходит за столом.
     *
     * Выключено — игра молчит и отвечает только на касание пальцем: так
     * играют, когда рядом люди.
     */
    public static boolean speakAll(Context context) {
        return file(context).getBoolean(KEY_SPEAK_ALL, true);
    }

    public static void setSpeakAll(Context context, boolean value) {
        file(context).edit().putBoolean(KEY_SPEAK_ALL, value).apply();
    }

    // ----- внешний вид -----

    public static int mySize(Context context) {
        return clamp(file(context).getInt(KEY_MY_SIZE, 2), 0, MY_SIZE_SP.length - 1);
    }

    public static void setMySize(Context context, int value) {
        file(context).edit().putInt(KEY_MY_SIZE, value).apply();
    }

    public static int foeSize(Context context) {
        return clamp(file(context).getInt(KEY_FOE_SIZE, 1), 0, FOE_SIZE_SP.length - 1);
    }

    public static void setFoeSize(Context context, int value) {
        file(context).edit().putInt(KEY_FOE_SIZE, value).apply();
    }

    public static int tableColor(Context context) {
        return clamp(file(context).getInt(KEY_TABLE_COLOR, 3), 0, Palette.NAMES.length - 1);
    }

    public static void setTableColor(Context context, int value) {
        file(context).edit().putInt(KEY_TABLE_COLOR, value).apply();
    }

    public static int cardColor(Context context) {
        return clamp(file(context).getInt(KEY_CARD_COLOR, 0), 0, Palette.NAMES.length - 1);
    }

    public static void setCardColor(Context context, int value) {
        file(context).edit().putInt(KEY_CARD_COLOR, value).apply();
    }

    public static int suitColor(Context context) {
        return clamp(file(context).getInt(KEY_SUIT_COLOR, 5), 0, Palette.NAMES.length - 1);
    }

    public static void setSuitColor(Context context, int value) {
        file(context).edit().putInt(KEY_SUIT_COLOR, value).apply();
    }

    public static int rankColor(Context context) {
        return clamp(file(context).getInt(KEY_RANK_COLOR, 1), 0, Palette.NAMES.length - 1);
    }

    public static void setRankColor(Context context, int value) {
        file(context).edit().putInt(KEY_RANK_COLOR, value).apply();
    }

    // ----- правила -----

    /** 36 или 52 карты. */
    public static int deck(Context context) {
        int value = file(context).getInt(KEY_DECK, Cards.DECK_36);
        return value == Cards.DECK_52 ? Cards.DECK_52 : Cards.DECK_36;
    }

    public static void setDeck(Context context, int value) {
        file(context).edit().putInt(KEY_DECK, value).apply();
    }

    /** Переводной ли дурак. При одном сопернике переводить не на кого. */
    public static boolean passing(Context context) {
        return file(context).getBoolean(KEY_PASSING, false);
    }

    public static void setPassing(Context context, boolean value) {
        file(context).edit().putBoolean(KEY_PASSING, value).apply();
    }

    public static int foes(Context context) {
        return clamp(file(context).getInt(KEY_FOES, 1), 1, FOES_ALLOWED.length);
    }

    public static void setFoes(Context context, int value) {
        file(context).edit().putInt(KEY_FOES, value).apply();
    }

    // ----- имя за столом -----

    /**
     * Как меня зовут за столом.
     *
     * Имя уезжает другим игрокам: по нему за столом называют того, кто сдался,
     * и того, чей ход. Пусто — за столом скажут «Игрок» и номер места.
     */
    public static String name(Context context) {
        return file(context).getString(KEY_NAME, "");
    }

    public static void setName(Context context, String value) {
        file(context).edit().putString(KEY_NAME, clean(value)).apply();
    }

    /**
     * Имя, пригодное для провода.
     *
     * Точка с запятой и знак равенства развалили бы строку вида на полуслове, а
     * запятая — список имён за столом. Человеку незачем диктовать разделители, а
     * ошибку его глазами не видно: имя с запятой уехало бы обрывком. Поэтому
     * лишнее отсекается здесь, при сохранении, и дальше по коду едет уже
     * безопасное имя.
     */
    public static String clean(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && out.length() < 20; i++) {
            char symbol = value.charAt(i);
            boolean divider = symbol == ';' || symbol == '=' || symbol == ','
                    || symbol == '\n' || symbol == '\r' || symbol == '\t';
            out.append(divider ? ' ' : symbol);
        }
        return out.toString().trim().replaceAll(" {2,}", " ");
    }

    // ----- чем связываться -----

    /** Блютус: телефоны сводят в настройках, зато он есть везде и не нужен роутер. */
    public static final int LINK_BLUETOOTH = 0;

    /** Вай-фай: одна сеть на двоих и никакого спаривания. */
    public static final int LINK_WIFI = 1;

    public static final String[] LINK_NAMES = {"Блютус", "Вай-фай"};

    public static int link(Context context) {
        int value = file(context).getInt(KEY_LINK, LINK_BLUETOOTH);
        return value == LINK_WIFI ? LINK_WIFI : LINK_BLUETOOTH;
    }

    public static void setLink(Context context, int value) {
        file(context).edit().putInt(KEY_LINK, value).apply();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}

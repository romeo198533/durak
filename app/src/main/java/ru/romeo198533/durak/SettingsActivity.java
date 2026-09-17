package ru.romeo198533.durak;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Настройки: голос, проговаривание, вид стола и правила.
 *
 * Каждый пункт — кнопка, на которой написан текущий ответ. Так экран читается
 * сверху вниз одним движением: не нужно заходить в каждый пункт, чтобы узнать,
 * что там стоит. Рядом с названием всегда и то, что выбрано.
 *
 * Стол и карты настраиваются для слабовидящих: цвета выбираются названиями, а
 * пара «буквы на фоне» проверяется на разборчивость, чтобы вслепую не выставить
 * чёрное на чёрном и потом не гадать, куда пропали карты.
 */
public class SettingsActivity extends Activity {

    private static final String[] SPEAK_NAMES = {
            "Проговаривать всё",
            "Только по касанию"};
    private static final String[] PASSING_NAMES = {"Подкидной", "Переводной"};
    private static final String[] DECK_NAMES = {"36 карт", "52 карты"};

    private Button voiceButton;
    private Button speakButton;
    private Button mySizeButton;
    private Button foeSizeButton;
    private Button tableColorButton;
    private Button cardColorButton;
    private Button suitColorButton;
    private Button rankColorButton;
    private Button foesButton;
    private Button passingButton;
    private Button deckButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Настройки");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Skin.column(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 24));

        voiceButton = item(root, () -> startActivity(new Intent(this, SpeechActivity.class)));
        speakButton = item(root, this::pickSpeak);
        mySizeButton = item(root, this::pickMySize);
        foeSizeButton = item(root, this::pickFoeSize);
        tableColorButton = item(root, () -> pickColor(Colour.TABLE));
        cardColorButton = item(root, () -> pickColor(Colour.CARD));
        suitColorButton = item(root, () -> pickColor(Colour.SUIT));
        rankColorButton = item(root, () -> pickColor(Colour.RANK));
        foesButton = item(root, this::pickFoes);
        passingButton = item(root, this::pickPassing);
        deckButton = item(root, this::pickDeck);
        item(root, this::checkUpdate).setText("Обновление");

        scroll.addView(root, Skin.wide());
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    /**
     * Пункт настроек: кнопка во всю ширину.
     *
     * Кнопки, а не строки списка: до строки списка слепой палец доходит хуже, а
     * крупная кнопка отзывается и на промах рядом с собой.
     */
    private Button item(LinearLayout root, Runnable action) {
        Button out = Skin.button(this, "", 17);
        out.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = Skin.wide();
        params.setMargins(0, Skin.dp(this, 3), 0, Skin.dp(this, 3));
        root.addView(out, params);
        return out;
    }

    private void show() {
        String voice = Prefs.engineLabel(this) + ", " + Speech.speedName(Prefs.speed(this));
        label(voiceButton, "Голос", voice, "Нажми, чтобы выбрать синтезатор, скорость, тон и громкость");

        label(speakButton, "Проговаривание",
                SPEAK_NAMES[Prefs.speakAll(this) ? 0 : 1],
                "Нажми, чтобы выбрать: проговаривать всё или только по касанию");

        label(mySizeButton, "Размер моих карт",
                Prefs.SIZE_NAMES[Prefs.mySize(this)], "Нажми, чтобы сменить");
        label(foeSizeButton, "Размер карт соперника",
                Prefs.SIZE_NAMES[Prefs.foeSize(this)], "Нажми, чтобы сменить");

        colour(tableColorButton, "Фон стола", Prefs.tableColor(this));
        colour(cardColorButton, "Фон карты", Prefs.cardColor(this));
        colour(suitColorButton, "Цвет масти", Prefs.suitColor(this));
        colour(rankColorButton, "Цвет цифры", Prefs.rankColor(this));

        String foes = Prefs.FOES_NAMES[Prefs.foes(this) - 1];
        label(foesButton, "Соперников", foes, "Нажми, чтобы сменить");

        label(passingButton, "Дурак",
                PASSING_NAMES[Prefs.passing(this) ? 1 : 0], "Нажми, чтобы сменить");

        label(deckButton, "Колода",
                Prefs.deck(this) == Cards.DECK_52 ? DECK_NAMES[1] : DECK_NAMES[0],
                "Нажми, чтобы сменить");
    }

    private void label(Button button, String name, String value, String hint) {
        button.setText(name + ": " + value);
        button.setContentDescription(name + ": " + value + ". " + hint);
    }

    /**
     * Пункт цвета: кнопка и покрашена в этот цвет.
     *
     * Слабовидящему название ни о чём не говорит, а покрашенная кнопка говорит
     * сразу. Буквы на ней — цветом цифр: та же пара, что будет на картах.
     */
    private void colour(Button button, String name, int index) {
        int color = Palette.color(index);
        int letters = Palette.readable(Palette.color(Prefs.rankColor(this)), color)
                ? Palette.color(Prefs.rankColor(this))
                : (Palette.readable(0xFF000000, color) ? 0xFF000000 : 0xFFFFFFFF);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setTextColor(letters);
        label(button, name, Palette.name(index), "Нажми, чтобы сменить цвет");
        button.setTextColor(letters);
    }

    // ----- выбор -----

    private void pickSpeak() {
        Ask.choose(this, "Что проговаривать", SPEAK_NAMES,
                Prefs.speakAll(this) ? 0 : 1, which -> {
                    Prefs.setSpeakAll(this, which == 0);
                    show();
                    Ui.say(this, SPEAK_NAMES[which]);
                });
    }

    private void pickMySize() {
        Ask.choose(this, "Размер моих карт", Prefs.SIZE_NAMES,
                Prefs.mySize(this), which -> {
                    Prefs.setMySize(this, which);
                    show();
                    Ui.say(this, "Мои карты: " + Prefs.SIZE_NAMES[which]);
                });
    }

    private void pickFoeSize() {
        Ask.choose(this, "Размер карт соперника", Prefs.SIZE_NAMES,
                Prefs.foeSize(this), which -> {
                    Prefs.setFoeSize(this, which);
                    show();
                    Ui.say(this, "Карты соперника: " + Prefs.SIZE_NAMES[which]);
                });
    }

    /** Какой именно цвет меняем: у каждого своя пара «за чем следить». */
    private enum Colour { TABLE, CARD, SUIT, RANK }

    private void pickColor(Colour what) {
        Ask.choose(this, name(what), Palette.NAMES, current(what), which -> {
            set(what, which);
            show();
            Ui.say(this, name(what) + ": " + Palette.name(which) + ". " + warning(what));
        });
    }

    private String name(Colour what) {
        switch (what) {
            case TABLE: return "Фон стола";
            case CARD: return "Фон карты";
            case SUIT: return "Цвет масти";
            default: return "Цвет цифры";
        }
    }

    private int current(Colour what) {
        switch (what) {
            case TABLE: return Prefs.tableColor(this);
            case CARD: return Prefs.cardColor(this);
            case SUIT: return Prefs.suitColor(this);
            default: return Prefs.rankColor(this);
        }
    }

    private void set(Colour what, int index) {
        switch (what) {
            case TABLE: Prefs.setTableColor(this, index); break;
            case CARD: Prefs.setCardColor(this, index); break;
            case SUIT: Prefs.setSuitColor(this, index); break;
            default: Prefs.setRankColor(this, index); break;
        }
    }

    /**
     * Предупреждение о неразборчивой паре.
     *
     * Само по себе каждое из четырёх значений верное, а вместе они дают невидимые
     * карты. Поэтому проверяем не выбор, а то, что из него получилось.
     */
    private String warning(Colour what) {
        int card = Palette.color(Prefs.cardColor(this));
        int table = Palette.color(Prefs.tableColor(this));
        int rank = Palette.color(Prefs.rankColor(this));
        int suit = Palette.color(Prefs.suitColor(this));
        StringBuilder out = new StringBuilder();

        if (!Palette.readable(rank, card)) {
            out.append("Цифры на карте сливаются с её фоном. ");
        }
        if (!Palette.readable(suit, card)) {
            out.append("Масть на карте сливается с её фоном. ");
        }
        if (!Palette.readable(card, table)) {
            out.append("Карты сливаются со столом. ");
        }
        if (out.length() == 0) return "Разборчиво.";
        return out.toString();
    }

    private void pickFoes() {
        Ask.choose(this, "Сколько соперников за столом", Prefs.FOES_NAMES,
                Prefs.foes(this) - 1, which -> {
                    Prefs.setFoes(this, which + 1);
                    show();
                    Ui.say(this, "Соперников: " + Prefs.FOES_NAMES[which]);
                });
    }

    private void pickPassing() {
        Ask.choose(this, "Вид дурака", PASSING_NAMES, Prefs.passing(this) ? 1 : 0, which -> {
            if (which == 1 && Prefs.foes(this) < 2) {
                Ask.explain(this, "Переводной дурак — игра на троих и больше, "
                        + "а за столом пока один соперник. Как только соперников "
                        + "можно будет выбрать, переводной заработает.");
                return;
            }
            Prefs.setPassing(this, which == 1);
            show();
            Ui.say(this, "Дурак: " + PASSING_NAMES[which]);
        });
    }

    private void pickDeck() {
        Ask.choose(this, "Сколько карт в колоде", DECK_NAMES,
                Prefs.deck(this) == Cards.DECK_52 ? 1 : 0, which -> {
                    Prefs.setDeck(this, which == 1 ? Cards.DECK_52 : Cards.DECK_36);
                    show();
                    Ui.say(this, "Колода: " + DECK_NAMES[which]);
                });
    }

    private void checkUpdate() {
        Update.check(this);
    }
}

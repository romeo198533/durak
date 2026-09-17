package ru.romeo198533.durak;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Вид стола: чем покрашено и каким кеглем написано.
 *
 * Шесть пунктов — и все про то, как стол выглядит. Собраны вместе, потому что
 * настраивают их за один присест и проверяют друг за другом: выбрал цвет карт,
 * тут же смотришь, читаются ли на нём масти. В общем списке настроек они висели
 * шестью строками и оттесняли правила — теперь оттуда одна дверь сюда.
 *
 * Цвета выбираются названиями, а пара «буквы на фоне» проверяется на
 * разборчивость тут же: иначе вслепую можно выставить чёрное на чёрном и потом
 * гадать, куда пропали карты. Размеры только для слабовидящих — слепому кегль
 * безразличен, ему важно число карт, а не их величина.
 */
public class LookActivity extends Activity {

    private Button tableColorButton;
    private Button cardColorButton;
    private Button suitColorButton;
    private Button rankColorButton;
    private Button mySizeButton;
    private Button foeSizeButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Вид стола");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Skin.column(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 24));

        tableColorButton = item(root, () -> pick(Colour.TABLE));
        cardColorButton = item(root, () -> pick(Colour.CARD));
        suitColorButton = item(root, () -> pick(Colour.SUIT));
        rankColorButton = item(root, () -> pick(Colour.RANK));
        mySizeButton = item(root, this::pickMySize);
        foeSizeButton = item(root, this::pickFoeSize);

        scroll.addView(root, Skin.wide());
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    private Button item(LinearLayout root, Runnable action) {
        Button out = Skin.button(this, "", 17);
        out.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = Skin.wide();
        params.setMargins(0, Skin.dp(this, 3), 0, Skin.dp(this, 3));
        root.addView(out, params);
        return out;
    }

    private void show() {
        colour(tableColorButton, "Фон стола", Prefs.tableColor(this));
        colour(cardColorButton, "Фон карты", Prefs.cardColor(this));
        colour(suitColorButton, "Цвет масти", Prefs.suitColor(this));
        colour(rankColorButton, "Цвет цифры", Prefs.rankColor(this));

        label(mySizeButton, "Размер моих карт", Prefs.SIZE_NAMES[Prefs.mySize(this)]);
        label(foeSizeButton, "Размер карт соперника", Prefs.SIZE_NAMES[Prefs.foeSize(this)]);
    }

    private void label(Button button, String name, String value) {
        button.setText(name + ": " + value);
        button.setContentDescription(name + ": " + value + ". Нажми, чтобы сменить");
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
        label(button, name, Palette.name(index));
        button.setTextColor(letters);
    }

    // ----- выбор -----

    /** Какой именно цвет меняем: у каждого своя пара «за чем следить». */
    private enum Colour { TABLE, CARD, SUIT, RANK }

    private void pick(Colour what) {
        Ask.choose(this, name(what), Palette.NAMES, current(what), which -> {
            set(what, which);
            show();
            Ui.say(this, name(what) + ": " + Palette.name(which) + ". " + warning());
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
    private String warning() {
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
}

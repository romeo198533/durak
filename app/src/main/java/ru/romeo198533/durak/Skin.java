package ru.romeo198533.durak;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Сборка экранов и раскраска.
 *
 * Экраны собираются кодом, а не разметкой: почти всё здесь зависит от настроек —
 * размер карт, цвет стола, цвет масти, — и в разметке это превратилось бы в
 * десяток почти одинаковых файлов. Здесь же видно, что откуда берётся.
 *
 * Кнопки крупные и с запасом по высоте: слепой рукой в кнопку попадают не
 * глядя, и промах по соседней стоит хода в партии.
 */
public final class Skin {

    /** Высота обычной кнопки: палец находит её без прицеливания. */
    public static final int TOUCH_DP = 64;

    /** Высота главной кнопки экрана. */
    public static final int BIG_DP = 96;

    private Skin() {
    }

    // ----- контейнеры -----

    public static LinearLayout column(Activity a) {
        LinearLayout out = new LinearLayout(a);
        out.setOrientation(LinearLayout.VERTICAL);
        out.setBackgroundColor(Palette.color(Prefs.tableColor(a)));
        return out;
    }

    public static LinearLayout row(Activity a) {
        LinearLayout out = new LinearLayout(a);
        out.setOrientation(LinearLayout.HORIZONTAL);
        return out;
    }

    public static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    /** Распорка: прижимает всё, что ниже, к нижнему краю экрана. */
    public static View spacer(Activity a, float weight) {
        View out = new View(a);
        out.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, weight));
        return out;
    }

    // ----- кнопки -----

    public static Button button(Activity a, String text, int sp) {
        Button out = new Button(a);
        out.setText(text);
        out.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        out.setAllCaps(false);
        out.setGravity(Gravity.CENTER);
        out.setMinHeight(dp(a, TOUCH_DP));
        out.setMinimumHeight(dp(a, TOUCH_DP));
        out.setPadding(dp(a, 4), dp(a, 8), dp(a, 4), dp(a, 8));
        out.setBackgroundTintList(ColorStateList.valueOf(Palette.color(Prefs.cardColor(a))));
        out.setTextColor(Palette.color(Prefs.rankColor(a)));
        return out;
    }

    /**
     * Кнопка с подписью для диктора.
     *
     * Видимый текст и то, что читает TalkBack, — разные вещи: на карте написано
     * «семь пик», а вслух хочется услышать ещё и то, что с ней можно сделать.
     */
    public static Button button(Activity a, String text, String spoken, int sp) {
        Button out = button(a, text, sp);
        out.setContentDescription(spoken);
        return out;
    }

    /** Кнопка с картой: масть одним цветом, достоинство другим. */
    public static Button card(Activity a, Card card, int sp, int trump) {
        Button out = button(a, "", sp);
        out.setText(colored(a, card));
        // Без слова «карта»: на экране карт больше ничего нет, и повторять это
        // на каждой из шести подряд незачем — «шесть пик» и есть карта.
        String spoken = card.name();
        if (card.isTrump(trump)) spoken += ", козырная";
        out.setContentDescription(spoken);
        return out;
    }

    /**
     * Надпись на карте: достоинство и масть разными цветами.
     *
     * Цвета выбирает владелец — для слабовидящих. Слепому они безразличны,
     * но текст остаётся обычным текстом, и TalkBack читает его как читал:
     * рисовать карты картинками нельзя, иначе они онемеют.
     */
    public static SpannableString colored(Activity a, Card card) {
        String rank = Cards.rankShort(card.rank);
        String suit = Cards.suitSymbol(card.suit);
        // Достоинство строкой выше масти. Достоинство — буквой или цифрой,
        // как и было: «К» и «6» слабовидящий читает сразу, а слово целиком
        // заняло бы всю карту. Масть — знаком, а не буквой «п» или «т»: знак
        // узнаётся с одного взгляда, букву же приходится разгадывать. Вслух
        // при этом называется слово: слепому знак не нужен, ему говорят
        // «король треф».
        SpannableString out = new SpannableString(rank + "\n" + suit);
        out.setSpan(new ForegroundColorSpan(Palette.color(Prefs.rankColor(a))),
                0, rank.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(Palette.color(Prefs.suitColor(a))),
                rank.length() + 1, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }

    /** Подпись на колоде мельче самой карты: она поясняет, а не показывает. */
    private static final float DECK_LABEL_SCALE = 0.5f;

    /**
     * Лицо колоды: козырная карта и сколько карт осталось.
     *
     * Козырь видно с самой раздачи, а не только по касанию: раньше на колоде
     * стояло число, и какая масть козырь, слабовидящий узнавал, лишь тронув
     * её. Козырная карта и лежит поверх колоды — как её и кладут на столе.
     * Достоинство на ней буквой или цифрой и знак масти — как на картах в
     * руке, чтобы карта читалась одинаково и там и тут.
     *
     * Когда колода разобрана, козырной карты уже нет, но козырь остаётся:
     * тогда виден один знак масти.
     */
    public static SpannableString deckFace(Activity a, Card trump, int suit, int left) {
        String head = "Козырь\n";
        String rank = trump == null ? "" : Cards.rankShort(trump.rank);
        String gap = rank.isEmpty() ? "" : " ";
        String symbol = Cards.suitSymbol(trump == null ? suit : trump.suit);
        String tail = "\n" + Cards.count(left);
        String body = rank + gap + symbol;

        SpannableString out = new SpannableString(head + body + tail);
        int bodyStart = head.length();
        int symbolStart = bodyStart + rank.length() + gap.length();
        int bodyEnd = bodyStart + body.length();

        out.setSpan(new RelativeSizeSpan(DECK_LABEL_SCALE),
                0, bodyStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new RelativeSizeSpan(DECK_LABEL_SCALE),
                bodyEnd, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!rank.isEmpty()) {
            out.setSpan(new ForegroundColorSpan(Palette.color(Prefs.rankColor(a))),
                    bodyStart, symbolStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        out.setSpan(new ForegroundColorSpan(Palette.color(Prefs.suitColor(a))),
                symbolStart, bodyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }

    /** Строка текста на экране, размером в sp. */
    public static TextView text(Activity a, String value, int sp) {
        TextView out = new TextView(a);
        out.setText(value);
        out.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        out.setTextColor(Palette.color(Prefs.rankColor(a)));
        out.setGravity(Gravity.CENTER);
        out.setPadding(dp(a, 4), dp(a, 6), dp(a, 4), dp(a, 6));
        return out;
    }

    public static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }
}

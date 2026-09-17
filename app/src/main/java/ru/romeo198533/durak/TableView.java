package ru.romeo198533.durak;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Стол на экране: карты, кнопки и надписи.
 *
 * Ни партии, ни связи здесь нет — только рисование вида места и слова о нём.
 * Поэтому одиночная игра и игра вдвоём по Блютусу показывают одно и то же
 * одним и тем же кодом: разница между ними лишь в том, откуда приезжает вид и
 * куда уходит ход. Двух столов, которые могут разойтись на вид, не бывает.
 *
 * Раскладка повторяет настоящий стол, каким его нащупывают рукой: в самом низу —
 * свои карты, в самом верху — соперник, а посередине колода с козырем и по бокам
 * от неё «Начать заново» и «Бито». Над колодой две крупные кнопки — «Сдаться» и
 * «Беру»: обе на виду весь круг, потому что искать их в решительный момент
 * некогда, а «Бито» до поры объясняет, чего ещё не хватает.
 *
 * Пустого места за столом нет: свободную высоту делят между собой три полосы —
 * «Сдаться» с «Беру», ряд с колодой и полоса своих карт. Что выше и ниже них,
 * занимает ровно столько, сколько нужно.
 *
 * Свои карты идут одной полосой слева направо, как их и держат в руке. Полоса
 * длиннее экрана — она прокручивается вбок, и диктор сам подводит её к той карте,
 * до которой дошёл.
 */
final class TableView {

    /** Что экран делает, когда по нему тронули. Решает не он, а партия. */
    interface Listener {
        void onCard(Card card);

        void onTake();

        void onPass();

        void onSurrender();

        void onRestart();

        void onDeck();
    }

    /**
     * Уже этой карта не станет.
     *
     * 26dp — это шаг в 30dp с зазором: палец шириной около сантиметра ещё
     * различает соседние карты на ощупь. Тринадцать карт — самая большая рука,
     * какая бывает после крупной взятки, — при таком пределе влезают целиком.
     * Дальше ужимать уже нельзя: по карте не попасть, и вернётся прокрутка.
     */
    private static final int MIN_CARD_DP = 26;

    /** Мелче этого надпись на карте не читается. */
    private static final int MIN_CARD_SP = 14;

    private final Activity activity;

    /** Кому докладывать о касаниях. */
    private final Listener listener;

    /** Панель соперника и надпись стола: обе — просто надписи, не кнопки. */
    private final TextView foeButton;
    private final TextView tableText;
    private final Button takeButton;
    private final Button passButton;
    private final Button deckButton;
    private final Button restartButton;
    private final Button surrenderButton;
    private final LinearLayout handBox;
    private final View root;

    /** Последний показанный вид: из него берутся и карты, и слова о них. */
    private Seat seat;

    /** Козырь словами: колода кончится, а козырь останется. */
    private String trumpName = "";

    /** Каким кеглем написано у соперника: переставлять его каждый ход незачем. */
    private int foeSizeShown;

    /** Каким кеглем нарисована козырная карта на колоде. */
    private int deckSizeShown;

    /** Какие карты лежат внизу и каким кеглем: по этому и решается, пересобирать ли. */
    private String handKey = "";

    TableView(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;

        LinearLayout root = Skin.column(activity);
        root.setPadding(Skin.dp(activity, 8), Skin.dp(activity, 8),
                Skin.dp(activity, 8), Skin.dp(activity, 8));

        // Панель соперника — надпись, а не кнопка: она ничего не делает, а
        // кнопка добавляла бы к счёту «кнопка, двойное нажатие для активации».
        // По касанию диктор читает её как есть: «соперник, шесть карт».
        foeButton = Skin.text(activity, "", 18);
        foeButton.setFocusable(true);
        foeButton.setBackgroundColor(Palette.color(Prefs.cardColor(activity)));
        foeButton.setPadding(Skin.dp(activity, 4), Skin.dp(activity, 12),
                Skin.dp(activity, 4), Skin.dp(activity, 12));
        root.addView(foeButton, band(0f));

        // Поле, где лежат карты хода и отбоя. Не надпись в уголке, а своя полоса
        // во всю ширину и с весом: Валера просил сделать поле крупнее и отдать
        // ему место, а кнопки от этого сами сдвигаются чуть ниже. Фон — как у
        // карт, чтобы поле было видно и слабовидящему.
        tableText = Skin.text(activity, "", 22);
        tableText.setBackgroundColor(Palette.color(Prefs.cardColor(activity)));
        root.addView(tableText, band(1f));

        // Верхний ряд — «Сдаться» слева и «Беру» справа; «Бито» ушло вниз, в
        // ряд с колодой. Валера просил поменять кнопки местами, и эта пара —
        // той же правкой. Кегль у кнопки задаёт ряд, а не сама кнопка,
        // поэтому переехавшая вниз стала мельче.
        LinearLayout decisions = Skin.row(activity);
        surrenderButton = Skin.button(activity, "Сдаться", 34);
        surrenderButton.setOnClickListener(view -> listener.onSurrender());
        decisions.addView(surrenderButton, fill(1f));
        takeButton = Skin.button(activity, "Беру", 34);
        takeButton.setOnClickListener(view -> listener.onTake());
        decisions.addView(takeButton, fill(1f));
        root.addView(decisions, band(1.2f));

        LinearLayout middle = Skin.row(activity);

        restartButton = Skin.button(activity, "Начать\nзаново", 20);
        restartButton.setOnClickListener(view -> listener.onRestart());
        middle.addView(restartButton, fill(1f));

        deckButton = Skin.button(activity, "", 20);
        deckButton.setOnClickListener(view -> listener.onDeck());
        middle.addView(deckButton, fill(1.4f));

        passButton = Skin.button(activity, "Бито", 20);
        passButton.setOnClickListener(view -> listener.onPass());
        middle.addView(passButton, fill(1f));

        root.addView(middle, band(0.9f));

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setFillViewport(false);
        handBox = Skin.row(activity);
        handBox.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        scroll.addView(handBox);
        root.addView(scroll, band(1.1f));

        this.root = root;
    }

    View root() {
        return root;
    }

    /**
     * Полоса во всю ширину экрана.
     *
     * Вес 0 — полоса по содержимому: так стоят панель соперника и надпись
     * стола, которым расти некуда и незачем. Вес больше нуля — полоса делит с
     * остальными свободную высоту.
     */
    private LinearLayout.LayoutParams band(float weight) {
        LinearLayout.LayoutParams out = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                weight > 0f ? 0 : LinearLayout.LayoutParams.WRAP_CONTENT,
                weight);
        out.setMargins(0, Skin.dp(activity, 4), 0, Skin.dp(activity, 4));
        return out;
    }

    /**
     * Кнопка во всю высоту своей полосы.
     *
     * Высота не задаётся числом: полоса растянута, и кнопка должна тянуться
     * вместе с ней, иначе под ней останется полоса фона.
     */
    private LinearLayout.LayoutParams fill(float weight) {
        LinearLayout.LayoutParams out = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        out.setMargins(Skin.dp(activity, 4), 0, Skin.dp(activity, 4), 0);
        return out;
    }

    /** Козырь словами — то, чего не видно на карте, когда колода разобрана. */
    void trump(String name) {
        trumpName = name;
    }

    /**
     * Убрать «Начать заново».
     *
     * В партии по проводу раздать заново некому: колода лежит у того, кто
     * держит стол, и гость её не получит. Кнопки, которая ничего не может, на
     * экране быть не должно — её место делят колода и «Бито».
     *
     * Звать до первого показа: после перерисовки прятать кнопку поздно, и
     * диктор успел бы её прочитать.
     */
    void noRestart() {
        restartButton.setVisibility(View.GONE);
    }

    /**
     * Показать свежий вид.
     *
     * Всё пишется только тогда, когда меняется. Диктор читает вслух то, что
     * правится прямо сейчас, — и обновление нетронутой надписи обрывало бы
     * фразу о ходе соперника ровно посередине.
     */
    void show(Seat fresh) {
        seat = fresh;

        int foeSize = Prefs.FOE_SIZE_SP[Prefs.foeSize(activity)];
        if (foeSize != foeSizeShown) {
            foeSizeShown = foeSize;
            foeButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, foeSize);
        }

        // Козырная карта на колоде — того же размера, что карты в руке: она
        // и есть карта, и мельче своих ей быть незачем.
        int deckSize = Prefs.MY_SIZE_SP[Prefs.mySize(activity)];
        if (deckSize != deckSizeShown) {
            deckSizeShown = deckSize;
            deckButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, deckSize);
        }

        // Надписи без описаний: диктор читает ровно то, что написано. Описания
        // остаются там, где на экране написано короче, чем нужно на слух, —
        // у колоды и у карт.
        plain(foeButton, "Соперник, " + Cards.count(foeCount()));
        plain(tableText, tableWords());
        // На колоде лежит козырная карта — её и видно, а число карт под ней.
        plain(deckButton, Skin.deckFace(activity, seat.trumpCard, seat.trump, seat.deckCount));

        describe(deckButton, deckWords() + " Козырь — " + trumpName);

        // У «Бито» описания нет намеренно, и меняться на ходу оно не должно:
        // диктор читает изменившуюся подпись той кнопки, на которой стоит, — и
        // сразу после своего хода Валера слышал «пока нельзя, на столе есть
        // неотбитая карта» вместо хода соперника. Почему нельзя, «Бито»
        // объясняет только тогда, когда её нажали впустую.
        showHand();
    }

    /** У соперника за столом на двоих одно место — то, которое не моё. */
    private int foeCount() {
        for (int place = 0; place < seat.handCounts.length; place++) {
            if (place != seat.me) return seat.handCounts[place];
        }
        return 0;
    }

    /**
     * Надпись без описания: диктор читает ровно то, что написано.
     *
     * Описания у таких надписей нет вовсе, поэтому здесь только текст — трогать
     * описание на каждом ходу значило бы дёргать его вхолостую и заставлять
     * диктора перечитывать кнопку, на которой он стоит.
     */
    private void plain(TextView view, CharSequence text) {
        // Сравниваются строки, а не сами последовательности: у раскрашенной
        // надписи с палитрой сравнивать нечего — две одинаковые на вид строки
        // остаются разными объектами, и надпись переставлялась бы вхолостую,
        // заставляя диктора перечитывать кнопку под пальцем.
        if (!text.toString().equals(view.getText().toString())) view.setText(text);
    }

    private void describe(TextView view, CharSequence spoken) {
        if (!spoken.equals(view.getContentDescription())) {
            view.setContentDescription(spoken);
        }
    }

    /**
     * Колода словами: «колода, 24 карты».
     *
     * Без «в ней»: считают карты в колоде, а не что-то внутри неё, и лишнее
     * слово тут только удлиняет фразу. Одними и теми же словами говорит и
     * нажатие, и диктор при касании — иначе одна и та же колода звучала бы
     * по-разному в зависимости от того, чем до неё дотронулись.
     */
    String deckWords() {
        if (seat == null) return "Колода.";
        if (seat.deckCount == 0) return "Колода пуста.";
        return "Колода, " + Cards.count(seat.deckCount) + ".";
    }

    /**
     * Стол словами: чем зашли и чем отбито.
     *
     * Пара читается как одно: «шесть пик бито семь пик» — зашли шестёркой, бито
     * семёркой. Неотбитая карта называется одна, без пометки: лишнее слово про
     * «ещё не бито» только мешает слушать, а что она одна — и так слышно,
     * второго имени за ней не идёт.
     */
    String tableWords() {
        if (seat == null) return "На столе пусто";
        List<Seat.Pair> slots = seat.table;
        if (slots.isEmpty()) return "На столе пусто";
        StringBuilder out = new StringBuilder();
        for (Seat.Pair slot : slots) {
            if (out.length() > 0) out.append(", ");
            out.append(slot.attack.name());
            if (slot.beaten()) out.append(" бито ").append(slot.defend.name());
        }
        return out.toString();
    }

    /**
     * Свои карты внизу, одной полосой слева направо.
     *
     * Порядок тот же, что у диктора: как идут, так и читаются. Козыри — в конце
     * списка, их ищут отдельно и находят сразу.
     *
     * Ширина карты считается от кегля, а не берётся от веса: внутри полосы,
     * которая шире экрана, вес делить не от чего. Двузначное достоинство при
     * самом крупном кегле должно оставаться целым.
     *
     * Но от кегля — только пока карты помещаются. Взятка на десять карт в
     * экран не влезала: половина руки оставалась за краем, и до неё ещё надо
     * было догадаться прокрутить. Поэтому карт на руке больше — карта мельче,
     * и вся рука остаётся на виду разом. Вместе с шириной уменьшается и
     * надпись: иначе достоинство обрезалось бы по краям карты.
     */
    private void showHand() {
        // Рука уже пришла по порядку показа — раскладывать её здесь незачем.
        List<Card> cards = seat.hand;
        int sp = Prefs.MY_SIZE_SP[Prefs.mySize(activity)];

        // Пересобираем, только если карты и правда изменились. Иначе диктор
        // после каждого хода соперника терял бы карту, на которой стоял, и
        // возвращался бы в начало полосы — а на карту ещё нужно попасть.
        // Цвета тоже в ключе: их меняют в настройках, и вернувшись за стол,
        // карты должны быть уже перекрашены. Число карт в ключе есть — оно
        // в списке, — значит и ширина пересчитается сама.
        String key = sp + "|" + Prefs.cardColor(activity) + "|" + Prefs.suitColor(activity)
                + "|" + Prefs.rankColor(activity) + "|" + Cards.list(cards);
        if (key.equals(handKey)) return;
        handKey = key;

        handBox.removeAllViews();

        if (cards.isEmpty()) {
            handBox.addView(Skin.text(activity, "Карт нет", sp), wrap());
            return;
        }

        // Сколько остаётся на карту, если разложить всю руку по ширине
        // экрана: поля корня по бокам и зазоры между картами — врозь.
        int natural = Skin.dp(activity, Math.max(Skin.TOUCH_DP, Math.round(sp * 1.6f)));
        int screen = activity.getResources().getDisplayMetrics().widthPixels;
        int room = screen - Skin.dp(activity, 16) - Skin.dp(activity, 4) * cards.size();
        int width = Math.min(natural,
                Math.max(Skin.dp(activity, MIN_CARD_DP), room / cards.size()));
        int sizeSp = Math.min(sp, fitSp(width));

        for (Card card : cards) {
            Button button = Skin.card(activity, card, sizeSp, seat.trump);
            button.setOnClickListener(view -> listener.onCard(card));
            // Во всю высоту полосы: полоса растянута до самого низа экрана, и
            // карта должна заполнять её целиком, а не висеть в ней островком.
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    width, LinearLayout.LayoutParams.MATCH_PARENT);
            params.setMargins(Skin.dp(activity, 2), Skin.dp(activity, 2),
                    Skin.dp(activity, 2), Skin.dp(activity, 2));
            handBox.addView(button, params);
        }
    }

    /**
     * Самый крупный кегль, при котором надпись ещё помещается в карту.
     *
     * В достоинстве бывает две цифры («10»), и они шире всего остального на
     * карте. Отсюда и счёт: две цифры — примерно 1,15 кегля, плюс поля по
     * бокам. Ниже предела не опускаемся: совсем мелкую карту и на ощупь не
     * найти, и прочитать нельзя.
     */
    private int fitSp(int widthPx) {
        float density = activity.getResources().getDisplayMetrics().density;
        int sp = Math.round((widthPx / density - 8f) / 1.15f);
        return Math.max(MIN_CARD_SP, sp);
    }

    /** Размер по содержимому: для полосы, которая сама шире экрана. */
    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /** Карты разложены заново — руку можно пересобирать, не боясь сбить диктора. */
    void forgetHand() {
        handKey = "";
    }

}

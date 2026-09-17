package ru.romeo198533.durak;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Игровой стол.
 *
 * Раскладка повторяет настоящий стол, каким его нащупывают рукой: в самом низу —
 * свои карты, в самом верху — соперник, а посередине колода с козырем и по бокам
 * от неё «Начать заново» и «Беру». Над колодой две крупные кнопки — «Сдаться» и
 * «Бито»: обе на виду весь круг, потому что искать их в решительный момент
 * некогда, а «Бито» до поры объясняет, чего ещё не хватает.
 *
 * Пустого места за столом нет: свободную высоту делят между собой три полосы —
 * «Сдаться» с «Бито», ряд с колодой и полоса своих карт. Что выше и ниже них,
 * занимает ровно столько, сколько нужно. Свои карты при этом стоят в самом низу,
 * где рука их и ищет.
 *
 * Свои карты идут одной полосой слева направо, как их и держат в руке. Полоса
 * длиннее экрана — она прокручивается вбок, и диктор сам подводит её к той карте,
 * до которой дошёл.
 *
 * Экран рисует не партию, а {@link Seat} — вид места, — и ходы шлёт на стол
 * через {@link Wire}. Поэтому он же будет работать и в сетевой игре: там
 * поменяется только то, откуда берётся вид и куда уходит ход.
 */
public class GameActivity extends Activity {

    /** Человек всегда первый, программа — вторая. */
    private static final int HUMAN = 0;
    private static final int FOE = 1;

    /** Пауза перед ходом соперника: чтобы его фраза не наезжала на свою. */
    private static final long FOE_PAUSE_MS = 1200L;

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

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Table table;
    private Bot bot;
    private Voice voice;

    /** Моё место за столом. Всё, что видно на экране, берётся отсюда. */
    private Seat seat;

    /** Проговаривать ли ход соперника сам по себе или только по касанию. */
    private boolean speakAll;

    /** Козырь словами: колода кончится, а козырь останется. */
    private String trumpName = "";

    /** Панель соперника и надпись стола: обе — просто надписи, не кнопки. */
    private TextView foeButton;
    private TextView tableText;
    private Button takeButton;
    private Button passButton;
    private Button deckButton;
    private Button restartButton;
    private Button surrenderButton;
    private LinearLayout handBox;
    private boolean finished;

    /** Каким кеглем написано у соперника: переставлять его каждый ход незачем. */
    private int foeSizeShown;

    /** Каким кеглем нарисована козырная карта на колоде. */
    private int deckSizeShown;

    /** Какие карты лежат внизу и каким кеглем: по этому и решается, пересобирать ли. */
    private String handKey = "";

    /** Ход соперника: одно движение, потом передышка и снова он же или человек. */
    private final Runnable foeStep = new Runnable() {
        @Override
        public void run() {
            stepFoe();
            advance();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        voice = new Voice(this, true);
        speakAll = Prefs.speakAll(this);
        build();
        deal();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (voice != null) {
            voice.shutdown();
            voice = null;
        }
        super.onDestroy();
    }

    /** Отпустить кнопку «назад» домой, а не в никуда. */
    @Override
    public void onBackPressed() {
        Ask.confirm(this, "Выйти из партии?", "Выйти", "Остаться", () -> finish());
    }

    // ----- экран -----

    /**
     * Собрать стол.
     *
     * Свободную высоту делят три полосы — «Сдаться» с «Бито», ряд с колодой и
     * полоса своих карт. Распорок здесь нет намеренно: распорка — это пустое
     * место, в котором палец не находит ничего, а Валера просил, чтобы за
     * столом был занят весь экран. Полосы с весом тянутся каждая на свою долю,
     * и пустоты не остаётся ни при каком размере экрана.
     *
     * «Сдаться» и «Бито» — самая крупная доля: в решительный момент по ним бьют
     * не целясь, и промах стоит хода.
     */
    private void build() {
        LinearLayout root = Skin.column(this);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8));

        // Панель соперника — надпись, а не кнопка: она ничего не делает, а
        // кнопка добавляла бы к счёту «кнопка, двойное нажатие для активации».
        // По касанию диктор читает её как есть: «соперник, шесть карт».
        foeButton = Skin.text(this, "", 18);
        foeButton.setFocusable(true);
        foeButton.setBackgroundColor(Palette.color(Prefs.cardColor(this)));
        foeButton.setPadding(Skin.dp(this, 4), Skin.dp(this, 12),
                Skin.dp(this, 4), Skin.dp(this, 12));
        root.addView(foeButton, band(0f));

        // Поле, где лежат карты хода и отбоя. Не надпись в уголке, а своя полоса
        // во всю ширину и с весом: Валера просил сделать поле крупнее и отдать
        // ему место, а кнопки от этого сами сдвигаются чуть ниже. Фон — как у
        // карт, чтобы поле было видно и слабовидящему.
        tableText = Skin.text(this, "", 22);
        tableText.setBackgroundColor(Palette.color(Prefs.cardColor(this)));
        root.addView(tableText, band(1f));

        // «Сдаться» стоит слева в верхнем ряду, а «Беру» ушло вниз, в ряд с
        // колодой: Валера просил поменять их местами. Кегль у кнопки от
        // переезда не меняется — его задаёт ряд, а не сама кнопка.
        LinearLayout decisions = Skin.row(this);
        surrenderButton = Skin.button(this, "Сдаться", 34);
        surrenderButton.setOnClickListener(view -> askSurrender());
        decisions.addView(surrenderButton, fill(1f));
        passButton = Skin.button(this, "Бито", 34);
        passButton.setOnClickListener(view -> tapPass());
        decisions.addView(passButton, fill(1f));
        root.addView(decisions, band(1.2f));

        LinearLayout middle = Skin.row(this);

        restartButton = Skin.button(this, "Начать\nзаново", 20);
        restartButton.setOnClickListener(view -> askRestart());
        middle.addView(restartButton, fill(1f));

        deckButton = Skin.button(this, "", 20);
        deckButton.setOnClickListener(view -> sayDeck());
        middle.addView(deckButton, fill(1.4f));

        takeButton = Skin.button(this, "Беру", 20);
        takeButton.setOnClickListener(view -> tapTake());
        middle.addView(takeButton, fill(1f));

        root.addView(middle, band(0.9f));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setFillViewport(false);
        handBox = Skin.row(this);
        handBox.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        scroll.addView(handBox);
        root.addView(scroll, band(1.1f));

        setContentView(root);
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
        out.setMargins(0, Skin.dp(this, 4), 0, Skin.dp(this, 4));
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
        out.setMargins(Skin.dp(this, 4), 0, Skin.dp(this, 4), 0);
        return out;
    }

    // ----- партия -----

    private void deal() {
        handler.removeCallbacks(foeStep);
        finished = false;
        handKey = "";
        table = new Table(Prefs.deck(this), System.nanoTime());
        bot = new Bot(FOE, System.nanoTime() ^ 0x2545F4914F6CDD1DL);
        table.start();
        look();

        Card open = seat.trumpCard;
        trumpName = open == null ? Cards.suitName(seat.trump) : open.name();

        refresh();
        // Рука вслух не перечисляется: её и так смотрят по картам, а список
        // шести карт подряд только отодвигает партию и мешает слушать.
        announce("Раздача. Козырь — " + trumpName + ". У тебя "
                + Cards.count(seat.handCounts[HUMAN]) + ".");
        advance();
    }

    /**
     * Взять свежий вид места.
     *
     * Вид едет строкой туда и обратно — тем же проводом, что потом пойдёт по
     * Блютусу. В одиночной игре это стоит одного разбора на ход, зато весь
     * путь связи проверяется на живом телефоне задолго до сети: если вид не
     * переживёт дорогу, это выяснится здесь, а не на партии с другом.
     */
    private void look() {
        seat = Wire.decode(Wire.encode(table.seatFor(HUMAN)));
    }

    /**
     * Ход за игрока: уходит на стол, и обратно приходит свежий вид.
     *
     * Отказ стола говорится вслух. По-хорошему его быть не должно — игрок
     * спрашивает разрешения у своего вида, а стол судит теми же правилами, —
     * но если они разойдутся, молчать нельзя: молчание выглядело бы как
     * «кнопка не работает».
     */
    private void play(int player, Wire.Move move) {
        String reason = table.act(player, move);
        look();
        if (reason != null) voice.say(reason);
    }

    /** Чей ход и что в нём требуется. */
    private void advance() {
        refresh();
        if (seat.over()) {
            finishGame();
            return;
        }
        if (isFoeTurn()) {
            handler.removeCallbacks(foeStep);
            handler.postDelayed(foeStep, FOE_PAUSE_MS);
            return;
        }
        promptHuman();
    }

    private boolean isFoeTurn() {
        if (seat.phase == Game.PHASE_DEFEND) return seat.defender == FOE;
        return seat.attacker == FOE;
    }

    /**
     * Одно движение соперника.
     *
     * Вслух называется только карта — «соперник, семь пик», — и то, чем круг
     * кончился: взял он или сказал «бито». Слова про ход и про бой отсюда убраны:
     * чем он бил, слышно в самой карте, а чем всё кончилось, иначе пропустишь —
     * карты уходят со стола молча.
     */
    private void stepFoe() {
        if (seat.over()) return;

        if (seat.phase == Game.PHASE_DEFEND && seat.defender == FOE) {
            Card card = bot.defend(table.seatFor(FOE));
            if (card == null) {
                play(FOE, Wire.Move.take());
                announce("Соперник взял.");
            } else {
                play(FOE, Wire.Move.beat(card));
                announce("Соперник, " + card.name() + ".");
            }
            return;
        }

        if (seat.phase == Game.PHASE_ATTACK && seat.attacker == FOE) {
            Card card = bot.attack(table.seatFor(FOE));
            if (card == null) {
                play(FOE, Wire.Move.pass());
                // Без «соперник»: круг кончил он, и это уже слышно — карту
                // перед этим называли его голосом. Дальше идёт «твой ход», и
                // вместе выходит ровно «бито, твой ход».
                announce("Бито.");
                return;
            }
            play(FOE, Wire.Move.hit(card));
            announce("Соперник, " + card.name() + ".");
        }
    }

    /**
     * Что человеку делать прямо сейчас.
     *
     * Подсказка называет только то, чего не слышно из карт: чей ход и какую
     * карту бить. Ни «бей или бери», ни «подкидывай или говори бито» здесь нет —
     * Валера знает правила, и это для него лишнее слово.
     */
    private void promptHuman() {
        String hint;
        if (seat.phase == Game.PHASE_DEFEND && seat.defender == HUMAN) {
            Seat.Pair open = seat.unbeaten();
            hint = open == null ? "Отбивайся." : "Отбивайся: " + open.attack.name() + ".";
        } else if (seat.table.isEmpty()) {
            hint = "Твой ход.";
        } else {
            hint = "Подкидывай.";
        }
        turn(hint);
    }

    /**
     * Сказать, что ход у человека.
     *
     * Когда проговаривание выключено, вслух не говорится ничего — так играют,
     * когда рядом люди. Но тогда ход можно и пропустить, поэтому о нём сообщает
     * короткая вибрация: её никто, кроме владельца, не слышит.
     */
    private void turn(String hint) {
        if (speakAll) voice.say(hint);
        else voice.buzz(Voice.BUZZ_TICK);
    }

    // ----- ходы человека -----

    private void tapCard(Card card) {
        handler.removeCallbacks(foeStep);
        if (seat.over()) return;

        if (seat.phase == Game.PHASE_DEFEND && seat.defender == HUMAN) {
            if (!seat.defendOptions().contains(card)) {
                Seat.Pair open = seat.unbeaten();
                voice.say("Не отбиться. Бить "
                        + (open == null ? "нечего" : open.attack.name()) + ".");
                return;
            }
            play(HUMAN, Wire.Move.beat(card));
            voice.say("Бьёшь: " + card.name() + ".");
            advance();
            return;
        }

        if (seat.phase == Game.PHASE_ATTACK && seat.attacker == HUMAN) {
            if (!seat.attackOptions().contains(card)) {
                voice.say(seat.table.isEmpty()
                        ? "Этой картой сейчас нельзя."
                        : "Подкидывать можно только по достоинству того, что на столе.");
                return;
            }
            play(HUMAN, Wire.Move.hit(card));
            voice.say("Ходишь: " + card.name() + ".");
            advance();
            return;
        }

        voice.say("Сейчас ход соперника.");
    }

    private void tapTake() {
        handler.removeCallbacks(foeStep);
        if (seat.over()) return;
        if (seat.table.isEmpty()) {
            voice.say("Стол пустой — брать нечего.");
            return;
        }
        if (seat.defender != HUMAN) {
            voice.say("Берёт тот, кто отбивается, а ты сейчас ходишь.");
            return;
        }
        play(HUMAN, Wire.Move.take());
        voice.say("Берёшь. У тебя " + Cards.count(seat.handCounts[HUMAN]) + ".");
        advance();
    }

    private void tapPass() {
        handler.removeCallbacks(foeStep);
        if (seat.over()) return;
        if (seat.attacker != HUMAN) {
            voice.say("Бито говорит тот, кто ходил, а сейчас ты отбиваешься.");
            return;
        }
        if (seat.table.isEmpty()) {
            voice.say("Бито говорят, когда всё отбито. Сначала зайди картой.");
            return;
        }
        if (!seat.canPass()) {
            voice.say("Бито нельзя: на столе есть неотбитая карта.");
            return;
        }
        play(HUMAN, Wire.Move.pass());
        voice.say("Бито.");
        advance();
    }

    // ----- что говорят кнопки -----

    private void sayDeck() {
        voice.say(deckWords() + " Козырь — " + trumpName + ".");
    }

    /**
     * Колода словами: «колода, 24 карты».
     *
     * Без «в ней»: считают карты в колоде, а не что-то внутри неё, и лишнее
     * слово тут только удлиняет фразу. Одними и теми же словами говорит и
     * нажатие, и диктор при касании — иначе одна и та же колода звучала бы
     * по-разному в зависимости от того, чем до неё дотронулись.
     */
    private String deckWords() {
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
    private String tableText() {
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

    // ----- перерисовка -----

    /**
     * Перерисовать стол.
     *
     * Всё пишется только тогда, когда меняется. Диктор читает вслух то, что
     * правится прямо сейчас, — и обновление нетронутой надписи обрывало бы
     * фразу о ходе соперника ровно посередине.
     */
    private void refresh() {
        int foeSize = Prefs.FOE_SIZE_SP[Prefs.foeSize(this)];
        if (foeSize != foeSizeShown) {
            foeSizeShown = foeSize;
            foeButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, foeSize);
        }

        // Козырная карта на колоде — того же размера, что карты в руке: она
        // и есть карта, и мельче своих ей быть незачем.
        int deckSize = Prefs.MY_SIZE_SP[Prefs.mySize(this)];
        if (deckSize != deckSizeShown) {
            deckSizeShown = deckSize;
            deckButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, deckSize);
        }

        // Надписи без описаний: диктор читает ровно то, что написано. Описания
        // остаются там, где на экране написано короче, чем нужно на слух, —
        // у колоды и у карт.
        plain(foeButton, "Соперник, " + Cards.count(seat.handCounts[FOE]));
        plain(tableText, tableText());
        // На колоде лежит козырная карта — её и видно, а число карт под ней.
        plain(deckButton, Skin.deckFace(this, seat.trumpCard, seat.trump, seat.deckCount));

        describe(deckButton, deckWords() + " Козырь — " + trumpName);

        // У «Бито» описания нет намеренно, и меняться на ходу оно не должно:
        // диктор читает изменившуюся подпись той кнопки, на которой стоит, — и
        // сразу после своего хода Валера слышал «пока нельзя, на столе есть
        // неотбитая карта» вместо хода соперника. Почему нельзя, «Бито»
        // объясняет только тогда, когда её нажали впустую.
        showHand();
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
        int sp = Prefs.MY_SIZE_SP[Prefs.mySize(this)];

        // Пересобираем, только если карты и правда изменились. Иначе диктор
        // после каждого хода соперника терял бы карту, на которой стоял, и
        // возвращался бы в начало полосы — а на карту ещё нужно попасть.
        // Цвета тоже в ключе: их меняют в настройках, и вернувшись за стол,
        // карты должны быть уже перекрашены. Число карт в ключе есть — оно
        // в списке, — значит и ширина пересчитается сама.
        String key = sp + "|" + Prefs.cardColor(this) + "|" + Prefs.suitColor(this)
                + "|" + Prefs.rankColor(this) + "|" + Cards.list(cards);
        if (key.equals(handKey)) return;
        handKey = key;

        handBox.removeAllViews();

        if (cards.isEmpty()) {
            handBox.addView(Skin.text(this, "Карт нет", sp), wrap());
            return;
        }

        // Сколько остаётся на карту, если разложить всю руку по ширине
        // экрана: поля корня по бокам и зазоры между картами — врозь.
        int natural = Skin.dp(this, Math.max(Skin.TOUCH_DP, Math.round(sp * 1.6f)));
        int screen = getResources().getDisplayMetrics().widthPixels;
        int room = screen - Skin.dp(this, 16) - Skin.dp(this, 4) * cards.size();
        int width = Math.min(natural, Math.max(Skin.dp(this, MIN_CARD_DP), room / cards.size()));
        int sizeSp = Math.min(sp, fitSp(width));

        for (Card card : cards) {
            Button button = Skin.card(this, card, sizeSp, seat.trump);
            button.setOnClickListener(view -> tapCard(card));
            // Во всю высоту полосы: полоса растянута до самого низа экрана, и
            // карта должна заполнять её целиком, а не висеть в ней островком.
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    width, LinearLayout.LayoutParams.MATCH_PARENT);
            params.setMargins(Skin.dp(this, 2), Skin.dp(this, 2),
                    Skin.dp(this, 2), Skin.dp(this, 2));
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
        float density = getResources().getDisplayMetrics().density;
        int sp = Math.round((widthPx / density - 8f) / 1.15f);
        return Math.max(MIN_CARD_SP, sp);
    }

    /** Размер по содержимому: для полосы, которая сама шире экрана. */
    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    // ----- конец и начало -----

    private void finishGame() {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(foeStep);

        String result;
        if (seat.draw) {
            result = "Ничья: вы вышли одновременно.";
        } else if (seat.loser == HUMAN) {
            result = "Ты дурак. Партия кончена.";
        } else {
            result = "Соперник дурак. Ты выиграл.";
        }
        voice.say(result);

        // Партия кончена — стол больше ни на что не отзывается. Поэтому выход
        // отсюда обязателен: оставить владельца за мёртвым столом нельзя.
        Ask.confirm(this, result, "Ещё раз", "Выйти", this::deal, this::finish);
    }

    private void askRestart() {
        Ask.confirm(this, "Начать заново?", "Заново", "Продолжить", this::deal);
    }

    private void askSurrender() {
        Ask.confirm(this, "Сдаться?", "Сдаюсь", "Играю", () -> {
            handler.removeCallbacks(foeStep);
            finished = true;
            voice.say("Ты сдался. Партия кончена.");
            finish();
        });
    }

    /** Сказать, если включено проговаривание; иначе промолчать. */
    private void announce(String text) {
        if (speakAll) voice.say(text);
    }
}

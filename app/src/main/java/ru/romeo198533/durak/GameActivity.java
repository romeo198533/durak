package ru.romeo198533.durak;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Игровой стол.
 *
 * Раскладка повторяет настоящий стол, каким его нащупывают рукой: в самом низу —
 * свои карты, в самом верху — соперник, посередине колода с козырем, а по бокам
 * от неё «Начать заново» и «Сдаться». Над колодой две крупные кнопки — «Беру» и
 * «Бито»: обе на виду весь круг, потому что искать их в решительный момент
 * некогда, а «Бито» до поры объясняет, чего ещё не хватает.
 *
 * Карты списком в две колонки, а не одной полосой внизу: за круг рука доходит до
 * двенадцати карт, и в полосу они не влезут. Две колонки читаются диктором в том
 * же порядке, в каком лежат, и вдвое короче.
 */
public class GameActivity extends Activity {

    /** Человек всегда первый, программа — вторая. */
    private static final int HUMAN = 0;
    private static final int FOE = 1;

    /** Пауза перед ходом соперника: чтобы его фраза не наезжала на свою. */
    private static final long FOE_PAUSE_MS = 1200L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Game game;
    private Bot bot;
    private Voice voice;

    /** Проговаривать ли ход соперника сам по себе или только по касанию. */
    private boolean speakAll;

    /** Козырь словами: колода кончится, а козырь останется. */
    private String trumpName = "";

    private Button foeButton;
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

    private void build() {
        LinearLayout root = Skin.column(this);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8));

        foeButton = Skin.button(this, "", 18);
        foeButton.setOnClickListener(view -> sayFoe());
        root.addView(foeButton, spaced());

        tableText = Skin.text(this, "", 17);
        tableText.setClickable(true);
        tableText.setOnClickListener(view -> voice.say("На столе: " + tableText()));
        root.addView(tableText, Skin.wide());

        LinearLayout decisions = Skin.row(this);
        takeButton = Skin.button(this, "Беру", 26);
        takeButton.setOnClickListener(view -> tapTake());
        decisions.addView(takeButton, tall());
        passButton = Skin.button(this, "Бито", 26);
        passButton.setOnClickListener(view -> tapPass());
        decisions.addView(passButton, tall());
        root.addView(decisions, Skin.wide());

        LinearLayout middle = Skin.row(this);

        restartButton = Skin.button(this, "Начать\nзаново", 16);
        restartButton.setOnClickListener(view -> askRestart());
        middle.addView(restartButton, Skin.weight(1f));

        deckButton = Skin.button(this, "", 16);
        deckButton.setOnClickListener(view -> sayDeck());
        LinearLayout.LayoutParams deckParams = Skin.weight(1.4f);
        deckParams.setMargins(Skin.dp(this, 4), 0, Skin.dp(this, 4), 0);
        middle.addView(deckButton, deckParams);

        surrenderButton = Skin.button(this, "Сдаться", 16);
        surrenderButton.setOnClickListener(view -> askSurrender());
        middle.addView(surrenderButton, Skin.weight(1f));

        root.addView(middle, spaced());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        handBox = Skin.row(this);
        handBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(handBox, Skin.wide());
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams out = Skin.wide();
        out.setMargins(0, Skin.dp(this, 4), 0, Skin.dp(this, 4));
        return out;
    }

    private LinearLayout.LayoutParams tall() {
        LinearLayout.LayoutParams out = Skin.weight(1f);
        out.height = Skin.dp(this, Skin.BIG_DP);
        out.setMargins(Skin.dp(this, 4), 0, Skin.dp(this, 4), 0);
        return out;
    }

    // ----- партия -----

    private void deal() {
        handler.removeCallbacks(foeStep);
        finished = false;
        handKey = "";
        game = new Game(Prefs.deck(this), System.nanoTime());
        bot = new Bot(FOE, System.nanoTime() ^ 0x2545F4914F6CDD1DL);
        game.start();

        Card open = game.trumpCard();
        trumpName = open == null ? Cards.suitName(game.trump()) : open.name();

        refresh();
        announce("Раздача. Козырь — " + trumpName + ". У тебя "
                + Cards.count(game.handCount(HUMAN)) + ": " + Cards.list(game.handSorted(HUMAN)) + ".");
        advance();
    }

    /** Чей ход и что в нём требуется. */
    private void advance() {
        refresh();
        if (game.isOver()) {
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
        if (game.phase() == Game.PHASE_DEFEND) return game.defender() == FOE;
        return game.attacker() == FOE;
    }

    /** Одно движение соперника. */
    private void stepFoe() {
        if (game.isOver()) return;

        if (game.phase() == Game.PHASE_DEFEND && game.defender() == FOE) {
            Card card = bot.defend(game);
            if (card == null) {
                game.take();
                announce("Соперник берёт.");
            } else {
                game.defend(card);
                announce("Соперник бьёт: " + card.name() + ".");
            }
            return;
        }

        if (game.phase() == Game.PHASE_ATTACK && game.attacker() == FOE) {
            Card card = bot.attack(game);
            if (card == null && game.canPass()) {
                game.pass();
                announce("Соперник говорит: бито.");
                return;
            }
            if (card != null) {
                game.attack(card);
                announce("Соперник ходит: " + card.name() + ".");
            }
        }
    }

    /** Что человеку делать прямо сейчас. */
    private void promptHuman() {
        String hint;
        if (game.phase() == Game.PHASE_DEFEND && game.defender() == HUMAN) {
            Game.Slot open = game.unbeaten();
            hint = open == null
                    ? "Отбивайся."
                    : "Отбивайся: бить " + open.attack.name() + ". Или бери.";
        } else if (game.table().isEmpty()) {
            hint = "Твой ход. Зайди любой картой.";
        } else {
            hint = "Подкидывай или говори бито.";
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
        if (game.isOver()) return;

        if (game.phase() == Game.PHASE_DEFEND && game.defender() == HUMAN) {
            if (!game.defendOptions().contains(card)) {
                Game.Slot open = game.unbeaten();
                voice.say("Не отбиться. Бить "
                        + (open == null ? "нечего" : open.attack.name()) + ".");
                return;
            }
            game.defend(card);
            voice.say("Бьёшь: " + card.name() + ".");
            advance();
            return;
        }

        if (game.phase() == Game.PHASE_ATTACK && game.attacker() == HUMAN) {
            if (!game.attackOptions().contains(card)) {
                voice.say(game.table().isEmpty()
                        ? "Этой картой сейчас нельзя."
                        : "Подкидывать можно только по достоинству того, что на столе.");
                return;
            }
            game.attack(card);
            voice.say("Ходишь: " + card.name() + ".");
            advance();
            return;
        }

        voice.say("Сейчас ход соперника.");
    }

    private void tapTake() {
        handler.removeCallbacks(foeStep);
        if (game.isOver()) return;
        if (!game.canTake()) {
            voice.say("Стол пустой — брать нечего.");
            return;
        }
        if (game.defender() != HUMAN) {
            voice.say("Берёт тот, кто отбивается, а ты сейчас ходишь.");
            return;
        }
        game.take();
        voice.say("Берёшь. У тебя " + Cards.count(game.handCount(HUMAN)) + ".");
        advance();
    }

    private void tapPass() {
        handler.removeCallbacks(foeStep);
        if (game.isOver()) return;
        if (game.attacker() != HUMAN) {
            voice.say("Бито говорит тот, кто ходил, а сейчас ты отбиваешься.");
            return;
        }
        if (game.table().isEmpty()) {
            voice.say("Бито говорят, когда всё отбито. Сначала зайди картой.");
            return;
        }
        if (!game.canPass()) {
            voice.say("Бито нельзя: на столе есть неотбитая карта.");
            return;
        }
        game.pass();
        voice.say("Бито.");
        advance();
    }

    // ----- что говорят кнопки -----

    private void sayFoe() {
        voice.say("У соперника " + Cards.count(game.handCount(FOE)) + ".");
    }

    private void sayDeck() {
        String cards = game.deckCount() == 0
                ? "Колода пуста."
                : "В колоде " + Cards.count(game.deckCount()) + ".";
        voice.say(cards + " Козырь — " + trumpName + ".");
    }

    /** Стол словами: чем зашли и отбито ли. */
    private String tableText() {
        List<Game.Slot> slots = game.table();
        if (slots.isEmpty()) return "Пусто";
        StringBuilder out = new StringBuilder();
        for (Game.Slot slot : slots) {
            if (out.length() > 0) out.append("; ");
            out.append(slot.attack.name());
            out.append(slot.beaten() ? " — бита" : " — не бита");
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
        String foe = Cards.count(game.handCount(FOE));
        label(foeButton, "Соперник: " + foe,
                "Соперник, у него " + foe + ". Нажми, чтобы услышать ещё раз");
        int foeSize = Prefs.FOE_SIZE_SP[Prefs.foeSize(this)];
        if (foeSize != foeSizeShown) {
            foeSizeShown = foeSize;
            foeButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, foeSize);
        }

        label(tableText, "На столе: " + tableText(),
                "На столе: " + tableText() + ". Нажми, чтобы услышать ещё раз");

        label(deckButton,
                game.deckCount() == 0 ? "Колода\nпуста" : "Колода\n" + Cards.count(game.deckCount()),
                "Колода. В ней " + Cards.count(game.deckCount())
                        + ", козырь — " + trumpName + ". Нажми, чтобы услышать");

        // Обе кнопки всегда на виду и всегда нажимаются: «Бито» вместо запрета
        // объясняет словами, чего ещё не хватает, — выключенная кнопка диктору
        // не видна вовсе, и слепой просто не понял бы, куда она делась.
        describe(passButton, game.canPass()
                ? "Бито. Нажми, чтобы закончить круг"
                : "Бито. Пока нельзя: на столе есть неотбитая карта");

        showHand();
    }

    private void label(TextView view, CharSequence text, CharSequence spoken) {
        if (!text.equals(view.getText())) view.setText(text);
        describe(view, spoken);
    }

    private void describe(TextView view, CharSequence spoken) {
        if (!spoken.equals(view.getContentDescription())) {
            view.setContentDescription(spoken);
        }
    }

    /**
     * Свои карты внизу, в две колонки по убыванию читаемости.
     *
     * Порядок тот же, что у диктора: слева направо, сверху вниз. Козыри — в
     * конце списка, их ищут отдельно и находят сразу.
     */
    private void showHand() {
        List<Card> cards = game.handSorted(HUMAN);
        int sp = Prefs.MY_SIZE_SP[Prefs.mySize(this)];

        // Пересобираем, только если карты и правда изменились. Иначе диктор
        // после каждого хода соперника терял бы карту, на которой стоял, и
        // возвращался бы в начало экрана — а на карту ещё нужно попасть.
        // Цвета тоже в ключе: их меняют в настройках, и вернувшись за стол,
        // карты должны быть уже перекрашены.
        String key = sp + "|" + Prefs.cardColor(this) + "|" + Prefs.suitColor(this)
                + "|" + Prefs.rankColor(this) + "|" + Cards.list(cards);
        if (key.equals(handKey)) return;
        handKey = key;

        handBox.removeAllViews();

        if (cards.isEmpty()) {
            handBox.addView(Skin.text(this, "Карт нет", sp), Skin.wide());
            return;
        }

        for (int i = 0; i < cards.size(); i += 2) {
            LinearLayout row = Skin.row(this);
            for (int j = i; j < i + 2 && j < cards.size(); j++) {
                final Card card = cards.get(j);
                Button button = Skin.card(this, card, sp, game.trump());
                button.setOnClickListener(view -> tapCard(card));
                LinearLayout.LayoutParams params = Skin.weight(1f);
                params.setMargins(Skin.dp(this, 2), Skin.dp(this, 2),
                        Skin.dp(this, 2), Skin.dp(this, 2));
                row.addView(button, params);
            }
            // Одной карте во всю ширину делать нечего: пусть стоит на своём
            // месте, иначе рука ищет её там, где её нет.
            if (cards.size() - i == 1) {
                View gap = new View(this);
                row.addView(gap, Skin.weight(1f));
            }
            handBox.addView(row, Skin.wide());
        }
    }

    // ----- конец и начало -----

    private void finishGame() {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(foeStep);

        String result;
        if (game.isDraw()) {
            result = "Ничья: вы вышли одновременно.";
        } else if (game.loser() == HUMAN) {
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

package ru.romeo198533.durak;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Игровой стол с программой: партия идёт в этом же телефоне.
 *
 * Экран рисует не партию, а {@link Seat} — вид места, — и ходы шлёт на стол
 * через {@link Wire}. Взятый вид едет строкой туда и обратно, тем же проводом,
 * что потом пойдёт по Блютусу: в одиночной игре это стоит одного разбора на
 * ход, зато весь путь связи проверяется на живом телефоне задолго до сети.
 *
 * Сам стол за этим экраном чужой: он в {@link Table}, и ведёт его программа.
 * Тот же вид и те же ходы приходят по сети в {@link NetGameActivity} — и
 * показываются тем же {@link TableView}. Двух столов, которые могут разойтись
 * на вид, не бывает.
 */
public class GameActivity extends Activity implements TableView.Listener {

    /** Человек всегда первый, программа — вторая. */
    private static final int HUMAN = 0;
    private static final int FOE = 1;

    /** Пауза перед ходом соперника: чтобы его фраза не наезжала на свою. */
    private static final long FOE_PAUSE_MS = 1200L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Table table;
    private Bot bot;
    private Voice voice;
    private TableView view;

    /** Моё место за столом. Всё, что видно на экране, берётся отсюда. */
    private Seat seat;

    /** Проговаривать ли ход соперника сам по себе или только по касанию. */
    private boolean speakAll;

    /** Козырь словами: колода кончится, а козырь останется. */
    private String trumpName = "";

    private boolean finished;

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
        view = new TableView(this, this);
        setContentView(view.root());
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

    // ----- партия -----

    private void deal() {
        handler.removeCallbacks(foeStep);
        finished = false;
        table = new Table(Prefs.deck(this), System.nanoTime());
        bot = new Bot(FOE, System.nanoTime() ^ 0x2545F4914F6CDD1DL);
        table.start();
        look();

        Card open = seat.trumpCard;
        trumpName = open == null ? Cards.suitName(seat.trump) : open.name();
        view.trump(trumpName);
        view.forgetHand();

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

        // Чужой ход называется здесь, а не там, где он сделан: экран рисует вид,
        // и о ходе он узнаёт из вида же — так же, как узнает его по Блютусу.
        announce(Words.moved(seat, HUMAN, Words.FOE));

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
     * Вслух отсюда не говорится ничего: сказанное называет {@link #advance} по
     * виду места. Так же узнаёт о чужом ходе и экран по Блютусу — и оба экрана
     * говорят одно и то же одними словами.
     */
    private void stepFoe() {
        if (seat.over()) return;

        if (seat.phase == Game.PHASE_DEFEND && seat.defender == FOE) {
            Card card = bot.defend(table.seatFor(FOE));
            play(FOE, card == null ? Wire.Move.take() : Wire.Move.beat(card));
            return;
        }

        if (seat.phase == Game.PHASE_ATTACK && seat.attacker == FOE) {
            Card card = bot.attack(table.seatFor(FOE));
            play(FOE, card == null ? Wire.Move.pass() : Wire.Move.hit(card));
        }
    }

    /** Что человеку делать прямо сейчас. */
    private void promptHuman() {
        String hint = Words.turn(seat, HUMAN);
        if (hint != null) turn(hint);
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

    @Override
    public void onCard(Card card) {
        handler.removeCallbacks(foeStep);
        if (seat.over()) return;

        if (seat.phase == Game.PHASE_DEFEND && seat.defender == HUMAN) {
            if (!seat.defendOptions().contains(card)) {
                voice.say(Words.refusal(seat, HUMAN));
                return;
            }
            play(HUMAN, Wire.Move.beat(card));
            voice.say("Бьёшь: " + card.name() + ".");
            advance();
            return;
        }

        if (seat.phase == Game.PHASE_ATTACK && seat.attacker == HUMAN) {
            if (!seat.attackOptions().contains(card)) {
                voice.say(Words.refusal(seat, HUMAN));
                return;
            }
            play(HUMAN, Wire.Move.hit(card));
            voice.say("Ходишь: " + card.name() + ".");
            advance();
            return;
        }

        voice.say("Сейчас ход соперника.");
    }

    @Override
    public void onTake() {
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

    @Override
    public void onPass() {
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

    @Override
    public void onDeck() {
        voice.say(view.deckWords() + " Козырь — " + trumpName + ".");
    }

    @Override
    public void onRestart() {
        askRestart();
    }

    @Override
    public void onSurrender() {
        askSurrender();
    }

    // ----- перерисовка -----

    private void refresh() {
        view.show(seat);
    }

    // ----- конец и начало -----

    private void finishGame() {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(foeStep);

        String result = Words.result(seat, HUMAN);
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

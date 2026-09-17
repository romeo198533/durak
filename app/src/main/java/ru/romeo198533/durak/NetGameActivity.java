package ru.romeo198533.durak;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Стол по проводу: то же, что и одиночная партия, но партия живёт не здесь.
 *
 * Экран рисует вид места и шлёт ходы — тем же {@link TableView}, что и игра с
 * программой. Разница одна: вид приезжает по каналу, а не достаётся у себя же.
 * Поэтому двух столов, которые могут разойтись на вид, не бывает: рисовать
 * по-разному им попросту нечем.
 *
 * Читает и пишет один поток, и строго по очереди: сначала вид, потом ход, потом
 * ответ на ход. Двух читающих у канала быть не должно — иначе ответ на ход
 * достался бы тому, кто ждал вида, и партия замерла бы на ровном месте.
 */
public class NetGameActivity extends Activity implements TableView.Listener {

    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Мои ходы, ждущие отправки.
     *
     * Через очередь, а не сразу из нажатия: отправка ждёт ответа главы, а ждать
     * в потоке, который рисует экран, нельзя — экран замёрз бы вместе с ней.
     */
    private final BlockingQueue<Wire.Move> moves = new LinkedBlockingQueue<>();

    private NetGame game;
    private Voice voice;
    private TableView view;
    private Seat seat;
    private boolean speakAll;
    private String trumpName = "";

    /** Ход отправлен и ждёт ответа: второй в это время слать нельзя. */
    private volatile boolean pending;

    /** Поток, который читает стол: его же и прерывают, уходя с экрана. */
    private Thread reader;

    private boolean finished;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        game = NetGame.take();
        if (game == null) {
            // Связь не дожила до этого экрана — играть нечем, и открывать
            // пустой стол нельзя.
            finish();
            return;
        }

        voice = new Voice(this, true);
        speakAll = Prefs.speakAll(this);
        view = new TableView(this, this);
        // Раздать заново здесь некому: колода у того, кто держит стол, и гость
        // её не получит. Кнопки, которая ничего не может, на экране быть не
        // должно.
        view.noRestart();
        setContentView(view.root());

        reader = new Thread(this::listen, "чтение стола");
        reader.start();
    }

    @Override
    protected void onDestroy() {
        // Стол закрывается вместе с экраном: держать канал открытым после
        // ухода незачем, а второй телефон иначе ждал бы хода до бесконечности.
        if (game != null) game.stop();
        // Поток чтения в это время ждёт хода от экрана, а не вида от стола:
        // закрытый канал его не разбудит, и без этого он остался бы висеть.
        if (reader != null) reader.interrupt();
        handler.removeCallbacksAndMessages(null);
        if (voice != null) {
            voice.shutdown();
            voice = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Ask.confirm(this, "Выйти из партии?", "Выйти", "Остаться", () -> {
            voice.say("Выходишь из партии.");
            finish();
        });
    }

    // ----- чтение и отправка -----

    /**
     * Всё общение со столом: вид — ход — ответ — снова вид.
     *
     * Звать из своего потока. Здесь ждут и вида, и ответа, и хода от экрана.
     */
    private void listen() {
        while (true) {
            Seat fresh;
            try {
                fresh = game.receive();
            } catch (IOException broken) {
                break;
            }
            if (fresh == null) break;

            final Seat shown = fresh;
            handler.post(() -> onView(shown));

            // Ход мой — ждём, пока экран его выберет. Не мой — читаем дальше:
            // за меня сходит тот, кто ведёт стол.
            if (shown.over() || shown.turn() != shown.me) continue;

            Wire.Move move;
            try {
                move = moves.take();
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }

            String reason;
            try {
                reason = game.send(move);
            } catch (IOException broken) {
                break;
            }
            if (reason != null) {
                final String why = reason;
                // Отказ стола вслух: по-хорошему его быть не должно — игрок
                // спрашивает разрешения у своего вида, — но если они разойдутся,
                // молчание выглядело бы как «кнопка не работает».
                handler.post(() -> voice.say(why));
            }
        }

        handler.post(this::lost);
    }

    /** Что сказать, когда стол закрылся сам, а экран этого не просил. */
    private void lost() {
        if (isFinishing() || isDestroyed() || finished) return;
        finished = true;

        String why = game.trouble();
        voice.say(why == null ? "Связь со столом пропала." : why);
        Ask.confirm(this, why == null ? "Связь со столом пропала." : why,
                "Выйти", "Остаться", this::finish);
    }

    // ----- вид приехал -----

    private void onView(Seat fresh) {
        pending = false;
        seat = fresh;

        if (trumpName.isEmpty()) {
            Card open = seat.trumpCard;
            trumpName = open == null ? Cards.suitName(seat.trump) : open.name();
            view.trump(trumpName);
        }

        view.show(seat);

        // Чужой ход называется здесь, а не там, где он сделан: за другого
        // игрока карту выкладывает не этот экран, и узнать о ходе ему больше
        // неоткуда.
        announce(Words.moved(seat, seat.me, foe()));

        if (seat.over()) {
            finishGame();
            return;
        }
        if (seat.turn() == seat.me) {
            String hint = Words.turn(seat, seat.me);
            if (hint != null) turn(hint);
        }
    }

    /** Как звать того, кто напротив: за своим столом он один. */
    private String foe() {
        if (game.mine()) return Words.FOE;
        String peer = game.peer();
        return peer == null || peer.isEmpty() ? Words.FOE : peer;
    }

    // ----- ходы человека -----

    @Override
    public void onCard(Card card) {
        if (seat == null || seat.over() || pending) return;

        if (seat.phase == Game.PHASE_DEFEND && seat.defender == seat.me) {
            if (!seat.defendOptions().contains(card)) {
                voice.say(Words.refusal(seat, seat.me));
                return;
            }
            play(Wire.Move.beat(card), "Бьёшь: " + card.name() + ".");
            return;
        }

        if (seat.phase == Game.PHASE_ATTACK && seat.attacker == seat.me) {
            if (!seat.attackOptions().contains(card)) {
                voice.say(Words.refusal(seat, seat.me));
                return;
            }
            play(Wire.Move.hit(card), "Ходишь: " + card.name() + ".");
            return;
        }

        voice.say("Сейчас ход соперника.");
    }

    @Override
    public void onTake() {
        if (seat == null || seat.over() || pending) return;
        if (seat.table.isEmpty()) {
            voice.say("Стол пустой — брать нечего.");
            return;
        }
        if (seat.defender != seat.me) {
            voice.say("Берёт тот, кто отбивается, а ты сейчас ходишь.");
            return;
        }
        play(Wire.Move.take(), "Берёшь.");
    }

    @Override
    public void onPass() {
        if (seat == null || seat.over() || pending) return;
        if (seat.attacker != seat.me) {
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
        play(Wire.Move.pass(), "Бито.");
    }

    @Override
    public void onDeck() {
        voice.say(view.deckWords() + " Козырь — " + trumpName + ".");
    }

    @Override
    public void onRestart() {
        // В партии по проводу заново не раздают: колода у того, кто держит стол.
        voice.say("Заново раздать можно только в игре с программой.");
    }

    @Override
    public void onSurrender() {
        Ask.confirm(this, "Выйти из партии?", "Выйти", "Остаться", () -> {
            voice.say("Выходишь из партии.");
            finish();
        });
    }

    // ----- отправка -----

    /**
     * Отдать ход столу.
     *
     * Ответ придёт не сюда, а в общий поток чтения: у канала должен быть ровно
     * один читающий. Здесь только очередь и слово о своём ходе — чтобы игрок
     * услышал его сразу, не дожидаясь, пока сходивший ход дойдёт до стола.
     */
    private void play(Wire.Move move, String said) {
        pending = true;
        voice.say(said);
        moves.add(move);
    }

    // ----- конец и начало -----

    private void finishGame() {
        if (finished) return;
        finished = true;

        String result = Words.result(seat, seat.me);
        voice.say(result);
        Ask.confirm(this, result, "Выйти", "Посмотреть стол", this::finish);
    }

    /** Сказать, если включено проговаривание; иначе промолчать. */
    private void announce(String text) {
        if (text != null && speakAll) voice.say(text);
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
}

package ru.romeo198533.durak;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Стол по проводу: то же, что и одиночная партия, но партия живёт не здесь.
 *
 * Экран рисует вид места и шлёт ходы — тем же {@link TableView}, что и игра с
 * программой. Разница одна: вид приезжает по каналу, а не достаётся у себя же.
 * Поэтому двух столов, которые могут разойтись на вид, не бывает: рисовать
 * по-разному им попросту нечем.
 *
 * Экран не владеет партией, а только смотрит на неё. Уйдя с экрана — свернув
 * игру, погасив телефон, уехав в другое окно, — партию не рвут: за столом ждут
 * живого соперника, и вернувшийся экран застаёт её там, где оставил. Поэтому
 * здесь нет ни чтения канала, ни очереди ходов: и то и другое у партии
 * ({@link NetGame}), а экран только подключается к ней и отключается.
 *
 * Пока партия идёт, поднята служба переднего плана ({@link GameService}): без
 * неё Android прибрал бы приложение в фоне вместе со связью.
 */
public class NetGameActivity extends Activity implements TableView.Listener, NetGame.Screen {

    /** Звать голосом и рисовать можно только отсюда: экран живёт в этом потоке. */
    private final Handler handler = new Handler(Looper.getMainLooper());

    private NetGame game;
    private Voice voice;
    private TableView view;
    private Seat seat;
    private boolean speakAll;
    private String trumpName = "";

    /** Номер круга в прошлом виде: по нему видно, что раздали заново. */
    private int lastRound;

    /** Партия кончена или связь пропала — второй раз об этом не говорят. */
    private boolean finished;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        game = NetGame.live();
        if (game == null) {
            // Партии нет — играть нечем. Открывать пустой стол незачем; если
            // сюда пришли из шторки, вернём человека в игру, а не в пустоту.
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        voice = new Voice(this, true);
        speakAll = Prefs.speakAll(this);
        view = new TableView(this, this);

        // Раздать заново может только хозяин стола: колода лежит у него, и
        // гость её не получит. Кнопки, которая ничего не может, на экране быть
        // не должно.
        if (!game.mine()) view.noRestart();

        setContentView(view.root());

        // Служба поднимается вместе со столом и снимается вместе с партией:
        // пока играют, приложение в фоне не прибирают.
        GameService.hold(this);

        // Партия сейчас же отдаст последний вид, если он у неё есть: человек
        // вернулся за стол и не должен ждать чужого хода, чтобы вспомнить, что
        // тут было.
        game.attach(this);
    }

    @Override
    protected void onDestroy() {
        if (game != null) game.detach(this);

        // Партию закрывают только тогда, когда экран уходит совсем. Поворот
        // телефона или уход в другое окно её не трогают: затем она и живёт
        // отдельно от экрана.
        if (game != null && isFinishing()) {
            game.stop();
            NetGame.forget(game);
            GameService.release(this);
        }

        handler.removeCallbacksAndMessages(null);
        if (voice != null) {
            voice.shutdown();
            voice = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        leave();
    }

    /**
     * Уйти из-за стола.
     *
     * Спрашиваем затем, что уход рвёт партию живому человеку на том конце: он
     * останется ждать хода, которого не будет. Сдача — другое дело: о ней
     * скажут всем, и она в отдельной кнопке.
     */
    private void leave() {
        Ask.confirm(this, "Выйти из партии?", "Выйти", "Остаться", () -> {
            if (voice != null) voice.say("Выходишь из партии.");
            finish();
        });
    }

    // ----- партия говорит -----

    /** Свежий вид приехал. Звать может кто угодно — рисуем в своём потоке. */
    @Override
    public void view(Seat fresh) {
        handler.post(() -> onView(fresh));
    }

    /** Слова от стола: отказ или обрыв. */
    @Override
    public void word(String text) {
        handler.post(() -> {
            if (voice != null) voice.say(text);
        });
    }

    /** Партия кончилась — связь закрылась или доиграна. */
    @Override
    public void ended() {
        handler.post(this::lost);
    }

    /** Что сказать, когда стол закрылся сам, а экран этого не просил. */
    private void lost() {
        if (isFinishing() || isDestroyed() || finished) return;
        finished = true;

        // Стол закрылся — значит, партии больше нет, и держать её незачем.
        // Иначе брошенная связь всплыла бы на следующей партии, а служба с
        // уведомлением «Идёт партия» висела бы над мёртвым столом и звала бы
        // обратно туда, где уже нечего делать. Снимаем здесь же, до разговора:
        // человек ещё выбирает, а прибирать пора.
        NetGame.forget(game);
        game.stop();
        GameService.release(this);

        String why = game.trouble();
        String said = why == null ? "Связь со столом пропала." : why;
        if (voice != null) voice.say(said);
        Ask.confirm(this, said, "Выйти", "Посмотреть стол", this::finish);
    }

    // ----- вид приехал -----

    private void onView(Seat fresh) {
        seat = fresh;

        // Козырь показывается один раз — но не навсегда: после раздачи заново
        // козырь другой, и старое слово было бы враньём.
        if (trumpName.isEmpty() || seat.round < lastRound) {
            Card open = seat.trumpCard;
            trumpName = open == null ? Cards.suitName(seat.trump) : open.name();
            view.trump(trumpName);
        }

        boolean redealt = seat.round < lastRound;
        lastRound = seat.round;

        view.show(seat);

        if (redealt) announce("Раздали заново. Козырь — " + trumpName + ".");

        // Чужой ход называется здесь, а не там, где он сделан: за другого
        // игрока карту выкладывает не этот экран, и узнать о ходе ему больше
        // неоткуда. Имя едет в виде: «Валера, семь пик».
        announce(Words.moved(seat, seat.me));

        if (seat.over()) {
            finishGame();
            return;
        }
        if (seat.turn() == seat.me) {
            String hint = Words.turn(seat, seat.me);
            if (hint != null) turn(hint);
        }
    }

    // ----- ходы человека -----

    @Override
    public void onCard(Card card) {
        if (seat == null || seat.over() || closed()) return;

        if (seat.myDefend()) {
            if (!seat.defendOptions().contains(card)) {
                voice.say(Words.refusal(seat, seat.me));
                return;
            }
            play(Wire.Move.beat(card), "Бьёшь: " + card.name() + ".");
            return;
        }

        if (seat.myAttack()) {
            if (!seat.attackOptions().contains(card)) {
                voice.say(Words.refusal(seat, seat.me));
                return;
            }
            play(Wire.Move.hit(card), "Ходишь: " + card.name() + ".");
            return;
        }

        voice.say("Сейчас не твой ход.");
    }

    @Override
    public void onTake() {
        if (seat == null || seat.over() || closed()) return;

        if (seat.table.isEmpty()) {
            voice.say("Стол пустой — брать нечего.");
            return;
        }
        if (!seat.canTake()) {
            // Право на взятие спрашивается у вида, а не у роли: отбился — и
            // партия снова в нападении, где брать нечего, хотя защищающийся
            // по-прежнему я.
            voice.say(seat.myDefend()
                    ? "Сейчас брать нельзя."
                    : "Берёт тот, кто отбивается, а сейчас не твой черёд.");
            return;
        }
        play(Wire.Move.take(), "Берёшь.");
    }

    @Override
    public void onPass() {
        if (seat == null || seat.over() || closed()) return;

        if (!seat.myAttack()) {
            voice.say(seat.myDefend()
                    ? "Бито говорит тот, чей ход, а сейчас ты отбиваешься."
                    : "Сейчас не твой ход.");
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
        // До первого вида колода ещё не названа, и «Козырь — .» было бы
        // обрывком вместо ответа.
        if (seat == null) {
            voice.say("Стол ещё не отвечает.");
            return;
        }
        voice.say(view.deckWords() + " Козырь — " + trumpName + ".");
    }

    @Override
    public void onRestart() {
        if (!game.mine()) {
            voice.say("Раздать заново может только хозяин стола.");
            return;
        }
        if (seat == null || seat.over() || closed()) return;

        Ask.confirm(this, "Раздать заново?", "Заново", "Продолжить", () -> {
            play(Wire.Move.restart(), "Раздаю заново.");
        });
    }

    @Override
    public void onSurrender() {
        if (seat == null || seat.over() || closed()) return;

        Ask.confirm(this, "Сдаться?", "Сдаюсь", "Играю", () -> {
            play(Wire.Move.surrender(), "Сдаёшься. Об этом скажут всем.");
        });
    }

    // ----- отправка -----

    /**
     * Отдать ход столу.
     *
     * Через партию, а не прямо в канал: отправка ждёт ответа главы, а ждать в
     * потоке, который рисует экран, нельзя — экран замёрз бы вместе с ней.
     * Слово о своём ходе говорится сразу, чтобы игрок услышал его, не дожидаясь
     * ответа стола.
     */
    private void play(Wire.Move move, String said) {
        if (!game.move(move)) {
            voice.say("Погоди: прошлый ход ещё в пути.");
            return;
        }
        voice.say(said);
    }

    /** Ход отправлен и ждёт ответа: второй в это время слать нельзя. */
    private boolean busy() {
        return game == null || game.busy();
    }

    /**
     * Стол закрылся или прошлый ход ещё в пути — объяснить нажатие словами.
     *
     * Молчать здесь нельзя: после обрыва связи нажатия уходили бы в пустоту, а
     * слепой игрок не отличает «кнопка не работает» от «партия кончена». Когда
     * партию доиграли, о конце говорит итоговый вопрос, но его можно закрыть
     * кнопкой «Посмотреть стол» — тогда сказать нужно ещё раз.
     */
    private boolean closed() {
        if (finished) {
            voice.say("Стол закрыт — играть больше нечем.");
            return true;
        }
        if (busy()) {
            voice.say("Погоди: прошлый ход ещё в пути.");
            return true;
        }
        return false;
    }

    // ----- конец -----

    private void finishGame() {
        if (finished) return;
        finished = true;

        // Партия доиграна — столу больше нечего сказать, и держать каналы
        // открытыми незачем. Служба снимается здесь же: она держала партию,
        // а партии больше нет.
        NetGame.forget(game);
        GameService.release(this);

        String result = Words.result(seat, seat.me);
        voice.say(result);

        // Партия кончена — стол больше ни на что не отзывается. Поэтому выход
        // отсюда обязателен: оставить владельца за мёртвым столом нельзя.
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

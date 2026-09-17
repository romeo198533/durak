package ru.romeo198533.durak;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Партия вдвоём по проводу.
 *
 * Партия живёт здесь, а не на экране. Экран может уйти — свернуть игру,
 * заблокировать телефон, уехать в другое окно, — и связь от этого рваться не
 * должна: за столом ждут живого человека, а не открытого окна. Поэтому и
 * читает канал, и шлёт ходы свой поток, заведённый вместе с партией; экран
 * только рисует то, что ему принесли, и кладёт в очередь свои ходы.
 *
 * Экран подключается к партии ({@link #attach}) и отключается ({@link #detach}),
 * а партия переживает и то и другое. Вернувшийся экран сейчас же получает
 * последний вид — тот самый, на котором он остановился.
 *
 * Глава стола — тот, кто открыл стол. Своё место у него устроено так же, как
 * чужое: канал к его собственному экрану ничем не отличается от канала к
 * другому телефону. Отдельного случая «свой игрок» не остаётся, и ошибиться в
 * нём негде.
 *
 * Читающий у канала ровно один — поток чтения. Он берёт вид, потом ход из
 * очереди, потом ответ главы, и снова вид. Двое читающих разъехались бы: ответ
 * на ход достался бы тому, кто ждал вида, и партия замерла бы на ровном месте.
 */
public final class NetGame {

    /**
     * Место главы за столом — нулевое.
     *
     * Гость своего номера не знает и знать не должен: место ему называет стол
     * первым же видом. Ход, пришедший по каналу, и без того опознан — по тому,
     * из какого канала он пришёл.
     */
    private static final int HOST = 0;

    /**
     * Экран, который сейчас смотрит на партию.
     *
     * Звать будут с потока чтения: перекинуть в главный — забота экрана, а не
     * партии. Партия про Android не знает ничего и знать не должна: иначе её
     * нельзя было бы прогнать вхолостую, без телефона.
     */
    public interface Screen {

        /** Свежий вид: нарисовать. Первый вид после подключения — текущий. */
        void view(Seat seat);

        /** Слова от стола: отказ или обрыв. Экран скажет их вслух сам. */
        void word(String text);

        /** Партия кончилась — связь закрылась или доиграна. */
        void ended();
    }

    private final Guest guest;

    /** null, если стол не мой: я сел за чужой. */
    private final Host host;
    private final boolean mine;

    /** Глава стола ведёт партию. У гостя его нет. */
    private final Thread keeper;

    /** Читает канал и шлёт мои ходы. Один на партию, живёт до её конца. */
    private final Thread reader;

    /** Мои ходы, ждущие отправки. */
    private final BlockingQueue<Wire.Move> moves = new LinkedBlockingQueue<>();

    /** Кому рисуем. null — экрана сейчас нет, партия ждёт его возвращения. */
    private volatile Screen screen;

    /** Последний вид: его получает экран, который вернулся. */
    private volatile Seat last;

    /** Ход отослан и ждёт ответа главы: второй в это время слать нельзя. */
    private volatile boolean waiting;

    /** Партию закрыли сами — тогда оборванная связь не новость. */
    private volatile boolean stopped;

    /** Что сломалось. Читается с другого конца — отсюда volatile. */
    private volatile String trouble;

    private NetGame(Guest guest, Host host, boolean mine, String name) {
        this.guest = guest;
        this.host = host;
        this.mine = mine;

        if (!mine) {
            // Себя называют первым словом, до партии, и до того, как за канал
            // взялся читающий поток: иначе это слово и ответ на ход разъехались
            // бы. Своё имя главе называть незачем — он и так знает.
            try {
                guest.hello(name);
            } catch (IOException broken) {
                trouble = trouble(broken);
            }
        }

        // Оба сторожа, а не работники: партия не должна держать телефон,
        // когда её уже никто не смотрит.
        this.reader = daemon(this::read, "чтение стола");
        this.keeper = host == null ? null : daemon(this::keep, "глава стола");

        this.reader.start();
        if (this.keeper != null) this.keeper.start();
    }

    private static Thread daemon(Runnable work, String name) {
        Thread thread = new Thread(work, name);
        thread.setDaemon(true);
        return thread;
    }

    /**
     * Открыть стол: я играю и веду партию, ко мне подключились гости.
     *
     * @param toGuests каналы к чужим телефонам — по одному на гостя. Сколько
     *                 их, столько мест за столом сверх моего.
     */
    public static NetGame open(int deckSize, long seed, String name, Link[] toGuests) {
        int players = toGuests.length + 1;
        Table table = new Table(deckSize, seed, players);
        // Своё имя глава называет столу сам: канал к своему экрану — такой же,
        // как чужой, но имя по нему не поедет, и стол узнал бы его только от
        // гостя, то есть никогда.
        table.setName(HOST, name);
        // Раздача идёт здесь, а не на экране: экран получает её уже видом, как и
        // всякий другой игрок.
        table.start();

        Link[] self = MemoryLink.pair("свой стол", "свой экран");
        Link[] atTable = new Link[players];
        atTable[HOST] = self[0];
        for (int i = 0; i < toGuests.length; i++) atTable[i + 1] = toGuests[i];

        return new NetGame(new Guest(self[1]), new Host(table, atTable), true, name);
    }

    /** Сесть за чужой стол: я играю, а ведёт партию тот, кто её открыл. */
    public static NetGame join(Link link, String name) {
        return new NetGame(new Guest(link), null, false, name);
    }

    // ----- экран приходит и уходит -----

    /**
     * Показать партию экрану.
     *
     * Если партия уже шла, экран сейчас же получает последний вид: человек
     * вернулся за стол, и заставлять его ждать чужого хода незачем.
     */
    public void attach(Screen on) {
        screen = on;
        Seat shown = last;
        if (shown != null) on.view(shown);
    }

    /** Экран ушёл. Партия остаётся — он может вернуться. */
    public void detach(Screen on) {
        if (screen == on) screen = null;
    }

    // ----- что экран делает с партией -----

    /**
     * Отдать ход столу.
     *
     * Через очередь, а не прямо в канал: отправка ждёт ответа главы, а ждать в
     * потоке, который рисует экран, нельзя — экран замёрз бы вместе с ней.
     *
     * @return false — ход уже в пути, второй слать нельзя.
     */
    public boolean move(Wire.Move move) {
        if (waiting || stopped) return false;
        waiting = true;
        moves.add(move);
        return true;
    }

    /** Ход отправлен и ждёт ответа: экран в это время нажатий не принимает. */
    public boolean busy() {
        return waiting;
    }

    /** Веду ли партию я: у гостя об этом не спросить, он этого и не знает. */
    public boolean mine() {
        return mine;
    }

    /** Кто за чужим столом — для экрана: «телефон Валеры». */
    public String peer() {
        return guest.host();
    }

    /** Что сломалось в партии. null — всё шло как надо. */
    public String trouble() {
        return trouble;
    }

    /** Разойтись по домам: закрыть каналы и отпустить оба потока. */
    public void stop() {
        stopped = true;
        if (host != null) host.stop();
        guest.close();
        // Чтение сторожат двое: один ждёт вида из канала, другой — хода от
        // экрана. Закрытый канал будит первого, прерывание — второго.
        reader.interrupt();
        if (keeper != null) keeper.interrupt();
    }

    // ----- потоки партии -----

    /**
     * Всё общение со столом: вид — ход — ответ — снова вид.
     *
     * Здесь ждут и вида, и ответа, и хода от экрана.
     */
    private void read() {
        try {
            while (true) {
                Seat fresh = guest.receive();
                if (fresh == null) break;
                last = fresh;
                tell(fresh);

                if (fresh.over()) continue;

                // Ход мой — ждём, пока экран его выберет: спешить некуда, стол
                // и так ждёт меня. Не мой — забираем только то, что уже
                // попросили: сдаться и раздать заново можно и в чужой ход, но
                // уедет такая просьба со следующим видом, а не с этим. Ждать
                // чужого хода тут нечем — вид придёт, когда соперник сходит.
                Wire.Move move = fresh.turn() == fresh.me ? moves.take() : moves.poll();
                if (move == null) continue;

                String reason = guest.send(move);
                // Ответ получен — стол ход разобрал. Только теперь экран снова
                // волен нажимать.
                waiting = false;
                if (reason != null) word(reason);
            }
        } catch (InterruptedException closed) {
            // Партию закрыли свои же: это не обрыв, и говорить не о чем.
            Thread.currentThread().interrupt();
            return;
        } catch (IOException | RuntimeException broken) {
            // RuntimeException тоже: кривая строка из канала — такая же беда,
            // как оборванный провод, и смолчать о ней нельзя.
            if (!stopped) trouble = trouble(broken);
        }

        if (stopped) return;
        Seat shown = last;
        // Партия доиграна: последний вид уже сказал игроку, чем всё кончилось,
        // и объявлять поверх этого «связь пропала» было бы враньём.
        if (shown != null && shown.over()) return;
        ended();
    }

    /** Глава стола: вести партию, пока она не кончится. */
    private void keep() {
        try {
            host.run();
        } catch (IOException | RuntimeException broken) {
            // Партию закрыли свои же — тогда и говорить не о чем: и оборванный
            // канал, и прерванное ожидание здесь означают одно и то же.
            if (!stopped) trouble = trouble(broken);
        } finally {
            // Партия кончилась — каналы закрываются в любом случае, и в том
            // числе свой собственный. Иначе свой же экран остался бы ждать вид,
            // которого уже никто не пришлёт.
            host.stop();
        }
    }

    private static String trouble(Exception what) {
        String why = what.getMessage();
        return why == null || why.isEmpty() ? "Связь оборвалась." : "Связь оборвалась: " + why;
    }

    private void tell(Seat seat) {
        Screen on = screen;
        if (on != null) on.view(seat);
    }

    private void word(String text) {
        Screen on = screen;
        if (on != null) on.word(text);
    }

    private void ended() {
        Screen on = screen;
        if (on != null) on.ended();
    }

    // ----- партия, которую сейчас играют -----

    /**
     * Партия живёт не на экране, а здесь.
     *
     * Связь заводится на экране «игра вдвоём», а играют на экране стола — это
     * разные Activity, и сокет через Intent не передать. Держится она до конца
     * партии, а не до первого взятия: экран стола может уйти и вернуться, и
     * каждый раз должен найти свою партию на месте.
     *
     * Забыть её обязан тот, кто закрывает: иначе брошенная связь всплыла бы на
     * следующей партии.
     */
    private static volatile NetGame live;

    /** Взять партию под руку, пока её не закрыли. */
    public static void leave(NetGame game) {
        live = game;
    }

    /** Партия, которую сейчас играют. null — ничего не идёт. */
    public static NetGame live() {
        return live;
    }

    /** Партия кончилась — забыть её. */
    public static void forget(NetGame game) {
        if (live == game) live = null;
    }
}

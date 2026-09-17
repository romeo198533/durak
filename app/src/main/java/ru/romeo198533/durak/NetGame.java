package ru.romeo198533.durak;

import java.io.IOException;

/**
 * Партия вдвоём по проводу.
 *
 * Экран знает про неё только три вещи: получить свежий вид, отослать ход и
 * закрыть за собой. Кто ведёт стол и откуда взялся канал — спрятано здесь.
 * Поэтому экрану всё равно, играет ли он с программой через канал в одной
 * памяти или с чужим телефоном по Блютусу: {@link Link} один и тот же.
 *
 * Глава стола — тот, кто открыл стол. Своё место у него устроено так же, как
 * чужое: канал к его собственному экрану ничем не отличается от канала к
 * другому телефону. Отдельного случая «свой игрок» не остаётся, и ошибиться в
 * нём негде.
 *
 * Читать зовут из одного потока и по очереди: сначала вид, потом ход. Так у
 * канала ровно один читающий. Иначе двое ждали бы на одном проводе разом, и
 * ответ на ход мог бы достаться тому, кто ждал вида.
 */
public final class NetGame {

    /** Кто за столом глава, а кто пришёл: места у главы и гостя свои. */
    private static final int HOST = 0;
    private static final int GUEST = 1;

    private final Guest guest;

    /** null, если стол не мой: я сел за чужой. */
    private final Host host;
    private final Thread keeper;
    private final boolean mine;

    /** Что сломалось у главы. Читается с другого конца — отсюда volatile. */
    private volatile String trouble;

    private NetGame(Guest guest, Host host, boolean mine) {
        this.guest = guest;
        this.host = host;
        this.mine = mine;

        if (host == null) {
            this.keeper = null;
            return;
        }
        this.keeper = new Thread(this::keep, "глава стола");
        // Сторож, а не работник: партия не должна держать телефон, когда экран
        // уже закрыт.
        this.keeper.setDaemon(true);
        this.keeper.start();
    }

    /**
     * Открыть стол: я играю и веду партию, ко мне подключился гость.
     *
     * @param toGuest канал к чужому телефону.
     */
    public static NetGame open(int deckSize, long seed, Link toGuest) {
        Table table = new Table(deckSize, seed);
        // Раздача идёт здесь, а не на экране: экран получает её уже видом, как и
        // всякий другой игрок.
        table.start();

        Link[] self = MemoryLink.pair("свой стол", "свой экран");
        Link[] atTable = new Link[Table.SEATS];
        atTable[HOST] = self[0];
        atTable[GUEST] = toGuest;

        return new NetGame(new Guest(self[1]), new Host(table, atTable), true);
    }

    /** Сесть за чужой стол: я играю, а ведёт партию тот, кто её открыл. */
    public static NetGame join(Link link) {
        return new NetGame(new Guest(link), null, false);
    }

    /**
     * Дождаться свежего вида.
     *
     * Звать из своего потока: здесь ждут. Пока партия идёт, вид приходит после
     * каждого хода — и своего, и чужого.
     *
     * @return вид или null, если стол закрылся.
     */
    public Seat receive() throws IOException {
        return guest.receive();
    }

    /**
     * Отправить ход и дождаться ответа главы.
     *
     * Ждать обязательно: иначе второй ход ушёл бы вдогонку первому и попал в
     * партию тогда, когда его никто не ждёт.
     *
     * @return null — ход принят; иначе причина отказа, словами для игрока.
     */
    public String send(Wire.Move move) throws IOException {
        return guest.send(move);
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

    /** Разойтись по домам: закрыть каналы и отпустить поток главы. */
    public void stop() {
        if (host != null) host.stop();
        guest.close();
        if (keeper != null) keeper.interrupt();
    }

    private void keep() {
        try {
            host.run();
        } catch (IOException | RuntimeException stopped) {
            String why = stopped.getMessage();
            trouble = why == null || why.isEmpty()
                    ? "Связь оборвалась."
                    : "Связь оборвалась: " + why;
        } finally {
            // Партия кончилась — каналы закрываются в любом случае, и в том
            // числе свой собственный. Иначе свой же экран остался бы ждать вид,
            // которого уже никто не пришлёт.
            host.stop();
        }
    }

    // ----- готовый канал, ждущий свой экран -----

    /**
     * Партия, открытая на одном экране и нужная на другом.
     *
     * Связь заводится на экране «игра вдвоём», а играют на экране стола — это
     * разные Activity, и сокет через Intent не передать. Поле живёт ровно до
     * первого взятия: второй экран забирает партию и обнуляет ссылку, чтобы
     * брошенное соединение не всплыло на следующей партии.
     *
     * Экран, который связь открыл, обязан её положить — иначе она пропадёт
     * вместе с ним, и спрашивать будет не с кого.
     */
    private static volatile NetGame waiting;

    /** Положить готовую партию, пока её не забрал экран стола. */
    public static void leave(NetGame game) {
        waiting = game;
    }

    /** Забрать партию. Второй раз её уже не взять. */
    public static NetGame take() {
        NetGame out = waiting;
        waiting = null;
        return out;
    }
}

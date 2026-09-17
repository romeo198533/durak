import ru.romeo198533.durak.Bot;
import ru.romeo198533.durak.Card;
import ru.romeo198533.durak.Guest;
import ru.romeo198533.durak.Host;
import ru.romeo198533.durak.Link;
import ru.romeo198533.durak.MemoryLink;
import ru.romeo198533.durak.Seat;
import ru.romeo198533.durak.Table;
import ru.romeo198533.durak.Wire;
import ru.romeo198533.durak.Words;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Проверка сетевой партии без телефонов.
 *
 * Гоняет партии двумя способами.
 *
 * Первый — напрямую: глава стола и оба игрока в одном потоке, ходы ходят
 * строкой через {@link Wire}. Тут проверяется, что вид места полон и что чужого
 * в нём нет.
 *
 * Второй — через каналы: у главы на каждого игрока свой {@link Link}, игроки
 * сидят в своих потоках и играют всерьёз — ждут вид, шлют ход, ждут ответ.
 * Каналы при этом не настоящие, а в одной памяти, — но партия про них этого не
 * знает и ведёт себя точно так же, как поведёт по Блютусу. Здесь проверяется
 * то, что на живых телефонах проверить нечем: что партия не замирает и не
 * расходится.
 *
 * Запуск:
 *
 *   javac -d /tmp/durak-net app/src/main/java/ru/romeo198533/durak/*.java tools/NetTest.java
 *   java -cp /tmp/durak-net NetTest
 */
public final class NetTest {

    private static int games;
    private static int rounds;
    private static int takes;
    private static int surrenders;
    private static int restarts;

    private static final AtomicInteger linkedGames = new AtomicInteger();
    private static final AtomicInteger linkedRounds = new AtomicInteger();
    private static final AtomicInteger linkedTakes = new AtomicInteger();

    /** Что сломалось в чужом потоке: там не бросить, туда и складываем. */
    private static volatile String failure;

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 300;

        for (int players : new int[]{2, 3}) {
            for (int size : new int[]{36, 52}) {
                for (int seed = 0; seed < count; seed++) {
                    play(size, seed, players);
                }
            }
        }

        // Те же счётчики, что и в EngineTest, и цифры обязаны совпасть: игроки
        // там и тут одни и те же, и провод не должен менять ни одного хода.
        // Совпали — значит, через строку не потерялось ничего, и оба раза
        // сыграна одна и та же партия. Обе проверки идут в журнале сборки
        // подряд, так что это видно глазом.
        System.out.println("напрямую, партий: " + games);
        System.out.println("напрямую, кругов: " + rounds);
        System.out.println("напрямую, взятий: " + takes);
        System.out.println("ходов мимо правил: 0");
        System.out.println("чужого в виде места: нет");

        for (int players : new int[]{2, 3, 4}) {
            for (int size : new int[]{36, 52}) {
                for (int seed = 0; seed < count; seed++) {
                    playSurrender(size, seed, players);
                }
            }
        }
        System.out.println("сдач проверено: " + surrenders);

        for (int players : new int[]{2, 3, 4}) {
            for (int size : new int[]{36, 52}) {
                for (int seed = 0; seed < count; seed++) {
                    playRestart(size, seed, players);
                }
            }
        }
        System.out.println("раздач заново проверено: " + restarts);

        for (int players : new int[]{2, 3}) {
            for (int size : new int[]{36, 52}) {
                for (int seed = 0; seed < count; seed++) {
                    playLinked(size, seed, players);
                }
            }
        }

        // Через каналы ходов не меньше, а партий столько же: иначе какая-то
        // партия замерла и её пришлось бы искать на телефоне.
        System.out.println("через каналы, партий: " + linkedGames.get());
        System.out.println("через каналы, кругов: " + linkedRounds.get());
        System.out.println("через каналы, взятий: " + linkedTakes.get());
        System.out.println("ошибок нет");

        System.out.println("вид места едет так:");
        Table sample = new Table(36, 0, 3);
        sample.start();
        System.out.println(Wire.encode(sample.seatFor(0)));
    }

    // ----- напрямую -----

    private static void play(int deckSize, int seed, int players) {
        Table table = new Table(deckSize, seed, players);
        table.start();

        // Игроки — те же программы, что и в одиночной партии: за сетевым
        // столом бот такой же игрок, как человек, и решает по своему виду.
        Bot[] bots = new Bot[players];
        for (int p = 0; p < players; p++) {
            bots[p] = new Bot(p, seed * 7 + p + 1);
            // Имена раздаются до начала: за столом называют вслух того, кто
            // сдался, и того, чей ход.
            table.setName(p, "Игрок " + (p + 1));
        }

        // Последний сыгранный ход: с ним сверяется то, что приехало в виде.
        int wasWho = -1;
        int wasKind = -1;
        Card wasCard = null;

        int guard = 0;
        while (!table.isOver()) {
            if (++guard > 20000) {
                throw new IllegalStateException("партия " + seed + " не кончается");
            }

            // Каждому едет его вид — строкой, как поедет по проводу.
            String[] lines = new String[players];
            Seat[] seats = new Seat[players];
            for (int p = 0; p < players; p++) {
                lines[p] = Wire.encode(table.seatFor(p));
                seats[p] = Wire.decode(lines[p]);
            }
            for (int p = 0; p < players; p++) {
                checkSeat(seats[p], p, seed);
                checkNoLeak(lines[p], p, seats, seed);
                checkLast(seats[p], wasWho, wasKind, wasCard, seed);
            }
            checkTable(seats, seed);

            int who = seats[0].turn();
            if (who < 0) break;

            // Ход выбирается по одному только виду места — другого игрок не
            // знает. Ход уходит строкой и возвращается разобранным.
            Wire.Move move = Wire.decodeMove(Wire.encode(decide(bots[who], seats[who])));
            String reason = table.act(who, move);

            // Отказ главы стола там, где игрок по своему виду считал ход
            // законным, — это расхождение подсказки с правилами. Молчать о нём
            // нельзя: на телефоне оно выглядело бы как «кнопка не работает».
            String told = Wire.decodeReply(Wire.encodeReply(reason));
            if (told != null) {
                throw new IllegalStateException("партия " + seed + ": игрок " + who
                        + " сходил " + move.kind + ", а стол отказал: " + told);
            }

            if (move.kind == Wire.Move.PASS) rounds++;
            if (move.kind == Wire.Move.TAKE) takes++;

            wasWho = who;
            wasKind = move.kind;
            wasCard = move.card;
        }

        games++;
    }

    // ----- через каналы -----

    /**
     * Партия, где глава и игроки сидят в разных потоках и говорят по каналам.
     *
     * Всё как на телефонах, кроме одного: канал не радио, а общая память.
     * Значит, проверяются не разрешения и не спаривание, а сама партия поверх
     * связи — то, что на живых телефонах искать дольше всего.
     */
    private static void playLinked(int deckSize, int seed, int players) throws Exception {
        if (failure != null) throw new IllegalStateException("партия " + seed + ": " + failure);

        Table table = new Table(deckSize, seed, players);
        for (int p = 0; p < players; p++) table.setName(p, "Игрок " + (p + 1));
        table.start();

        // По каналу на каждое место, включая место самого главы: своё ничем
        // не отличается от чужого, и отдельного случая «свой игрок» нет.
        Link[][] ends = new Link[players][];
        Link[] atTable = new Link[players];
        Guest[] guests = new Guest[players];
        for (int p = 0; p < players; p++) {
            ends[p] = MemoryLink.pair("глава", "игрок " + (p + 1));
            atTable[p] = ends[p][0];
            guests[p] = new Guest(ends[p][1]);
        }

        Host host = new Host(table, atTable);

        Bot[] bots = new Bot[players];
        for (int p = 0; p < players; p++) bots[p] = new Bot(p, seed * 7 + p + 1);

        Thread[] all = new Thread[players + 1];
        all[0] = new Thread(() -> {
            try {
                host.run();
            } catch (IOException | RuntimeException stopped) {
                failure = "глава: " + stopped;
            }
        }, "глава");
        for (int p = 0; p < players; p++) {
            final int seat = p;
            all[p + 1] = new Thread(() -> {
                try {
                    guestLoop(guests[seat], bots[seat]);
                } catch (IOException | RuntimeException stopped) {
                    failure = "игрок " + seat + ": " + stopped;
                }
            }, "игрок " + p);
        }

        for (Thread thread : all) thread.start();
        for (Thread thread : all) thread.join(60000);
        for (Thread thread : all) {
            if (thread.isAlive()) {
                throw new IllegalStateException("партия " + seed
                        + ": поток «" + thread.getName() + "» не дождался конца");
            }
        }
        if (failure != null) throw new IllegalStateException("партия " + seed + ": " + failure);

        linkedGames.incrementAndGet();
    }

    /** Игрок за чужим столом: ждёт вид, ходит, когда его черёд, ждёт ответа. */
    private static void guestLoop(Guest guest, Bot bot) throws IOException {
        while (true) {
            Seat seat = guest.receive();
            if (seat == null || seat.over()) return;

            checkFresh(seat);
            if (seat.turn() != seat.me) continue;

            Wire.Move move = decide(bot, seat);
            String reason = guest.send(move);
            if (reason != null) {
                throw new IllegalStateException("глава отказал игроку " + seat.me + ": " + reason);
            }

            if (move.kind == Wire.Move.PASS) linkedRounds.incrementAndGet();
            if (move.kind == Wire.Move.TAKE) linkedTakes.incrementAndGet();
        }
    }

    // ----- общее -----

    /**
     * Вид, приехавший по каналу, не врёт про последний ход.
     *
     * Сверить его с настоящим здесь нечем: игрок видит только свой вид. Но
     * бессмыслицу видно и так — ход картой без карты, «бито» с картой, чужое
     * место за столом. Через канал едет та же строка, что разобрана выше, так
     * что этого довольно.
     */
    private static void checkFresh(Seat seat) {
        if (seat.lastWho < 0) return;

        if (seat.lastWho >= seat.seats) {
            throw new IllegalStateException("последним ходил игрок " + seat.lastWho
                    + ", а за столом мест " + seat.seats);
        }
        boolean byCard = seat.lastKind == Wire.Move.HIT || seat.lastKind == Wire.Move.BEAT;
        if (byCard != (seat.lastCard != null)) {
            throw new IllegalStateException("последний ход " + seat.lastKind
                    + " назван с картой " + seat.lastCard);
        }
    }

    /**
     * Ход игрока по его виду места.
     *
     * Программа называет карту или говорит, что ходить нечем: молчание бота
     * значит «бито», молчание защищающегося — «беру». Так же поступит и
     * живой игрок: карта или отказ.
     */
    private static Wire.Move decide(Bot bot, Seat mine) {
        if (mine.myAttack()) {
            Card card = bot.attack(mine);
            return card == null ? Wire.Move.pass() : Wire.Move.hit(card);
        }
        Card card = bot.defend(mine);
        return card == null ? Wire.Move.take() : Wire.Move.beat(card);
    }

    /** Вид доехал целым: моё место, число мест, имена и своя рука на месте. */
    private static void checkSeat(Seat seat, int player, int seed) {
        if (seat.me != player) {
            throw new IllegalStateException("партия " + seed + ": вид приехал не на то место");
        }
        if (seat.seats != seat.handCounts.length || seat.names.length != seat.seats) {
            throw new IllegalStateException("партия " + seed + ": за столом " + seat.seats
                    + " мест, а рассказано про " + seat.handCounts.length
                    + " рук и " + seat.names.length + " имён");
        }
        String myName = "Игрок " + (player + 1);
        if (!myName.equals(seat.names[player])) {
            throw new IllegalStateException("партия " + seed + ": место " + player
                    + " зовут " + seat.names[player] + ", а было " + myName);
        }
        if (seat.handCounts[player] != seat.hand.size()) {
            throw new IllegalStateException("партия " + seed + ": в руке " + seat.hand.size()
                    + " карт, а объявлено " + seat.handCounts[player]);
        }
    }

    /**
     * Чужой руки в виде нет.
     *
     * Своей рукой чужой игрок не располагает: в {@link Seat} для неё и поля нет.
     * Но проверяем не уговор, а то, что уехало: ни одной карты из чужой руки в
     * строке быть не должно — ни в руке, ни в каком другом поле.
     */
    private static void checkNoLeak(String line, int player, Seat[] seats, int seed) {
        for (int other = 0; other < seats.length; other++) {
            if (other == player) continue;
            for (Card card : seats[other].hand) {
                if (line.contains(Wire.card(card))) {
                    throw new IllegalStateException("партия " + seed + ": игроку " + player
                            + " уехала чужая карта " + card.name());
                }
            }
        }
    }

    /**
     * Последний ход доехал и назван верно.
     *
     * По виду «бито» и «беру» на вид не отличить друг от друга, поэтому экран
     * узнаёт о чужом ходе только отсюда. Если поле потеряется или перепутается,
     * за столом по проводу пропадёт чужой ход — а это половина партии.
     */
    private static void checkLast(Seat seat, int who, int kind, Card card, int seed) {
        if (seat.lastWho != who || seat.lastKind != kind) {
            throw new IllegalStateException("партия " + seed + ": последний ход приехал как "
                    + seat.lastWho + "/" + seat.lastKind + ", а сыгран был " + who + "/" + kind);
        }
        boolean same = card == null ? seat.lastCard == null : card.equals(seat.lastCard);
        if (!same) {
            throw new IllegalStateException("партия " + seed + ": последним ходом названа "
                    + (seat.lastCard == null ? "никакая карта" : seat.lastCard.name())
                    + ", а сыграна " + (card == null ? "никакая" : card.name()));
        }
        if (kind == Wire.Move.PASS || kind == Wire.Move.TAKE) {
            if (seat.lastCard != null) {
                throw new IllegalStateException("партия " + seed
                        + ": у хода без карты объявилась карта " + seat.lastCard.name());
            }
        } else if (kind >= 0 && seat.lastCard == null) {
            throw new IllegalStateException("партия " + seed
                    + ": ход картой приехал без карты");
        }
    }

    /**
     * Карты не задваиваются, а про общее рассказано одинаково.
     *
     * Мест за столом бывает и три, поэтому сверяется не пара, а все места разом:
     * общее у них одно — колода, стол, чей ход, кто вышел, — и разойтись в нём
     * двум местам не в чем.
     */
    private static void checkTable(Seat[] seats, int seed) {
        for (int a = 0; a < seats.length; a++) {
            for (int b = a + 1; b < seats.length; b++) {
                for (Card card : seats[a].hand) {
                    if (seats[b].hand.contains(card)) {
                        throw new IllegalStateException("партия " + seed
                                + ": карта " + card.name() + " лежит и в руке "
                                + a + ", и в руке " + b);
                    }
                }
            }
        }

        Seat first = seats[0];
        for (int p = 1; p < seats.length; p++) {
            Seat other = seats[p];
            if (first.deckCount != other.deckCount) {
                throw new IllegalStateException("партия " + seed
                        + ": про колоду рассказано разное");
            }
            if (first.table.size() != other.table.size()) {
                throw new IllegalStateException("партия " + seed
                        + ": про стол рассказано разное");
            }
            if (first.mover != other.mover || first.defender != other.defender) {
                throw new IllegalStateException("партия " + seed
                        + ": про чей ход рассказано разное");
            }
            if (first.over() != other.over() || first.loser != other.loser) {
                throw new IllegalStateException("партия " + seed
                        + ": про конец партии рассказано разное");
            }
            if (first.lastWho != other.lastWho || first.lastKind != other.lastKind) {
                throw new IllegalStateException("партия " + seed
                        + ": про последний ход рассказано разное");
            }
            for (int who = 0; who < seats.length; who++) {
                if (first.done(who) != other.done(who)) {
                    throw new IllegalStateException("партия " + seed
                            + ": про место " + who + " рассказано разное");
                }
            }
        }
    }

    /**
     * Сдаться может любой и в любой момент — этот прогон ровно про то.
     *
     * Сдаётся место, которое в этот ход не ходит: раньше такое слово до стола
     * не дошло бы, потому что читался только канал того, чей черёд. Проверяется
     * не только конец партии, но и слова: за столом должно прозвучать имя того,
     * кто сдался, а не номер его места.
     */
    private static void playSurrender(int deckSize, int seed, int players) {
        Table table = new Table(deckSize, seed, players);
        for (int p = 0; p < players; p++) table.setName(p, "Игрок " + (p + 1));
        table.start();

        Bot[] bots = new Bot[players];
        for (int p = 0; p < players; p++) bots[p] = new Bot(p, seed * 7 + p + 1);

        // Кто сдаётся и на каком ходу — по зерну: и в свой черёд, и в чужой.
        int quitter = seed % players;
        int when = seed % 4;

        int moves = 0;
        while (!table.isOver() && moves < when) {
            int who = turnOf(table);
            if (who < 0) return;
            table.act(who, Wire.decodeMove(Wire.encode(
                    decide(bots[who], table.seatFor(who)))));
            moves++;
        }
        if (table.isOver()) return;

        // Слово о сдаче уходит тем же ходом, что и всё прочее: свой черёд или
        // чужой — столу это без разницы, и в этом весь смысл.
        String reason = table.act(quitter, Wire.decodeMove(Wire.encode(Wire.Move.surrender())));
        if (reason != null) {
            throw new IllegalStateException("партия " + seed + ": место " + quitter
                    + " сдалось, а стол отказал: " + reason);
        }

        for (int p = 0; p < players; p++) {
            Seat seat = Wire.decode(Wire.encode(table.seatFor(p)));
            if (!seat.over()) {
                throw new IllegalStateException("партия " + seed
                        + ": после сдачи партия не кончилась");
            }
            if (seat.loser != quitter) {
                throw new IllegalStateException("партия " + seed
                        + ": сдался " + quitter + ", а дураком назван " + seat.loser);
            }
            String said = Words.result(seat, p);
            String wanted = p == quitter
                    ? "Ты сдался. Партия кончена."
                    : "Игрок " + (quitter + 1) + " сдался. Ты выиграл.";
            if (!said.equals(wanted)) {
                throw new IllegalStateException("партия " + seed + ": про сдачу сказано «"
                        + said + "», а надо «" + wanted + "»");
            }
        }
        surrenders++;
    }

    /** Чей ход на столе: у главы то же, что и в виде места. */
    private static int turnOf(Table table) {
        return table.turn();
    }

    /**
     * Раздать заново — право хозяйское, и раздача при этом настоящая.
     *
     * Проверяется и отказ гостю, и то, что после раздачи стол начинается
     * сначала: круг первый, рука на месте, колода снова полная. Экран узнаёт о
     * раздаче по номеру круга — он уехал назад, — поэтому проверяется именно он.
     */
    private static void playRestart(int deckSize, int seed, int players) {
        Table table = new Table(deckSize, seed, players);
        for (int p = 0; p < players; p++) table.setName(p, "Игрок " + (p + 1));
        table.start();

        Bot[] bots = new Bot[players];
        for (int p = 0; p < players; p++) bots[p] = new Bot(p, seed * 7 + p + 1);

        // Несколько ходов, чтобы круг стал не первым: иначе раздача заново была
        // бы неотличима от начала партии.
        for (int i = 0; i < 5 && !table.isOver(); i++) {
            int who = turnOf(table);
            if (who < 0) break;
            table.act(who, Wire.decodeMove(Wire.encode(decide(bots[who], table.seatFor(who)))));
        }
        if (table.isOver()) return;

        // Гость раздать заново не может: колода лежит у хозяина стола.
        String refused = table.act(1, Wire.Move.restart());
        if (refused == null) {
            throw new IllegalStateException("партия " + seed + ": гость раздал заново");
        }

        String reason = table.act(0, Wire.Move.restart());
        if (reason != null) {
            throw new IllegalStateException("партия " + seed
                    + ": хозяин стола не смог раздать заново: " + reason);
        }

        Seat seat = Wire.decode(Wire.encode(table.seatFor(0)));
        checkSeat(seat, 0, seed);
        if (seat.round != 1 || seat.over() || seat.table.size() != 0) {
            throw new IllegalStateException("партия " + seed + ": после раздачи заново круг "
                    + seat.round + ", конец партии " + seat.over()
                    + ", карт на столе " + seat.table.size());
        }
        if (seat.lastKind != Wire.Move.RESTART) {
            throw new IllegalStateException("партия " + seed
                    + ": о раздаче заново не рассказано (последний ход " + seat.lastKind + ")");
        }
        restarts++;
    }

    private NetTest() {
    }
}

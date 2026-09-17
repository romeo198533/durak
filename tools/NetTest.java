import ru.romeo198533.durak.Bot;
import ru.romeo198533.durak.Card;
import ru.romeo198533.durak.Guest;
import ru.romeo198533.durak.Host;
import ru.romeo198533.durak.Link;
import ru.romeo198533.durak.MemoryLink;
import ru.romeo198533.durak.Seat;
import ru.romeo198533.durak.Table;
import ru.romeo198533.durak.Wire;

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

    private static final AtomicInteger linkedGames = new AtomicInteger();
    private static final AtomicInteger linkedRounds = new AtomicInteger();
    private static final AtomicInteger linkedTakes = new AtomicInteger();

    /** Что сломалось в чужом потоке: там не бросить, туда и складываем. */
    private static volatile String failure;

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 300;

        for (int size : new int[]{36, 52}) {
            for (int seed = 0; seed < count; seed++) {
                play(size, seed);
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

        for (int size : new int[]{36, 52}) {
            for (int seed = 0; seed < count; seed++) {
                playLinked(size, seed);
            }
        }

        // Через каналы ходов не меньше, а партий столько же: иначе какая-то
        // партия замерла и её пришлось бы искать на телефоне.
        System.out.println("через каналы, партий: " + linkedGames.get());
        System.out.println("через каналы, кругов: " + linkedRounds.get());
        System.out.println("через каналы, взятий: " + linkedTakes.get());
        System.out.println("ошибок нет");

        System.out.println("вид места едет так:");
        Table sample = new Table(36, 0);
        sample.start();
        System.out.println(Wire.encode(sample.seatFor(0)));
    }

    // ----- напрямую -----

    private static void play(int deckSize, int seed) {
        Table table = new Table(deckSize, seed);
        table.start();

        // Игроки — те же программы, что и в одиночной партии: за сетевым
        // столом бот такой же игрок, как человек, и решает по своему виду.
        Bot[] bots = new Bot[Table.SEATS];
        for (int p = 0; p < Table.SEATS; p++) bots[p] = new Bot(p, seed * 7 + p + 1);

        int guard = 0;
        while (!table.isOver()) {
            if (++guard > 20000) {
                throw new IllegalStateException("партия " + seed + " не кончается");
            }

            // Каждому едет его вид — строкой, как поедет по проводу.
            String[] lines = new String[Table.SEATS];
            Seat[] seats = new Seat[Table.SEATS];
            for (int p = 0; p < Table.SEATS; p++) {
                lines[p] = Wire.encode(table.seatFor(p));
                seats[p] = Wire.decode(lines[p]);
            }
            for (int p = 0; p < Table.SEATS; p++) {
                checkSeat(seats[p], p, seed);
                checkNoLeak(lines[p], p, seats, seed);
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
    private static void playLinked(int deckSize, int seed) throws Exception {
        if (failure != null) throw new IllegalStateException("партия " + seed + ": " + failure);

        Table table = new Table(deckSize, seed);
        table.start();

        // По каналу на каждое место, включая место самого главы: своё ничем
        // не отличается от чужого, и отдельного случая «свой игрок» нет.
        Link[][] ends = new Link[Table.SEATS][];
        Link[] atTable = new Link[Table.SEATS];
        Guest[] guests = new Guest[Table.SEATS];
        for (int p = 0; p < Table.SEATS; p++) {
            ends[p] = MemoryLink.pair("глава", "игрок " + (p + 1));
            atTable[p] = ends[p][0];
            guests[p] = new Guest(ends[p][1]);
        }

        Host host = new Host(table, atTable);

        Bot[] bots = new Bot[Table.SEATS];
        for (int p = 0; p < Table.SEATS; p++) bots[p] = new Bot(p, seed * 7 + p + 1);

        Thread[] all = new Thread[Table.SEATS + 1];
        all[0] = new Thread(() -> {
            try {
                host.run();
            } catch (IOException | RuntimeException stopped) {
                failure = "глава: " + stopped;
            }
        }, "глава");
        for (int p = 0; p < Table.SEATS; p++) {
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

    /** Вид доехал целым: моё место, число мест и своя рука на месте. */
    private static void checkSeat(Seat seat, int player, int seed) {
        if (seat.me != player) {
            throw new IllegalStateException("партия " + seed + ": вид приехал не на то место");
        }
        if (seat.seats != Table.SEATS || seat.handCounts.length != Table.SEATS) {
            throw new IllegalStateException("партия " + seed + ": за столом не "
                    + Table.SEATS + " мест");
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

    /** Карты не задваиваются: руки не пересекаются, а стол не растёт сам собой. */
    private static void checkTable(Seat[] seats, int seed) {
        for (Card card : seats[0].hand) {
            if (seats[1].hand.contains(card)) {
                throw new IllegalStateException("партия " + seed
                        + ": карта " + card.name() + " лежит в обеих руках");
            }
        }

        if (seats[0].deckCount != seats[1].deckCount) {
            throw new IllegalStateException("партия " + seed + ": про колоду рассказано разное");
        }
        if (seats[0].table.size() != seats[1].table.size()) {
            throw new IllegalStateException("партия " + seed + ": про стол рассказано разное");
        }
    }

    private NetTest() {
    }
}

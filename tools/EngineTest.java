import ru.romeo198533.durak.Bot;
import ru.romeo198533.durak.Card;
import ru.romeo198533.durak.Game;

/**
 * Проверка правил без телефона.
 *
 * Гоняет партии, где оба игрока — программы, и на каждом ходу сверяет, что
 * карты не пропадают и не берутся из воздуха. Запуск:
 *
 *   javac -d /tmp/durak-test app/src/main/java/ru/romeo198533/durak/*.java tools/EngineTest.java
 *   java -cp /tmp/durak-test EngineTest
 */
public final class EngineTest {

    private static int games;
    private static int rounds;
    private static int takes;
    private static int draws;

    public static void main(String[] args) {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 300;

        for (int size : new int[]{36, 52}) {
            for (int seed = 0; seed < count; seed++) {
                play(size, seed);
            }
        }

        System.out.println("партий: " + games);
        System.out.println("кругов: " + rounds);
        System.out.println("взятий: " + takes);
        System.out.println("ничьих: " + draws);
        System.out.println("ошибок нет");
    }

    private static void play(int deckSize, int seed) {
        Game game = new Game(deckSize, seed);
        game.start();
        Bot first = new Bot(0, seed * 7 + 1);
        Bot second = new Bot(1, seed * 13 + 2);

        int guard = 0;
        while (!game.isOver()) {
            if (++guard > 20000) {
                throw new IllegalStateException("партия " + seed + " не кончается");
            }
            check(game, deckSize, seed);

            if (game.phase() == Game.PHASE_ATTACK) {
                Bot bot = game.attacker() == 0 ? first : second;
                Card card = bot.attack(game);
                if (card == null) {
                    if (!game.canPass()) {
                        throw new IllegalStateException(
                                "партия " + seed + ": «бито» без права");
                    }
                    game.pass();
                    rounds++;
                } else if (!game.attack(card)) {
                    throw new IllegalStateException(
                            "партия " + seed + ": не принят заход " + card);
                }
            } else if (game.phase() == Game.PHASE_DEFEND) {
                Bot bot = game.defender() == 0 ? first : second;
                Card card = bot.defend(game);
                if (card == null) {
                    if (!game.canTake()) {
                        throw new IllegalStateException(
                                "партия " + seed + ": взятие без права");
                    }
                    game.take();
                    takes++;
                } else if (!game.defend(card)) {
                    throw new IllegalStateException(
                            "партия " + seed + ": не принят отбой " + card);
                }
            }
        }

        check(game, deckSize, seed);
        games++;
        if (game.isDraw()) draws++;
    }

    /** Карт ровно столько, сколько было в колоде: ни одна не потерялась. */
    private static void check(Game game, int deckSize, int seed) {
        int total = game.deckCount() + game.discardCount()
                + game.handCount(0) + game.handCount(1);
        for (Game.Slot slot : game.table()) {
            total++;
            if (slot.beaten()) total++;
        }
        if (total != deckSize) {
            throw new IllegalStateException("партия " + seed
                    + ": карт " + total + ", а должно быть " + deckSize);
        }
    }

    private EngineTest() {
    }
}

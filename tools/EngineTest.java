import ru.romeo198533.durak.Bot;
import ru.romeo198533.durak.Card;
import ru.romeo198533.durak.Game;

/**
 * Проверка правил без телефона.
 *
 * Гоняет партии, где все игроки — программы, и на каждом ходу сверяет, что
 * карты не пропадают и не берутся из воздуха. Играется и на двоих, и на
 * троих: правила одни, и трое — не отдельная игра, а тот же круг, где
 * подкинуть может не один, а двое.
 *
 * Числа на двоих — не украшение, а память: движок обобщался на N мест, и
 * партии на двоих обязаны остаться ровно теми же (9563 круга, 2908 взятий).
 * Разошлись — значит, обобщение что-то сломало.
 *
 * Запуск:
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

        for (int players : new int[]{2, 3}) {
            games = 0;
            rounds = 0;
            takes = 0;
            draws = 0;
            for (int size : new int[]{36, 52}) {
                for (int seed = 0; seed < count; seed++) {
                    play(size, seed, players);
                }
            }
            System.out.println("на " + players + "х: партий " + games
                    + " / кругов " + rounds + " / взятий " + takes
                    + " / ничьих " + draws);
        }
        System.out.println("ошибок нет");
    }

    private static void play(int deckSize, int seed, int players) {
        Game game = new Game(deckSize, seed, players);
        game.start();
        Bot[] bots = new Bot[players];
        for (int p = 0; p < players; p++) {
            bots[p] = new Bot(p, seed * (7 + p * 6) + p);
        }

        int guard = 0;
        while (!game.isOver()) {
            if (++guard > 20000) {
                throw new IllegalStateException("партия " + seed + " не кончается");
            }
            check(game, deckSize, seed);

            if (game.phase() == Game.PHASE_ATTACK) {
                if (game.mover() == game.defender()) {
                    throw new IllegalStateException(
                            "партия " + seed + ": подкидывает защищающийся");
                }
                Card card = bots[game.mover()].attack(game);
                if (card == null) {
                    if (!game.canPass()) {
                        throw new IllegalStateException(
                                "партия " + seed + ": «бито» без права");
                    }
                    game.pass();
                    rounds++;
                } else {
                    if (!game.attackOptions().contains(card)) {
                        throw new IllegalStateException(
                                "партия " + seed + ": ход не из доступных " + card);
                    }
                    if (!game.attack(card)) {
                        throw new IllegalStateException(
                                "партия " + seed + ": не принят заход " + card);
                    }
                }
            } else if (game.phase() == Game.PHASE_DEFEND) {
                Card card = bots[game.defender()].defend(game);
                if (card == null) {
                    if (!game.canTake()) {
                        throw new IllegalStateException(
                                "партия " + seed + ": взятие без права");
                    }
                    game.take();
                    takes++;
                } else {
                    if (!game.defendOptions().contains(card)) {
                        throw new IllegalStateException(
                                "партия " + seed + ": отбой не из доступных " + card);
                    }
                    if (!game.defend(card)) {
                        throw new IllegalStateException(
                                "партия " + seed + ": не принят отбой " + card);
                    }
                }
            }
        }

        check(game, deckSize, seed);
        checkEnd(game, seed, players);
        games++;
        if (game.isDraw()) draws++;
    }

    /** Карт ровно столько, сколько было в колоде: ни одна не потерялась. */
    private static void check(Game game, int deckSize, int seed) {
        int total = game.deckCount() + game.discardCount();
        for (int p = 0; p < game.players(); p++) {
            total += game.handCount(p);
        }
        for (Game.Slot slot : game.table()) {
            total++;
            if (slot.beaten()) total++;
        }
        if (total != deckSize) {
            throw new IllegalStateException("партия " + seed
                    + ": карт " + total + ", а должно быть " + deckSize);
        }

        // Круг кончается взятием или «бито», а не отбоем: неотбитая карта на
        // столе в нападении означала бы, что партия пошла дальше, не спросив
        // защищающегося.
        if (game.phase() == Game.PHASE_ATTACK && game.unbeatenCount() != 0) {
            throw new IllegalStateException("партия " + seed + ": подкидывают по неотбитой карте");
        }
    }

    /** Кончилась партия правильно: дурак один, и он с картами. */
    private static void checkEnd(Game game, int seed, int players) {
        int left = 0;
        for (int p = 0; p < players; p++) {
            if (game.handCount(p) > 0) left++;
        }

        if (game.isDraw()) {
            if (left != 0) {
                throw new IllegalStateException("партия " + seed
                        + ": ничья, а карты остались у " + left);
            }
            return;
        }
        if (left != 1) {
            throw new IllegalStateException("партия " + seed
                    + ": партия кончена, а с картами " + left);
        }
        if (game.handCount(game.loser()) == 0) {
            throw new IllegalStateException("партия " + seed
                    + ": дурак назван, а рука у него пуста");
        }
    }

    private EngineTest() {
    }
}

package ru.romeo198533.durak;

import java.util.ArrayList;
import java.util.List;

/**
 * Стол, который ведёт глава: настоящая партия и раздача видов по местам.
 *
 * Партия живёт здесь, у одного из игроков, — в одном экземпляре на всех. Всё
 * остальное получают виды: {@link #seatFor} собирает для каждого ровно то, что
 * ему положено знать, а {@link #act} принимает ход и проверяет его по общим
 * правилам. Клиент судит ходы у себя лишь для подсказки; последнее слово всегда
 * здесь, и отказ возвращается словами, а не молчанием.
 *
 * Пока мест двое — движок {@link Game} написан на двоих. Третий игрок придёт
 * вместе с обобщением движка на N мест, и вид места уже к этому готов: чужие
 * руки в нём идут числом, а не списком.
 */
public final class Table {

    /** Сколько мест за столом. */
    public static final int SEATS = 2;

    private final Game game;

    /** Номер круга, с единицы. Круг кончается «бито» или взятием. */
    private int round = 1;

    /**
     * @param deckSize 36 или 52 карты.
     * @param seed     зерно перемешивания: с ним партию можно повторить.
     */
    public Table(int deckSize, long seed) {
        this.game = new Game(deckSize, seed);
    }

    public void start() {
        game.start();
        round = 1;
    }

    public boolean isOver() {
        return game.isOver();
    }

    /** Чья очередь ходить. -1 — партия кончена. */
    public int turn() {
        if (game.isOver()) return -1;
        return game.phase() == Game.PHASE_DEFEND ? game.defender() : game.attacker();
    }

    /** Вид места: то, что уедет этому игроку и только ему. */
    public Seat seatFor(int player) {
        Seat seat = new Seat();
        seat.me = player;
        seat.seats = SEATS;
        seat.round = round;
        seat.trump = game.trump();
        seat.trumpCard = game.trumpCard();
        seat.deckCount = game.deckCount();
        seat.attacker = game.attacker();
        seat.defender = game.defender();
        seat.phase = game.phase();
        seat.loser = game.loser();
        seat.draw = game.isDraw();

        seat.handCounts = new int[SEATS];
        for (int p = 0; p < SEATS; p++) seat.handCounts[p] = game.handCount(p);

        List<Seat.Pair> pairs = new ArrayList<>();
        for (Game.Slot slot : game.table()) {
            pairs.add(new Seat.Pair(slot.attack, slot.defend));
        }
        seat.table = pairs;

        // Рука — по порядку показа, а не в порядке хранения: раскладывать её
        // приходится на каждом ходу, и незачем делать это дважды.
        seat.hand = game.handSorted(player);
        return seat;
    }

    /**
     * Принять ход от игрока.
     *
     * @return null — ход принят; иначе причина отказа, словами для игрока.
     */
    public String act(int player, Wire.Move move) {
        if (game.isOver()) return "Партия кончена.";
        if (player != game.attacker() && player != game.defender()) {
            return "За этим столом нет такого места.";
        }

        switch (move.kind) {
            case Wire.Move.HIT:
                if (move.card == null) return "Заход без карты.";
                if (game.phase() != Game.PHASE_ATTACK || game.attacker() != player) {
                    return "Сейчас не твой ход.";
                }
                if (!game.attackOptions().contains(move.card)) return "Так зайти нельзя.";
                game.attack(move.card);
                return null;

            case Wire.Move.BEAT:
                if (move.card == null) return "Отбой без карты.";
                if (game.phase() != Game.PHASE_DEFEND || game.defender() != player) {
                    return "Сейчас не твой ход.";
                }
                if (!game.defendOptions().contains(move.card)) return "Эта карта не бьёт.";
                game.defend(move.card);
                return null;

            case Wire.Move.PASS:
                if (game.attacker() != player) return "Сейчас не твой ход.";
                if (!game.canPass()) return "Сейчас нельзя сказать «бито».";
                game.pass();
                round++;
                return null;

            case Wire.Move.TAKE:
                if (game.defender() != player) return "Сейчас не твой ход.";
                if (!game.canTake()) return "Сейчас нельзя взять.";
                game.take();
                round++;
                return null;

            default:
                return "Непонятный ход.";
        }
    }
}

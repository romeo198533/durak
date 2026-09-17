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
 * Мест за столом сколько угодно — правила одни и те же. Глава стола ничем не
 * выделен: его место такое же, как чужое, и ходы его проходят ту же проверку.
 * Выделено только одно право — раздать заново, потому что колода лежит у него.
 */
public final class Table {

    private final Game game;

    /** Номер круга, с единицы. Круг кончается «бито» или взятием. */
    private int round = 1;

    /**
     * Последний принятый ход — тот, о котором узнают все.
     *
     * Запоминается здесь, а не выводится экраном из сравнения двух видов: по
     * виду «бито» и «беру» не отличить друг от друга, а угадывать, что было,
     * экран не должен. Кто ходил, видно и так, а вот чем — только отсюда.
     */
    private int lastWho = -1;
    private int lastKind = -1;
    private Card lastCard;

    /** Зерно раздачи: с ним партию можно повторить, а заново — раздать другую. */
    private final long seed;

    /**
     * Как звать каждого за столом.
     *
     * Имя каждый называет сам, и оно едет всем: без него чужую сдачу и чужой ход
     * пришлось бы объявлять по номеру места, а это за столом звучит как «игрок
     * два сдался». Пусто — так и скажут, по номеру.
     */
    private final String[] names;

    /**
     * @param deckSize 36 или 52 карты.
     * @param seed     зерно перемешивания: с ним партию можно повторить.
     * @param players  сколько мест за столом.
     */
    public Table(int deckSize, long seed, int players) {
        this.seed = seed;
        this.game = new Game(deckSize, seed, players);
        this.names = new String[game.players()];
    }

    /**
     * Как звать игрока за столом.
     *
     * Имя называет сам игрок — первым словом, ещё до партии. Стол его только
     * запоминает и раздаёт всем: сам он имён не выдумывает.
     */
    public void setName(int player, String name) {
        if (player >= 0 && player < names.length) names[player] = name;
    }

    public void start() {
        game.start();
        round = 1;
        lastWho = -1;
        lastKind = -1;
        lastCard = null;
    }

    public boolean isOver() {
        return game.isOver();
    }

    /** Сколько мест за столом. */
    public int seats() {
        return game.players();
    }

    /** Чья очередь ходить. -1 — партия кончена. */
    public int turn() {
        if (game.isOver()) return -1;
        return game.phase() == Game.PHASE_DEFEND ? game.defender() : game.mover();
    }

    /** Вид места: то, что уедет этому игроку и только ему. */
    public Seat seatFor(int player) {
        int seats = game.players();

        Seat seat = new Seat();
        seat.me = player;
        seat.seats = seats;
        seat.round = round;
        seat.trump = game.trump();
        seat.trumpCard = game.trumpCard();
        seat.deckCount = game.deckCount();
        seat.attacker = game.attacker();
        seat.defender = game.defender();
        seat.mover = game.mover();
        seat.phase = game.phase();
        seat.loser = game.loser();
        seat.draw = game.isDraw();

        // Последний ход — общий для всех: он и так лежит на столе открытым.
        seat.lastWho = lastWho;
        seat.lastKind = lastKind;
        seat.lastCard = lastCard;

        seat.names = names.clone();
        seat.handCounts = new int[seats];
        seat.done = new boolean[seats];
        for (int p = 0; p < seats; p++) {
            seat.handCounts[p] = game.handCount(p);
            seat.done[p] = game.isDone(p);
        }

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
        if (player < 0 || player >= game.players()) {
            return "За этим столом нет такого места.";
        }

        switch (move.kind) {
            case Wire.Move.HIT:
                if (move.card == null) return "Заход без карты.";
                if (game.phase() != Game.PHASE_ATTACK || game.mover() != player) {
                    return "Сейчас не твой ход.";
                }
                if (!game.attackOptions().contains(move.card)) return "Так зайти нельзя.";
                game.attack(move.card);
                break;

            case Wire.Move.BEAT:
                if (move.card == null) return "Отбой без карты.";
                if (game.phase() != Game.PHASE_DEFEND || game.defender() != player) {
                    return "Сейчас не твой ход.";
                }
                if (!game.defendOptions().contains(move.card)) return "Эта карта не бьёт.";
                game.defend(move.card);
                break;

            case Wire.Move.PASS:
                if (game.phase() != Game.PHASE_ATTACK || game.mover() != player) {
                    return "Сейчас не твой ход.";
                }
                if (!game.canPass()) return "Сейчас нельзя сказать «бито».";
                game.pass();
                round++;
                break;

            case Wire.Move.TAKE:
                if (game.defender() != player) return "Сейчас не твой ход.";
                if (!game.canTake()) return "Сейчас нельзя взять.";
                game.take();
                round++;
                break;

            case Wire.Move.SURRENDER:
                // Сдаются в любой ход и по своей воле: столу остаётся только
                // записать, кто вышел из игры, и объявить это всем.
                game.surrender(player);
                round++;
                break;

            case Wire.Move.RESTART:
                // Раздать заново может только хозяин стола: колода у него.
                if (player != 0) return "Раздать заново может только хозяин стола.";
                game.start();
                round = 1;
                lastWho = -1;
                lastKind = -1;
                lastCard = null;
                break;

            default:
                return "Непонятный ход.";
        }

        // Запоминается только принятый ход: отказанный за столом не был, и
        // рассказывать о нём игрокам нечего.
        lastWho = player;
        lastKind = move.kind;
        lastCard = move.card;
        return null;
    }

    /** Зерно этой партии: по нему видно, что стол тот самый. */
    public long seed() {
        return seed;
    }
}

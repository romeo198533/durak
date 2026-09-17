package ru.romeo198533.durak;

import java.util.ArrayList;
import java.util.List;

/**
 * Место за столом: всё, что знает и видит один игрок.
 *
 * Это и есть ответ на «каждому едет только то, что ему положено знать». Здесь
 * нет поля, куда можно было бы положить чужую руку, — не по уговору, а по
 * устройству: у чужих рук есть только число карт. Поэтому вид можно смело
 * отправлять по проводу: лишнего в нём не окажется, даже если ошибиться.
 *
 * Заодно это единственное, чем располагает клиент, чтобы решить, чем ходить.
 * Отсюда и методы «что мне можно» внизу: они считают по видимому. Те же
 * правила судит и глава стола, у которого лежит настоящая {@link Game};
 * его слово — последнее, а здесь только подсказка для экрана.
 */
public final class Seat {

    /** Пара на столе: чем зашли и чем отбились. Защита null — ещё не отбито. */
    public static final class Pair {

        public final Card attack;

        public final Card defend;

        public Pair(Card attack, Card defend) {
            this.attack = attack;
            this.defend = defend;
        }

        public boolean beaten() {
            return defend != null;
        }
    }

    /** Моё место за столом: 0, 1, … */
    public int me;

    /** Сколько всего мест за столом. */
    public int seats;

    /** Номер круга, с единицы. По нему видно, что круг сменился. */
    public int round;

    public int trump;

    /** Открытая козырная карта. null — колода разобрана, козырь остаётся. */
    public Card trumpCard;

    public int deckCount;

    /** Сколько карт у каждого: чужие руки — только числом. */
    public int[] handCounts = new int[0];

    public List<Pair> table = new ArrayList<>();

    /** Только своя рука и только она. */
    public List<Card> hand = new ArrayList<>();

    public int attacker;

    public int defender;

    /**
     * Чей ход в нападении: зайти, подкинуть или сказать «бито».
     *
     * В защите ходит защищающийся, и это поле ни на что не влияет. Оно нужно
     * затем, что подкидывать может не один: за столом на троих очередь
     * доходит до каждого, и экран обязан знать, до кого именно.
     */
    public int mover;

    /** Кто вышел: рука пуста и колода тоже. Вышедший за столом не игрок. */
    public boolean[] done = new boolean[0];

    /**
     * Как звать каждого за столом — по имени, а не по номеру места.
     *
     * Имя нужно затем, чтобы назвать вслух чужой ход и чужую сдачу: «Валера
     * сдался» вместо «игрок 2 сдался». Пусто — значит, имени не задали, и
     * звать будут по месту.
     */
    public String[] names = new String[0];

    public int phase;

    /** Кто остался дураком. -1 — партия идёт или ничья. */
    public int loser = -1;

    public boolean draw;

    /**
     * Последний ход за столом: кто, что и чем.
     *
     * Ход этот ничей не секрет — карта и так лежит на столе открытой, — поэтому
     * он и едет всем одинаковый. Нужен он экрану затем, чтобы назвать вслух
     * чужой ход: свой игрок и так помнит, а чужой иначе остался бы без слов,
     * ведь карту за него выкладывает не этот экран.
     */
    public int lastWho = -1;

    /** Род хода — из {@link Wire.Move}. -1 — ещё никто не ходил. */
    public int lastKind = -1;

    /** Какой картой ходили. null — ходили не картой: «бито» или «беру». */
    public Card lastCard;

    public boolean over() {
        return phase == Game.PHASE_OVER;
    }

    /** Ход мой, и я заходят или подкидываю. */
    public boolean myAttack() {
        return !over() && phase == Game.PHASE_ATTACK && mover == me;
    }

    /** Ход мой, и я отбиваюсь. */
    public boolean myDefend() {
        return !over() && phase == Game.PHASE_DEFEND && defender == me;
    }

    /** Чья очередь ходить. -1 — партия кончена. */
    public int turn() {
        if (over()) return -1;
        return phase == Game.PHASE_DEFEND ? defender : mover;
    }

    public int trump() {
        return trump;
    }

    /** Незакрытая карта на столе. В пошаговой модели она одна. */
    public Pair unbeaten() {
        for (Pair pair : table) {
            if (!pair.beaten()) return pair;
        }
        return null;
    }

    public int unbeatenCount() {
        int n = 0;
        for (Pair pair : table) {
            if (!pair.beaten()) n++;
        }
        return n;
    }

    // ----- что мне можно -----

    /**
     * Чем я могу зайти или подкинуть. Пусто — не мой ход или подкидывать нечем.
     *
     * Повторяет правила главы стола по одному видимому: своя рука, стол и
     * число карт у защищающегося — больше для этого не нужно.
     */
    public List<Card> attackOptions() {
        List<Card> out = new ArrayList<>();
        if (!myAttack()) return out;
        if (table.size() >= Game.MAX_TABLE) return out;

        // По пустому столу заходит только тот, с кого начался круг: подкинуть
        // первым нечего, подкидывают по тому, что уже лежит.
        if (table.isEmpty() && mover != attacker) return out;

        // Больше, чем защищающийся в силах отбить, не подкидывают.
        if (!table.isEmpty() && unbeatenCount() >= cardsAt(defender)) return out;

        for (Card card : hand) {
            if (table.isEmpty() || rankOnTable(card.rank)) out.add(card);
        }
        return out;
    }

    /**
     * Сколько карт у места.
     *
     * Спрашивается по номеру из вида, а вид приходит по проводу от другой
     * версии игры и разбирается нарочно терпимо: незнакомое поле пропускается,
     * а недостающее остаётся пустым. Места, которого в виде не оказалось, для
     * нас нет; ноль карт у него — ответ верный в том смысле, что заходить по
     * такому виду всё равно нечем. Без этой проверки номер места уронил бы
     * приложение прямо за столом.
     */
    private int cardsAt(int place) {
        return place < 0 || place >= handCounts.length ? 0 : handCounts[place];
    }

    /** Чем я могу отбить незакрытую карту. */
    public List<Card> defendOptions() {
        List<Card> out = new ArrayList<>();
        if (!myDefend()) return out;
        Pair open = unbeaten();
        if (open == null) return out;
        for (Card card : hand) {
            if (card.beats(open.attack, trump)) out.add(card);
        }
        return out;
    }

    /** Мне можно сказать «бито»: круг есть, и всё на столе закрыто. */
    public boolean canPass() {
        return myAttack() && !table.isEmpty() && unbeatenCount() == 0;
    }

    /** Вышел ли игрок: рука пуста и колода тоже. */
    public boolean done(int player) {
        return player >= 0 && player < done.length && done[player];
    }

    /** Мне можно взять: круг есть. Берут и когда есть чем биться. */
    public boolean canTake() {
        return myDefend() && !table.isEmpty();
    }

    private boolean rankOnTable(int rank) {
        for (Pair pair : table) {
            if (pair.attack.rank == rank) return true;
            if (pair.defend != null && pair.defend.rank == rank) return true;
        }
        return false;
    }
}

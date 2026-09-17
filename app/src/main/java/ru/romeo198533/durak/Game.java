package ru.romeo198533.durak;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Партия в дурака: колода, руки, стол и правила.
 *
 * Ни одной ссылки на Android — партию можно прогнать и проверить без
 * телефона. Всё, что видно и слышно, живёт в экранах; здесь только правила.
 *
 * Первая версия играет вдвоём: человек против программы. Число соперников
 * заложено в {@link #players}, чтобы не переписывать правила потом, но
 * проверено пока только на двоих.
 */
public final class Game {

    /** Ход атакующего: зайти или подкинуть. */
    public static final int PHASE_ATTACK = 0;

    /** Ход защищающегося: отбиться или взять. */
    public static final int PHASE_DEFEND = 1;

    /** Партия кончена. */
    public static final int PHASE_OVER = 2;

    public static final int HAND_SIZE = 6;

    /** Больше шести карт за круг не подкидывают. */
    public static final int MAX_TABLE = 6;

    /** Пара на столе: чем зашли и чем отбились. Защита null — ещё не отбито. */
    public static final class Slot {

        public final Card attack;

        public Card defend;

        Slot(Card attack) {
            this.attack = attack;
        }

        public boolean beaten() {
            return defend != null;
        }
    }

    private final int deckSize;
    private final Random random;

    private final List<Card> deck = new ArrayList<>();
    private final List<List<Card>> hands = new ArrayList<>();
    private final List<Slot> table = new ArrayList<>();

    private int trump = Card.SPADES;
    private int attacker = 0;
    private int defender = 1;
    private int phase = PHASE_ATTACK;

    /** Кто остался дураком. -1 — партия ещё идёт или ничья. */
    private int loser = -1;

    /** Сколько карт ушло в отбой. Нужно проверке: карты не исчезают. */
    private int discarded;

    /** Была ли ничья: оба вышли одновременно. */
    private boolean draw;

    /**
     * @param deckSize 36 или 52 карты.
     * @param seed     зерно перемешивания: одинаковое даёт одинаковую раздачу,
     *                 а это единственный способ повторить неудачную партию.
     */
    public Game(int deckSize, long seed) {
        this.deckSize = deckSize >= Cards.DECK_52 ? Cards.DECK_52 : Cards.DECK_36;
        this.random = new Random(seed);
    }

    /** Раздать и открыть козырь. */
    public void start() {
        deck.clear();
        table.clear();
        hands.clear();
        loser = -1;
        draw = false;
        discarded = 0;

        deck.addAll(Cards.newDeck(deckSize, random));

        hands.add(new ArrayList<Card>());
        hands.add(new ArrayList<Card>());
        for (int i = 0; i < HAND_SIZE; i++) {
            for (int p = 0; p < 2; p++) {
                hands.get(p).add(deck.remove(0));
            }
        }

        // Козырная карта лежит внизу колоды и достаётся последней — потому
        // и смотрим на неё, а не вынимаем.
        trump = deck.get(deck.size() - 1).suit;

        attacker = 0;
        defender = 1;
        phase = PHASE_ATTACK;
    }

    // ----- что видно -----

    public int trump() {
        return trump;
    }

    /** Открытая козырная карта. null — колода разобрана, но козырь остаётся. */
    public Card trumpCard() {
        return deck.isEmpty() ? null : deck.get(deck.size() - 1);
    }

    public int deckCount() {
        return deck.size();
    }

    /** Сколько карт ушло в отбой за партию. */
    public int discardCount() {
        return discarded;
    }

    public int phase() {
        return phase;
    }

    public int attacker() {
        return attacker;
    }

    public int defender() {
        return defender;
    }

    public boolean isOver() {
        return phase == PHASE_OVER;
    }

    public int loser() {
        return loser;
    }

    public boolean isDraw() {
        return draw;
    }

    public List<Slot> table() {
        return Collections.unmodifiableList(table);
    }

    public int handCount(int player) {
        return hands.get(player).size();
    }

    /** Рука как есть — в порядке хранения. Для правил, не для показа. */
    public List<Card> hand(int player) {
        return Collections.unmodifiableList(hands.get(player));
    }

    /**
     * Рука в том порядке, в каком её читают вслух: сначала обычные масти по
     * старшинству, козыри в конце — их видно сразу, и искать их не надо.
     */
    /**
     * Рука по порядку: от мелкого к крупному, слева направо.
     *
     * Сначала просто достоинство, а не масть: «шесть, семь, восемь» — так руку
     * и держат, и так её читают вслух. Козыри в конце: козырь бьёт любую
     * некозырную карту, значит он и старше всех — а ищут его отдельно, и с
     * конца списка он находится сразу. Масть решает только между равными.
     */
    public List<Card> handSorted(int player) {
        List<Card> out = new ArrayList<>(hands.get(player));
        Collections.sort(out, new Comparator<Card>() {
            @Override
            public int compare(Card a, Card b) {
                boolean ta = a.isTrump(trump);
                boolean tb = b.isTrump(trump);
                if (ta != tb) return ta ? 1 : -1;
                if (a.rank != b.rank) return Integer.compare(a.rank, b.rank);
                return Integer.compare(a.suit, b.suit);
            }
        });
        return out;
    }

    // ----- ходы -----

    /** Незакрытая карта на столе. В нашей пошаговой модели она одна. */
    public Slot unbeaten() {
        for (Slot slot : table) {
            if (!slot.beaten()) return slot;
        }
        return null;
    }

    public int unbeatenCount() {
        int n = 0;
        for (Slot slot : table) {
            if (!slot.beaten()) n++;
        }
        return n;
    }

    public boolean allBeaten() {
        return !table.isEmpty() && unbeatenCount() == 0;
    }

    /** Чем атакующий может зайти или подкинуть. Пусто — подкидывать нечем. */
    public List<Card> attackOptions() {
        List<Card> out = new ArrayList<>();
        if (phase != PHASE_ATTACK || isOver()) return out;
        if (table.size() >= MAX_TABLE) return out;

        // Нельзя подкинуть больше, чем защищающийся в силах отбить: иначе
        // круг заканчивался бы взятием карт, которых нечем крыть.
        if (!table.isEmpty() && unbeatenCount() >= hands.get(defender).size()) return out;

        for (Card card : hands.get(attacker)) {
            if (table.isEmpty() || rankOnTable(card.rank)) out.add(card);
        }
        return out;
    }

    /** Есть ли на столе карта этого достоинства — по ней и подкидывают. */
    private boolean rankOnTable(int rank) {
        for (Slot slot : table) {
            if (slot.attack.rank == rank) return true;
            if (slot.defend != null && slot.defend.rank == rank) return true;
        }
        return false;
    }

    /** Чем защищающийся может отбить незакрытую карту. */
    public List<Card> defendOptions() {
        List<Card> out = new ArrayList<>();
        if (phase != PHASE_DEFEND || isOver()) return out;
        Slot open = unbeaten();
        if (open == null) return out;
        for (Card card : hands.get(defender)) {
            if (card.beats(open.attack, trump)) out.add(card);
        }
        return out;
    }

    /** Можно ли сказать «бито»: круг есть, и всё на столе закрыто. */
    public boolean canPass() {
        return phase == PHASE_ATTACK && !isOver() && allBeaten();
    }

    /** Можно ли взять: круг есть. Берут и когда есть чем биться. */
    public boolean canTake() {
        return !isOver() && !table.isEmpty();
    }

    /** Зайти или подкинуть картой. */
    public boolean attack(Card card) {
        if (phase != PHASE_ATTACK || isOver()) return false;
        if (!hands.get(attacker).remove(card)) return false;
        table.add(new Slot(card));
        phase = PHASE_DEFEND;
        return true;
    }

    /** Отбиться картой. */
    public boolean defend(Card card) {
        if (phase != PHASE_DEFEND || isOver()) return false;
        Slot open = unbeaten();
        if (open == null) return false;
        if (!card.beats(open.attack, trump)) return false;
        if (!hands.get(defender).remove(card)) return false;
        open.defend = card;
        phase = PHASE_ATTACK;
        return true;
    }

    /** Сказать «бито». Карты уходят в отбой, ход переходит защищавшемуся. */
    public boolean pass() {
        if (!canPass()) return false;
        discarded += table.size() * 2 - unbeatenCount();
        table.clear();
        int wasDefender = defender;
        defender = attacker;
        attacker = wasDefender;
        endRound();
        return true;
    }

    /** Взять карты со стола. Ход остаётся у того же атакующего. */
    public boolean take() {
        if (!canTake()) return false;
        List<Card> taken = new ArrayList<>();
        for (Slot slot : table) {
            taken.add(slot.attack);
            if (slot.defend != null) taken.add(slot.defend);
        }
        hands.get(defender).addAll(taken);
        table.clear();
        endRound();
        return true;
    }

    /** Конец круга: добрать карты и посмотреть, не кончилась ли партия. */
    private void endRound() {
        // Круг всегда кончается ходом атакующего. Кто им стал — решено выше:
        // после «бито» роли поменялись, после взятия остались как были.
        phase = PHASE_ATTACK;

        refill(attacker);
        refill(defender);

        if (!deck.isEmpty()) return;

        boolean firstOut = hands.get(0).isEmpty();
        boolean secondOut = hands.get(1).isEmpty();
        if (!firstOut && !secondOut) return;

        phase = PHASE_OVER;
        if (firstOut && secondOut) {
            draw = true;
            loser = -1;
        } else {
            loser = firstOut ? 1 : 0;
        }
    }

    /** Добрать до шести. Первым берёт тот, кто ходил. */
    private void refill(int player) {
        List<Card> hand = hands.get(player);
        while (hand.size() < HAND_SIZE && !deck.isEmpty()) {
            hand.add(deck.remove(0));
        }
    }

    /** Правила этой партии словами — для экрана настроек и для памяти. */
    public int deckSize() {
        return deckSize;
    }
}

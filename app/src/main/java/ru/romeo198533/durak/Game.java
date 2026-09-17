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
 * За столом может сидеть любое число игроков, но правила у всех одни. Круг
 * ведёт один нападающий, отбивается один защищающийся, остальные подкидывают.
 * Двое — частный случай: подкидывать, кроме нападающего, некому, и партия
 * играется ровно так, как игралась до того, как мест стало больше.
 *
 * Место за столом — просто номер. Кто за ним сидит, движок не знает: это
 * забота экрана.
 */
public final class Game {

    /** Ход нападающего: зайти или подкинуть. */
    public static final int PHASE_ATTACK = 0;

    /** Ход защищающегося: отбиться или взять. */
    public static final int PHASE_DEFEND = 1;

    /** Партия кончена. */
    public static final int PHASE_OVER = 2;

    public static final int HAND_SIZE = 6;

    /** Больше шести карт за круг не подкидывают. */
    public static final int MAX_TABLE = 6;

    /** Меньше двух мест за столом не бывает. */
    public static final int MIN_PLAYERS = 2;

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
    private final int players;
    private final Random random;

    private final List<Card> deck = new ArrayList<>();
    private final List<List<Card>> hands = new ArrayList<>();
    private final List<Slot> table = new ArrayList<>();

    /** Кто вышел: рука пуста и добирать неоткуда. Вышедших за столом больше нет. */
    private final boolean[] done;

    private int trump = Card.SPADES;
    private int attacker = 0;
    private int defender = 1;

    /** Чей ход в нападении: зайти, подкинуть или сказать «бито». */
    private int mover = 0;

    /** Кто положил последнюю карту. С него и продолжается подкидывание. */
    private int addedBy = 0;

    /** Сколько подряд сказали «бито» с последней положенной карты. */
    private int passes;

    private int phase = PHASE_ATTACK;

    /** Кто остался дураком. -1 — партия ещё идёт или ничья. */
    private int loser = -1;

    /** Сколько карт ушло в отбой. Нужно проверке: карты не исчезают. */
    private int discarded;

    /** Была ли ничья: все вышли разом. */
    private boolean draw;

    /**
     * @param deckSize 36 или 52 карты.
     * @param seed     зерно перемешивания: одинаковое даёт одинаковую раздачу,
     *                 а это единственный способ повторить неудачную партию.
     */
    public Game(int deckSize, long seed) {
        this(deckSize, seed, MIN_PLAYERS);
    }

    /**
     * @param players сколько мест за столом.
     */
    public Game(int deckSize, long seed, int players) {
        this.deckSize = deckSize >= Cards.DECK_52 ? Cards.DECK_52 : Cards.DECK_36;
        this.players = Math.max(MIN_PLAYERS, players);
        this.random = new Random(seed);
        this.done = new boolean[this.players];
    }

    /** Раздать и открыть козырь. */
    public void start() {
        deck.clear();
        table.clear();
        hands.clear();
        for (int p = 0; p < players; p++) {
            hands.add(new ArrayList<Card>());
            done[p] = false;
        }
        loser = -1;
        draw = false;
        discarded = 0;
        passes = 0;

        deck.addAll(Cards.newDeck(deckSize, random));

        for (int i = 0; i < HAND_SIZE; i++) {
            for (int p = 0; p < players; p++) {
                hands.get(p).add(deck.remove(0));
            }
        }

        // Козырная карта лежит внизу колоды и достаётся последней — потому
        // и смотрим на неё, а не вынимаем.
        trump = deck.get(deck.size() - 1).suit;

        attacker = 0;
        defender = 1;
        mover = attacker;
        addedBy = attacker;
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

    /** Сколько мест за столом. */
    public int players() {
        return players;
    }

    public int attacker() {
        return attacker;
    }

    public int defender() {
        return defender;
    }

    /** Чей ход в нападении. В защите ходит защищающийся. */
    public int mover() {
        return mover;
    }

    /** Вышел ли игрок: рука пуста и колода тоже. */
    public boolean isDone(int player) {
        return done[player];
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

    /** Чем можно зайти или подкинуть. Пусто — подкидывать нечем. */
    public List<Card> attackOptions() {
        List<Card> out = new ArrayList<>();
        if (phase != PHASE_ATTACK || isOver()) return out;
        if (table.size() >= MAX_TABLE) return out;

        // Защищающемуся не подкидывают, и первым заходит только тот, с кого
        // начался круг: подкидывать по пустому столу нечего.
        if (mover == defender) return out;
        if (table.isEmpty() && mover != attacker) return out;

        // Нельзя подкинуть больше, чем защищающийся в силах отбить: иначе
        // круг заканчивался бы взятием карт, которых нечем крыть.
        if (!table.isEmpty() && unbeatenCount() >= hands.get(defender).size()) return out;

        for (Card card : hands.get(mover)) {
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

    /**
     * Можно ли взять: отбиваюсь, и круг есть. Берут и когда есть чем биться.
     *
     * Фаза спрашивается не для красоты. Отбой переводит партию в нападение,
     * не меняя ролей, — защищающийся остаётся собой, и без этой проверки
     * «Беру» проходило бы в чужой ход: игрок забирал бы карты, которые сам же
     * только что отбил. Ровно так же смотрит на это вид места
     * ({@link Seat#canTake()}), и разойтись им нельзя.
     */
    public boolean canTake() {
        return phase == PHASE_DEFEND && !isOver() && !table.isEmpty();
    }

    /** Зайти или подкинуть картой. */
    public boolean attack(Card card) {
        if (phase != PHASE_ATTACK || isOver()) return false;
        if (mover == defender) return false;
        if (table.isEmpty() && mover != attacker) return false;
        if (!hands.get(mover).remove(card)) return false;
        table.add(new Slot(card));
        addedBy = mover;
        passes = 0;
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
        // Подкидывать дальше начинает сосед того, кто ходил: круг идёт по
        // очереди, и очередь эта не должна перескакивать через игрока.
        mover = nextTo(addedBy);
        phase = PHASE_ATTACK;
        return true;
    }

    /**
     * Сказать «бито».
     *
     * Когда подкидывать отказались все, карты уходят в отбой, а круг кончается:
     * защищавшийся становится нападающим, а отбиваться садится следующий.
     */
    public boolean pass() {
        if (!canPass()) return false;
        passes++;
        if (passes >= freePlayers()) {
            beat();
            return true;
        }
        mover = nextTo(mover);
        return true;
    }

    /** Круг кончился впустую: карты в отбой, роли меняются. */
    private void beat() {
        discarded += table.size() * 2 - unbeatenCount();
        table.clear();
        attacker = defender;
        defender = nextAlive(defender);
        endRound();
    }

    /** Взять карты со стола. Нападать садится следующий за защищавшимся. */
    public boolean take() {
        if (!canTake()) return false;
        List<Card> taken = new ArrayList<>();
        for (Slot slot : table) {
            taken.add(slot.attack);
            if (slot.defend != null) taken.add(slot.defend);
        }
        hands.get(defender).addAll(taken);
        table.clear();
        attacker = nextAlive(defender);
        defender = nextAlive(attacker);
        endRound();
        return true;
    }

    /**
     * Сдаться.
     *
     * Сдаются в любой ход и без чужого согласия: партия кончается сразу, и
     * дураком остаётся тот, кто сдался. Карты при этом никуда не деваются —
     * партия брошена, а не доиграна, и разбирать её незачем.
     */
    public void surrender(int player) {
        if (isOver()) return;
        if (player < 0 || player >= players) return;
        phase = PHASE_OVER;
        draw = false;
        loser = player;
    }

    /** Конец круга: добрать карты и посмотреть, не кончилась ли партия. */
    private void endRound() {
        refillAll();

        // Колода разобрана — вышедшие выходят: их рука пуста, и добирать им
        // больше нечего. Вышедший за столом больше не игрок.
        if (deck.isEmpty()) {
            for (int p = 0; p < players; p++) {
                if (!done[p] && hands.get(p).isEmpty()) done[p] = true;
            }
        }

        int left = 0;
        int last = -1;
        for (int p = 0; p < players; p++) {
            if (!done[p]) {
                left++;
                last = p;
            }
        }

        if (left <= 1) {
            phase = PHASE_OVER;
            if (left == 0) {
                draw = true;
                loser = -1;
            } else {
                draw = false;
                loser = last;
            }
            return;
        }

        // Нападать и отбиваться должны те, кто ещё за столом: вышедший за
        // столом не игрок, и начинать ему круг нечем — карт у него нет.
        if (done[attacker]) attacker = nextAlive(attacker);
        if (done[defender] || defender == attacker) defender = nextAlive(attacker);
        startRound();
    }

    /** Новый круг: заходит нападающий, и заходит картой. */
    private void startRound() {
        phase = PHASE_ATTACK;
        passes = 0;
        mover = attacker;
        addedBy = attacker;
    }

    /**
     * Добрать до шести.
     *
     * Первым берёт тот, кто ходил, — ему и распоряжаться. Дальше по кругу, а
     * защищавшийся берёт последним: ему труднее всех, и колода достаётся ему
     * по остатку.
     */
    private void refillAll() {
        refill(attacker);
        int p = attacker;
        for (int i = 1; i < players; i++) {
            p = (p + 1) % players;
            if (p != defender) refill(p);
        }
        refill(defender);
    }

    private void refill(int player) {
        List<Card> hand = hands.get(player);
        while (hand.size() < HAND_SIZE && !deck.isEmpty()) {
            hand.add(deck.remove(0));
        }
    }

    // ----- кто за кем -----

    /**
     * Следующий за этим, мимо кого не подкидывают.
     *
     * Защищавшийся пропускается: он отбивается, а не подкидывает. Вышедшие
     * пропускаются тоже — их за столом уже нет.
     */
    private int nextTo(int from) {
        int p = from;
        for (int i = 0; i < players; i++) {
            p = (p + 1) % players;
            if (!done[p] && p != defender) return p;
        }
        return from;
    }

    /** Следующий за этим, кто ещё за столом. */
    private int nextAlive(int from) {
        int p = from;
        for (int i = 0; i < players; i++) {
            p = (p + 1) % players;
            if (!done[p]) return p;
        }
        return from;
    }

    /** Сколько игроков ещё могут подкинуть: все за столом, кроме защищавшегося. */
    private int freePlayers() {
        int n = 0;
        for (int p = 0; p < players; p++) {
            if (!done[p] && p != defender) n++;
        }
        return n;
    }

    /** Правила этой партии словами — для экрана настроек и для памяти. */
    public int deckSize() {
        return deckSize;
    }
}

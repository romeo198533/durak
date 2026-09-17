package ru.romeo198533.durak;

import java.util.ArrayList;
import java.util.List;

/**
 * Что едет по проводу: строки туда и обратно.
 *
 * Свой текстовый формат, а не готовый разбор вроде org.json: строку видно
 * глазами в журнале связи, и её можно собрать и разобрать без Android — значит,
 * протокол проверяется партией прямо здесь, без двух телефонов.
 *
 * Вид записи — «ключ=значение», разделённые точкой с запятой, первым идёт
 * род сообщения:
 *
 *   V;me=0;seats=2;round=3;trump=2;trumpCard=2-14;deck=18;hands=5,6;
 *     table=2-7:0-9,1-6:-;hand=0-6,3-13;attacker=1;defender=0;phase=0;loser=-1;draw=0;
 *     last=1:0:0-9
 *   M;do=hit;card=0-7
 *   R;ok=0;why=Сейчас не твой ход.
 *
 * Имена, а не порядок: поле можно добавить, не переставляя остальные, и в
 * журнале видно, что где. Карта — «масть-достоинство», список — через запятую,
 * пустое место — прочерк. Своя рука едет только тому, чей это вид: чужой руки
 * в самом {@link Seat} не бывает.
 */
public final class Wire {

    /** Ход одного игрока: зайти, отбиться, сказать «бито», взять, сдаться. */
    public static final class Move {

        public static final int HIT = 0;
        public static final int BEAT = 1;
        public static final int PASS = 2;
        public static final int TAKE = 3;

        /**
         * Сдаться можно в любой ход, а не только в свой.
         *
         * Поэтому это единственный ход, который стол обязан уметь прочитать
         * от любого места и в любой момент, — за него и пришлось завести
         * чтение со всех каналов разом, а не с одного, чей черёд.
         */
        public static final int SURRENDER = 4;

        /** Раздать заново. Ход хозяйский: колода лежит у того, кто открыл стол. */
        public static final int RESTART = 5;

        public final int kind;

        /** У «бито», «беру» и «сдаюсь» карты нет — null. */
        public final Card card;

        private Move(int kind, Card card) {
            this.kind = kind;
            this.card = card;
        }

        public static Move hit(Card card) {
            return new Move(HIT, card);
        }

        public static Move beat(Card card) {
            return new Move(BEAT, card);
        }

        public static Move pass() {
            return new Move(PASS, null);
        }

        public static Move take() {
            return new Move(TAKE, null);
        }

        public static Move surrender() {
            return new Move(SURRENDER, null);
        }

        public static Move restart() {
            return new Move(RESTART, null);
        }
    }

    private static final char FIELD = ';';
    private static final char EQUALS = '=';
    private static final String LIST = ",";
    private static final String PAIR = ":";

    /** Прочерк там, где ехать нечему: пустая рука, пустой стол, нет карты. */
    private static final String NONE = "-";

    private static final String VIEW = "V";
    private static final String MOVE = "M";
    private static final String REPLY = "R";

    /** Первое слово гостя: как его звать. */
    private static final String NAME = "N";

    private Wire() {
    }

    // ----- вид места -----

    public static String encode(Seat seat) {
        List<String> counts = new ArrayList<>();
        for (int count : seat.handCounts) counts.add(String.valueOf(count));

        List<String> pairs = new ArrayList<>();
        for (Seat.Pair pair : seat.table) {
            pairs.add(card(pair.attack) + PAIR + (pair.defend == null ? NONE : card(pair.defend)));
        }

        StringBuilder out = new StringBuilder();
        out.append(VIEW);
        field(out, "me", seat.me);
        field(out, "seats", seat.seats);
        field(out, "round", seat.round);
        field(out, "trump", seat.trump);
        field(out, "trumpCard", seat.trumpCard == null ? NONE : card(seat.trumpCard));
        field(out, "deck", seat.deckCount);
        field(out, "hands", join(counts));
        field(out, "table", join(pairs));
        field(out, "hand", join(cards(seat.hand)));
        field(out, "attacker", seat.attacker);
        field(out, "defender", seat.defender);
        field(out, "mover", seat.mover);
        field(out, "done", flags(seat.done));
        field(out, "names", join(names(seat)));
        field(out, "phase", seat.phase);
        field(out, "loser", seat.loser);
        field(out, "draw", seat.draw ? 1 : 0);
        field(out, "last", last(seat));
        return out.toString();
    }

    public static Seat decode(String line) {
        String[] fields = line.split(String.valueOf(FIELD), -1);
        if (fields.length == 0 || !VIEW.equals(fields[0])) {
            throw new IllegalArgumentException("не вид места: " + line);
        }

        Seat seat = new Seat();
        seat.seats = 0;
        for (int i = 1; i < fields.length; i++) {
            String[] pair = split(fields[i]);
            String key = pair[0];
            String value = pair[1];
            if ("me".equals(key)) seat.me = number(value, key);
            else if ("seats".equals(key)) seat.seats = number(value, key);
            else if ("round".equals(key)) seat.round = number(value, key);
            else if ("trump".equals(key)) seat.trump = number(value, key);
            else if ("trumpCard".equals(key)) seat.trumpCard = empty(value) ? null : card(value);
            else if ("deck".equals(key)) seat.deckCount = number(value, key);
            else if ("hands".equals(key)) seat.handCounts = counts(value);
            else if ("table".equals(key)) seat.table = pairs(value);
            else if ("hand".equals(key)) seat.hand = cards(value);
            else if ("attacker".equals(key)) seat.attacker = number(value, key);
            else if ("defender".equals(key)) seat.defender = number(value, key);
            else if ("mover".equals(key)) seat.mover = number(value, key);
            else if ("done".equals(key)) seat.done = flags(value);
            else if ("names".equals(key)) seat.names = names(value);
            else if ("phase".equals(key)) seat.phase = number(value, key);
            else if ("loser".equals(key)) seat.loser = number(value, key);
            else if ("draw".equals(key)) seat.draw = number(value, key) != 0;
            else if ("last".equals(key)) last(value, seat);
            // Незнакомое поле пропускаем молча: связь с более новой версией
            // не должна обрываться из-за одного лишнего слова.
        }
        return seat;
    }

    // ----- ход -----

    public static String encode(Move move) {
        StringBuilder out = new StringBuilder();
        out.append(MOVE);
        switch (move.kind) {
            case Move.HIT:
                field(out, "do", "hit");
                field(out, "card", card(move.card));
                break;
            case Move.BEAT:
                field(out, "do", "beat");
                field(out, "card", card(move.card));
                break;
            case Move.PASS:
                field(out, "do", "pass");
                break;
            case Move.TAKE:
                field(out, "do", "take");
                break;
            case Move.SURRENDER:
                field(out, "do", "surrender");
                break;
            case Move.RESTART:
                field(out, "do", "restart");
                break;
            default:
                throw new IllegalArgumentException("непонятный ход: " + move.kind);
        }
        return out.toString();
    }

    public static Move decodeMove(String line) {
        String[] fields = line.split(String.valueOf(FIELD), -1);
        if (fields.length == 0 || !MOVE.equals(fields[0])) {
            throw new IllegalArgumentException("не ход: " + line);
        }

        String what = null;
        Card card = null;
        for (int i = 1; i < fields.length; i++) {
            String[] pair = split(fields[i]);
            if ("do".equals(pair[0])) what = pair[1];
            else if ("card".equals(pair[0])) card = card(pair[1]);
        }

        if ("hit".equals(what)) return Move.hit(card);
        if ("beat".equals(what)) return Move.beat(card);
        if ("pass".equals(what)) return Move.pass();
        if ("take".equals(what)) return Move.take();
        if ("surrender".equals(what)) return Move.surrender();
        if ("restart".equals(what)) return Move.restart();
        throw new IllegalArgumentException("непонятный ход: " + line);
    }

    // ----- первое слово гостя -----

    /**
     * Как звать игрока — первым словом, ещё до партии.
     *
     * Гость называет себя, едва канал открылся, и на этом всё: ответа на это
     * слово не ждут, потому что это не ход, и отказывать в нём нечего. Стол
     * разложит имя по местам и разошлёт его всем вместе со следующим видом —
     * затем оно и нужно, чтобы назвать вслух чужую сдачу и чужой ход.
     */
    public static String encodeName(String name) {
        StringBuilder out = new StringBuilder();
        out.append(NAME);
        field(out, "name", safe(name));
        return out.toString();
    }

    /**
     * Разобрать первое слово.
     *
     * @return имя или null, если это не имя: ход и ответ разбираются не здесь.
     */
    public static String decodeName(String line) {
        if (line == null) return null;
        String[] fields = line.split(String.valueOf(FIELD), -1);
        if (fields.length == 0 || !NAME.equals(fields[0])) return null;
        for (int i = 1; i < fields.length; i++) {
            if (fields[i].indexOf(EQUALS) < 0) continue;
            String[] pair = split(fields[i]);
            if ("name".equals(pair[0])) return safe(pair[1]);
        }
        return "";
    }

    // ----- ответ главы стола -----

    /** Ответ на ход: null — принят, иначе — почему отказано. */
    public static String encodeReply(String reason) {
        StringBuilder out = new StringBuilder();
        out.append(REPLY);
        field(out, "ok", reason == null ? "1" : "0");
        if (reason != null) field(out, "why", reason);
        return out.toString();
    }

    /** Разбор ответа: null — ход принят, иначе причина отказа. */
    public static String decodeReply(String line) {
        String[] fields = line.split(String.valueOf(FIELD), -1);
        if (fields.length == 0 || !REPLY.equals(fields[0])) {
            throw new IllegalArgumentException("не ответ: " + line);
        }
        for (int i = 1; i < fields.length; i++) {
            String[] pair = split(fields[i]);
            if ("why".equals(pair[0])) return pair[1];
        }
        return null;
    }

    // ----- мелочи -----

    /**
     * Карта записью «масть-достоинство»: «2-14» — туз бубей.
     *
     * Числа, а не буквы: и то и другое одинаково непонятно человеку, зато числа
     * не зависят от того, как карта называется вслух, и переживут любую правку
     * озвучки.
     */
    public static String card(Card card) {
        if (card == null) return NONE;
        return card.suit + "-" + card.rank;
    }

    public static Card card(String value) {
        if (empty(value)) return null;
        int dash = value.indexOf('-');
        if (dash <= 0) throw new IllegalArgumentException("не карта: " + value);
        int suit = number(value.substring(0, dash), "масть");
        int rank = number(value.substring(dash + 1), "достоинство");
        return new Card(suit, rank);
    }

    /**
     * Имена за столом — по слову на место, как и всё остальное.
     *
     * Имя проходит через {@link #safe}: разделитель внутри имени развалил бы
     * строку на полуслове прямо посреди партии, а человеку его не видно.
     */
    private static List<String> names(Seat seat) {
        List<String> out = new ArrayList<>();
        for (String name : seat.names) out.add(safe(name));
        return out;
    }

    private static String[] names(String value) {
        if (empty(value)) return new String[0];
        String[] parts = value.split(LIST, -1);
        String[] out = new String[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = safe(parts[i]);
        return out;
    }

    /** Имя без разделителей строки: их в имени быть не может. */
    private static String safe(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && out.length() < 20; i++) {
            char symbol = value.charAt(i);
            boolean divider = symbol == FIELD || symbol == EQUALS || symbol == ','
                    || symbol == '\n' || symbol == '\r' || symbol == '\t';
            out.append(divider ? ' ' : symbol);
        }
        return out.toString().trim();
    }

    private static List<String> cards(List<Card> list) {
        List<String> out = new ArrayList<>();
        for (Card card : list) out.add(card(card));
        return out;
    }

    private static List<Card> cards(String value) {
        List<Card> out = new ArrayList<>();
        if (empty(value)) return out;
        for (String one : value.split(LIST, -1)) out.add(card(one));
        return out;
    }

    /**
     * Последний ход записью «кто:род:карта» — «1:0:2-7».
     *
     * Врозь от остальных пар, потому что карта сама пишется через прочерк
     * («масть-достоинство»), и разделителем ему быть нельзя.
     */
    private static String last(Seat seat) {
        if (seat.lastWho < 0 || seat.lastKind < 0) return NONE;
        return seat.lastWho + PAIR + seat.lastKind + PAIR + card(seat.lastCard);
    }

    private static void last(String value, Seat seat) {
        if (empty(value)) return;
        String[] parts = value.split(PAIR, -1);
        if (parts.length < 3) throw new IllegalArgumentException("не последний ход: " + value);
        seat.lastWho = number(parts[0], "кто ходил");
        seat.lastKind = number(parts[1], "род хода");
        seat.lastCard = card(parts[2]);
    }

    private static List<Seat.Pair> pairs(String value) {
        List<Seat.Pair> out = new ArrayList<>();
        if (empty(value)) return out;
        for (String one : value.split(LIST, -1)) {
            int colon = one.indexOf(PAIR);
            if (colon < 0) throw new IllegalArgumentException("не пара на столе: " + one);
            out.add(new Seat.Pair(card(one.substring(0, colon)), card(one.substring(colon + 1))));
        }
        return out;
    }

    /**
     * Кто вышел — по слову на место: «0,1,0».
     *
     * Номерами, а не списком вышедших, чтобы место в строке совпадало с местом
     * за столом: у кого рука пуста, видно по одной букве, и разбирать нечего.
     */
    private static String flags(boolean[] values) {
        List<String> out = new ArrayList<>();
        for (boolean value : values) out.add(value ? "1" : "0");
        return join(out);
    }

    private static boolean[] flags(String value) {
        if (empty(value)) return new boolean[0];
        String[] parts = value.split(LIST, -1);
        boolean[] out = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = number(parts[i], "вышел") != 0;
        return out;
    }

    private static int[] counts(String value) {
        if (empty(value)) return new int[0];
        String[] parts = value.split(LIST, -1);
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = number(parts[i], "число карт");
        return out;
    }

    private static boolean empty(String value) {
        return value.isEmpty() || NONE.equals(value);
    }

    private static String join(List<String> parts) {
        if (parts.isEmpty()) return NONE;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(LIST);
            out.append(parts.get(i));
        }
        return out.toString();
    }

    private static void field(StringBuilder out, String key, String value) {
        // Значение с разделителем внутри развалило бы разбор на глазах у
        // игрока. Пусть лучше падает здесь, где это ловится прогоном партии.
        if (value.indexOf(FIELD) >= 0 || value.indexOf(EQUALS) >= 0) {
            throw new IllegalArgumentException("в поле " + key + " есть разделитель: " + value);
        }
        out.append(FIELD).append(key).append(EQUALS).append(value);
    }

    private static void field(StringBuilder out, String key, int value) {
        field(out, key, String.valueOf(value));
    }

    private static String[] split(String field) {
        int equals = field.indexOf(EQUALS);
        if (equals < 0) throw new IllegalArgumentException("поле без значения: " + field);
        return new String[]{field.substring(0, equals), field.substring(equals + 1)};
    }

    private static int number(String value, String what) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("не число в поле " + what + ": " + value);
        }
    }
}

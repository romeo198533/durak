package ru.romeo198533.durak;

/**
 * Слова за столом: что сказать игроку.
 *
 * Собраны в одном месте не ради красоты, а чтобы одиночная игра и партия по
 * Блютусу говорили одно и то же. Экранов два, а стол один, и если каждый
 * сложит свои фразы сам, они разойдутся на первой же правке: за одним столом
 * скажут «соперник, семь пик», за другим промолчат.
 *
 * Здесь только слова — ни партии, ни связи. Решает, когда говорить, экран.
 */
public final class Words {

    /** Как зовётся тот, кто сидит напротив. За столом на двоих он один. */
    public static final String FOE = "Соперник";

    private Words() {
    }

    /**
     * Что человеку делать прямо сейчас.
     *
     * Подсказка называет только то, чего не слышно из карт: чей ход и какую
     * карту бить. Ни «бей или бери», ни «подкидывай или говори бито» здесь нет —
     * Валера знает правила, и это для него лишнее слово.
     *
     * @return подсказка или null, если ход не мой: говорить тогда нечего.
     */
    public static String turn(Seat seat, int me) {
        if (seat == null || seat.over() || seat.turn() != me) return null;

        if (seat.phase == Game.PHASE_DEFEND) {
            Seat.Pair open = seat.unbeaten();
            return open == null ? "Отбивайся." : "Отбивайся: " + open.attack.name() + ".";
        }
        return seat.table.isEmpty() ? "Твой ход." : "Подкидывай.";
    }

    /**
     * Как звать игрока за столом.
     *
     * Имя каждый называет сам, и оно едет всем вместе с видом. Не назвал —
     * зовём по месту: за столом на двоих «соперник», за столом побольше —
     * «игрок» и номер. Номер тот же, что и на экране, поэтому спутать некого.
     */
    public static String who(Seat seat, int player) {
        if (seat != null && player >= 0 && player < seat.names.length) {
            String name = seat.names[player];
            if (name != null && !name.trim().isEmpty()) return name.trim();
        }
        if (seat == null || seat.seats <= 2) return FOE;
        return "Игрок " + (player + 1);
    }

    /**
     * Что сказал последний ход за столом.
     *
     * Имя карты берётся из вида, а не угадывается сравнением двух видов: по
     * столу «бито» и «беру» на вид не отличить друг от друга. Кто ходил и чем —
     * то, что лежит на столе открытым, поэтому об этом и можно говорить вслух.
     *
     * @return сказанное или null, если говорить нечего: хода ещё не было или
     *         ходил я сам — свой ход игрок и так слышал.
     */
    public static String moved(Seat seat, int me) {
        if (seat == null || seat.lastWho < 0 || seat.lastWho == me) return null;

        switch (seat.lastKind) {
            case Wire.Move.TAKE:
                return who(seat, seat.lastWho) + " взял.";
            case Wire.Move.PASS:
                // Без имени: круг кончил он, и это уже слышно — карту перед
                // этим называли его голосом. Дальше идёт «твой ход», и вместе
                // выходит ровно «бито, твой ход».
                return "Бито.";
            default:
                if (seat.lastCard == null) return null;
                return who(seat, seat.lastWho) + ", " + seat.lastCard.name() + ".";
        }
    }

    /**
     * Конец партии словами: кто дурак и что теперь.
     *
     * Сдача называется отдельно: дураком остаётся тот, кто сдался, но сказано
     * будет именно «сдался». За столом это разные вещи — одно дело проиграть в
     * игре, другое выйти из неё самому, — и слышать их надо по-разному.
     */
    public static String result(Seat seat, int me) {
        if (seat.draw) return "Ничья: все вышли разом.";

        if (seat.lastKind == Wire.Move.SURRENDER) {
            if (seat.loser == me) return "Ты сдался. Партия кончена.";
            return who(seat, seat.loser) + " сдался. Ты выиграл.";
        }

        if (seat.loser == me) return "Ты дурак. Партия кончена.";
        return who(seat, seat.loser) + " дурак. Ты выиграл.";
    }

    /**
     * Почему ход не принят или не сделан.
     *
     * Живёт здесь по той же причине, что и остальное: отказ стола приходит
     * словами, и оба экрана должны сказать его одинаково.
     */
    public static String refusal(Seat seat, int me) {
        if (seat.phase == Game.PHASE_DEFEND && seat.defender == me) {
            Seat.Pair open = seat.unbeaten();
            return "Не отбиться. Бить "
                    + (open == null ? "нечего" : open.attack.name()) + ".";
        }
        return seat.table.isEmpty()
                ? "Этой картой сейчас нельзя."
                : "Подкидывать можно только по достоинству того, что на столе.";
    }
}

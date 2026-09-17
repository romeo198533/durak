package ru.romeo198533.durak;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Соперник-программа.
 *
 * Играет просто и предсказуемо: ходит младшим, бьёт дешёвым, козырь бережёт.
 * Хитростей здесь намеренно нет — первая версия нужна, чтобы проверить стол
 * и озвучку, а не чтобы выигрывать. Умнее станет, когда Валера сыграет и
 * скажет, где соперник ведёт себя глупо.
 *
 * Считает бот по одному только своему виду места — {@link Seat}, — и потому
 * годится не только в одиночную игру: за сетевым столом он такой же игрок, как
 * и человек, и играет по тем же правилам видимого. Отсюда две пары входов:
 * {@link Game} для одиночной партии и {@link Seat} для общей. Решение везде
 * принимается по списку доступных карт, а не по движку, поэтому оно одно на
 * оба случая.
 */
public final class Bot {

    private final int player;

    private final Random random;

    /** @param player за кого играет программа: 0 — человек, 1 — она. */
    public Bot(int player, long seed) {
        this.player = player;
        this.random = new Random(seed);
    }

    /**
     * Чем зайти или подкинуть.
     *
     * @return карта или null: подкидывать нечем либо невыгодно — тогда «бито».
     */
    public Card attack(Game game) {
        return pickAttack(game.attackOptions(), game.table().isEmpty(), game.trump());
    }

    /** То же по виду места: так бот играет за сетевым столом. */
    public Card attack(Seat seat) {
        return pickAttack(seat.attackOptions(), seat.table.isEmpty(), seat.trump);
    }

    /**
     * Чем отбиться.
     *
     * @return карта или null — берёт.
     */
    public Card defend(Game game) {
        Game.Slot open = game.unbeaten();
        return pickDefend(game.defendOptions(), open == null ? null : open.attack,
                game.trump(), game.hand(player));
    }

    /** То же по виду места. */
    public Card defend(Seat seat) {
        Seat.Pair open = seat.unbeaten();
        return pickDefend(seat.defendOptions(), open == null ? null : open.attack,
                seat.trump, seat.hand);
    }

    /**
     * Сказать ли «бито», когда защищался не он.
     *
     * Бот этим не пользуется, но метод нужен: ход атакующего и ход
     * защищающегося разведены, и вызывающий должен уметь спросить обоих.
     */
    public boolean pass(Game game) {
        return game.canPass() && game.attacker() == player;
    }

    // ----- решение -----

    private Card pickAttack(List<Card> options, boolean first, int trump) {
        if (options.isEmpty()) return null;
        List<Card> sorted = new ArrayList<>(options);
        Collections.sort(sorted, Card.BY_STRENGTH);

        // Некозырная карта дешевле: её и кладём. Первым заходом — самую
        // младшую, подкидыванием — тоже младшую из подходящих.
        for (Card card : sorted) {
            if (!card.isTrump(trump)) return card;
        }

        // Остались одни козыри. Первым заходом деваться некуда — ходим
        // младшим. Подкидывать козырь поверх своего же захода — себе дороже:
        // лучше сказать «бито».
        if (first) return sorted.get(0);
        return null;
    }

    private Card pickDefend(List<Card> options, Card attack, int trump, List<Card> hand) {
        if (options.isEmpty()) return null;
        List<Card> sorted = new ArrayList<>(options);
        Collections.sort(sorted, Card.BY_STRENGTH);

        // Самая дешёвая некозырная подойдёт всегда — её и тратим.
        Card cheapestTrump = null;
        for (Card card : sorted) {
            if (!card.isTrump(trump)) return card;
            if (cheapestTrump == null) cheapestTrump = card;
        }

        // Козырь поверх козыря — обычное дело, тут экономить нечего.
        if (attack != null && attack.isTrump(trump)) return cheapestTrump;

        // Козырь на некозырную карту — трата, но не всякая. Если козырей
        // в руке несколько, один не жалко; дорогой заход стоит убить; при
        // короткой руке терять круг нельзя вовсе.
        int trumps = 0;
        for (Card card : hand) {
            if (card.isTrump(trump)) trumps++;
        }
        if (trumps >= 3 || hand.size() <= 2 || (attack != null && attack.rank >= 12)) {
            return cheapestTrump;
        }

        return null;
    }
}

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
        List<Card> options = new ArrayList<>(game.attackOptions());
        if (options.isEmpty()) return null;
        Collections.sort(options, Card.BY_STRENGTH);

        boolean first = game.table().isEmpty();

        // Некозырная карта дешевле: её и кладём. Первым заходом — самую
        // младшую, подкидыванием — тоже младшую из подходящих.
        for (Card card : options) {
            if (!card.isTrump(game.trump())) return card;
        }

        // Остались одни козыри. Первым заходом деваться некуда — ходим
        // младшим. Подкидывать козырь поверх своего же захода — себе дороже:
        // лучше сказать «бито».
        if (first) return options.get(0);
        return null;
    }

    /**
     * Чем отбиться.
     *
     * @return карта или null — берёт.
     */
    public Card defend(Game game) {
        List<Card> options = new ArrayList<>(game.defendOptions());
        if (options.isEmpty()) return null;
        Collections.sort(options, Card.BY_STRENGTH);

        Game.Slot open = game.unbeaten();
        Card attack = open == null ? null : open.attack;

        // Самая дешёвая некозырная подойдёт всегда — её и тратим.
        Card cheapestTrump = null;
        for (Card card : options) {
            if (!card.isTrump(game.trump())) return card;
            if (cheapestTrump == null) cheapestTrump = card;
        }

        // Козырь поверх козыря — обычное дело, тут экономить нечего.
        if (attack != null && attack.isTrump(game.trump())) return cheapestTrump;

        // Козырь на некозырную карту — трата, но не всякая. Если козырей
        // в руке несколько, один не жалко; дорогой заход стоит убить; при
        // короткой руке терять круг нельзя вовсе.
        int trumps = 0;
        for (Card card : game.hand(player)) {
            if (card.isTrump(game.trump())) trumps++;
        }
        int hand = game.handCount(player);
        if (trumps >= 3 || hand <= 2 || (attack != null && attack.rank >= 12)) {
            return cheapestTrump;
        }

        return null;
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
}

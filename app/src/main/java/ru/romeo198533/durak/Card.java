package ru.romeo198533.durak;

import java.util.Comparator;

/**
 * Игральная карта: масть и достоинство.
 *
 * Ничего, кроме самих чисел: правила игры живут в {@link Game}, а названия
 * для озвучки — в {@link Cards}. Так движок можно проверять без телефона.
 */
public final class Card {

    public static final int SPADES = 0;
    public static final int CLUBS = 1;
    public static final int DIAMONDS = 2;
    public static final int HEARTS = 3;

    /** Достоинство: 6..14, где 11 — валет, 12 — дама, 13 — король, 14 — туз. */
    public final int rank;

    public final int suit;

    public Card(int suit, int rank) {
        this.suit = suit;
        this.rank = rank;
    }

    /**
     * Бьёт ли эта карта карту other при козыре trump.
     *
     * Своя масть берёт старшинством, чужая — только козырем. Козырь поверх
     * козыря решается тем же старшинством, поэтому проверка масти идёт первой.
     */
    public boolean beats(Card other, int trump) {
        if (other == null) return false;
        if (suit == other.suit) return rank > other.rank;
        return suit == trump;
    }

    public boolean isTrump(int trump) {
        return suit == trump;
    }

    /** Название для озвучки: «семь пик», «туз бубей». */
    public String name() {
        return Cards.rankName(rank) + " " + Cards.suitGenitive(suit);
    }

    /** Короткая запись для отладки и заметок: «7п». */
    public String shortName() {
        return Cards.rankShort(rank) + Cards.suitShort(suit);
    }

    @Override
    public String toString() {
        return shortName();
    }

    /** От младшей к старшей: чем раньше в списке, тем дешевле карта. */
    public static final Comparator<Card> BY_STRENGTH = new Comparator<Card>() {
        @Override
        public int compare(Card a, Card b) {
            if (a.rank != b.rank) return Integer.compare(a.rank, b.rank);
            return Integer.compare(a.suit, b.suit);
        }
    };

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Card)) return false;
        Card card = (Card) other;
        return card.suit == suit && card.rank == rank;
    }

    @Override
    public int hashCode() {
        return suit * 31 + rank;
    }
}

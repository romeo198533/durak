package ru.romeo198533.durak;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Колода и русские названия карт.
 *
 * Названия собраны здесь, а не в {@link Card}, потому что это дело озвучки,
 * а не правил: движок сравнивает числа, а говорит пусть словами.
 */
public final class Cards {

    public static final int DECK_36 = 36;
    public static final int DECK_52 = 52;

    private Cards() {
    }

    /** Колода нужного размера, перемешанная. 36 — от шестёрки, 52 — от двойки. */
    public static List<Card> newDeck(int size, Random random) {
        int lowest = size >= DECK_52 ? 2 : 6;
        List<Card> deck = new ArrayList<>();
        for (int suit = Card.SPADES; suit <= Card.HEARTS; suit++) {
            for (int rank = lowest; rank <= 14; rank++) {
                deck.add(new Card(suit, rank));
            }
        }
        Collections.shuffle(deck, random);
        return deck;
    }

    public static String rankName(int rank) {
        switch (rank) {
            case 2: return "два";
            case 3: return "три";
            case 4: return "четыре";
            case 5: return "пять";
            case 6: return "шесть";
            case 7: return "семь";
            case 8: return "восемь";
            case 9: return "девять";
            case 10: return "десять";
            case 11: return "валет";
            case 12: return "дама";
            case 13: return "король";
            case 14: return "туз";
            default: return "карта " + rank;
        }
    }

    public static String rankShort(int rank) {
        switch (rank) {
            case 11: return "В";
            case 12: return "Д";
            case 13: return "К";
            case 14: return "Т";
            default: return String.valueOf(rank);
        }
    }

    /**
     * Знак масти — то, что написано на карте.
     *
     * Масть на карте показывается знаком, а достоинство словом: «король» и
     * трефа читаются слабовидящим сразу, а «К» и «т» приходится разгадывать.
     * Вслух при этом называется слово: слепому знак не нужен, ему говорят
     * «король треф».
     */
    public static String suitSymbol(int suit) {
        switch (suit) {
            case Card.SPADES: return "♠";
            case Card.CLUBS: return "♣";
            case Card.DIAMONDS: return "♦";
            case Card.HEARTS: return "♥";
            default: return "";
        }
    }

    public static String suitShort(int suit) {
        switch (suit) {
            case Card.SPADES: return "п";
            case Card.CLUBS: return "т";
            case Card.DIAMONDS: return "б";
            case Card.HEARTS: return "ч";
            default: return "?";
        }
    }

    /** Масть как её называют при карте: «туз бубей», «семь пик». */
    public static String suitGenitive(int suit) {
        switch (suit) {
            case Card.SPADES: return "пик";
            case Card.CLUBS: return "треф";
            case Card.DIAMONDS: return "бубей";
            case Card.HEARTS: return "червей";
            default: return "";
        }
    }

    /** Масть сама по себе: «пики», «трефы». Нужна для фразы «козырь — бубны». */
    public static String suitName(int suit) {
        switch (suit) {
            case Card.SPADES: return "пики";
            case Card.CLUBS: return "трефы";
            case Card.DIAMONDS: return "бубны";
            case Card.HEARTS: return "черви";
            default: return "";
        }
    }

    /** Красная ли масть — пригодится, когда цвета будут настраиваться. */
    public static boolean isRed(int suit) {
        return suit == Card.DIAMONDS || suit == Card.HEARTS;
    }

    /**
     * Перечисление карт словами: «семь пик, туз бубей и король треф».
     *
     * Список читает синтезатор, поэтому без запятых на слух не обойтись:
     * «семь пик туз бубей» слилось бы в одно слово.
     */
    public static String list(List<Card> cards) {
        if (cards.isEmpty()) return "пусто";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) out.append(i == cards.size() - 1 ? " и " : ", ");
            out.append(cards.get(i).name());
        }
        return out.toString();
    }

    /** Сколько карт — словами, с правильным склонением: «шесть карт». */
    public static String count(int n) {
        return n + " " + Ru.plural(n, "карта", "карты", "карт");
    }
}

package ru.romeo198533.durak;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Всё, что читает вслух синтезатор, собирается здесь.
 *
 * Взято из «Говорящего таймера для кухни»: склонения и дата нужны те же,
 * а писать их заново — верный способ получить «5 карта».
 */
public final class Ru {

    private Ru() {
    }

    private static final String[] MONTHS_GEN = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    /** Русское склонение после числа: 1 карта, 2 карты, 5 карт. */
    public static String plural(int n, String one, String few, String many) {
        int n10 = n % 10;
        int n100 = n % 100;
        if (n10 == 1 && n100 != 11) return one;
        if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) return few;
        return many;
    }

    /**
     * Полная дата для главного экрана: «четверг, 17 сентября 2026».
     *
     * День недели и месяц берутся у системы на русском, а не выписаны
     * таблицей: так они сами следят за календарём.
     */
    public static String fullDate(LocalDateTime now) {
        String weekday = now.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("ru"));
        return weekday + ", " + now.getDayOfMonth() + " "
                + MONTHS_GEN[now.getMonthValue() - 1] + " " + now.getYear();
    }

    /** Время для главного экрана: «21:34». */
    public static String time(LocalDateTime now) {
        return String.format("%02d:%02d", now.getHour(), now.getMinute());
    }
}

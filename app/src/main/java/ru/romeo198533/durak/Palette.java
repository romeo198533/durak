package ru.romeo198533.durak;

/**
 * Цвета для слабовидящих.
 *
 * Выбираются не оттенками, а названиями: «жёлтый», «зелёный». Слепому
 * владельцу название — единственное, что от цвета остаётся, а продиктовать
 * настройку словами можно только тогда, когда у каждого цвета есть имя.
 */
public final class Palette {

    public static final String[] NAMES = {
            "белый", "чёрный", "жёлтый", "зелёный",
            "синий", "красный", "серый", "оранжевый"};

    private static final int[] COLORS = {
            0xFFFFFFFF, 0xFF000000, 0xFFFFEB3B, 0xFF00C853,
            0xFF2962FF, 0xFFD50000, 0xFF9E9E9E, 0xFFFF6D00};

    private Palette() {
    }

    public static int color(int index) {
        return COLORS[clamp(index)];
    }

    public static String name(int index) {
        return NAMES[clamp(index)];
    }

    public static int indexOf(int color) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == color) return i;
        }
        return 0;
    }

    /**
     * Видно ли буквы на фоне.
     *
     * Тот, кто видит, заметил бы плохую пару сразу, а вслепую её легко
     * выставить и потом не понять, почему карты пропали. Считаем по яркости:
     * тёмное на тёмном и светлое на светлом — не видно.
     */
    public static boolean readable(int foreground, int background) {
        return Math.abs(brightness(foreground) - brightness(background)) >= 90;
    }

    private static int brightness(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static int clamp(int index) {
        if (index < 0) return 0;
        if (index >= COLORS.length) return COLORS.length - 1;
        return index;
    }
}

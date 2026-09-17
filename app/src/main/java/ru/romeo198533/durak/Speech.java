package ru.romeo198533.durak;

/**
 * Настройки голоса: скорость, тон и громкость синтезатора.
 *
 * Значения хранятся целыми процентами от обычного: сто — так, как задумал
 * движок. Проценты хороши тем, что читаются вслух как есть («быстрее обычной на
 * двадцать процентов»), и тем, что шаг в десять не копит дробную ошибку.
 *
 * Шаг фиксированный, а границы жёсткие: у предела кнопка просто перестаёт менять
 * значение. Так владелец всегда слышит, где он находится, и одним долгим
 * нажатием не улетает ни в неслышимый шёпот, ни в тарабарщину.
 */
public final class Speech {

    /** Шаг кнопок «быстрее — медленнее» и им подобных. */
    public static final int STEP = 10;

    public static final int SPEED_MIN = 50;
    public static final int SPEED_MAX = 200;
    public static final int SPEED_DEFAULT = 100;

    public static final int PITCH_MIN = 50;
    public static final int PITCH_MAX = 200;
    public static final int PITCH_DEFAULT = 100;

    /** Тише десяти процентов голос уже не разобрать — это граница, а не ноль. */
    public static final int VOLUME_MIN = 10;
    public static final int VOLUME_MAX = 100;
    public static final int VOLUME_DEFAULT = 100;

    private Speech() { }

    public static int speed(int value) {
        return align(value, SPEED_MIN, SPEED_MAX);
    }

    public static int pitch(int value) {
        return align(value, PITCH_MIN, PITCH_MAX);
    }

    public static int volume(int value) {
        return align(value, VOLUME_MIN, VOLUME_MAX);
    }

    public static int speedUp(int value) {
        return bump(speed(value), STEP, SPEED_MIN, SPEED_MAX);
    }

    public static int speedDown(int value) {
        return bump(speed(value), -STEP, SPEED_MIN, SPEED_MAX);
    }

    public static int pitchUp(int value) {
        return bump(pitch(value), STEP, PITCH_MIN, PITCH_MAX);
    }

    public static int pitchDown(int value) {
        return bump(pitch(value), -STEP, PITCH_MIN, PITCH_MAX);
    }

    public static int volumeUp(int value) {
        return bump(volume(value), STEP, VOLUME_MIN, VOLUME_MAX);
    }

    public static int volumeDown(int value) {
        return bump(volume(value), -STEP, VOLUME_MIN, VOLUME_MAX);
    }

    /** Проценты в то, что понимает синтезатор: сто процентов — единица. */
    public static float factor(int value) {
        return value / 100f;
    }

    public static String speedName(int value) {
        int v = speed(value);
        if (v == SPEED_DEFAULT) return "обычная скорость";
        if (v >= SPEED_MAX) return "самая быстрая: вдвое быстрее обычной";
        if (v <= SPEED_MIN) return "самая медленная: вдвое медленнее обычной";
        return (v > SPEED_DEFAULT ? "быстрее обычной на " : "медленнее обычной на ")
                + percent(Math.abs(v - SPEED_DEFAULT));
    }

    public static String pitchName(int value) {
        int v = pitch(value);
        if (v == PITCH_DEFAULT) return "обычный тон";
        if (v >= PITCH_MAX) return "самый высокий тон";
        if (v <= PITCH_MIN) return "самый низкий тон";
        return (v > PITCH_DEFAULT ? "выше обычного на " : "ниже обычного на ")
                + percent(Math.abs(v - PITCH_DEFAULT));
    }

    public static String volumeName(int value) {
        int v = volume(value);
        if (v >= VOLUME_MAX) return "полная громкость";
        if (v <= VOLUME_MIN) return "самая тихая: десять процентов";
        return "громкость " + percent(v);
    }

    private static String percent(int n) {
        return n + " " + Ru.plural(n, "процент", "процента", "процентов");
    }

    /** Кратно шагу и не выходит за границы: хранимое могло остаться от старой версии. */
    private static int align(int value, int min, int max) {
        if (value <= min) return min;
        if (value >= max) return max;
        int steps = Math.round((value - min) / (float) STEP);
        int aligned = min + steps * STEP;
        return aligned > max ? max : aligned;
    }

    private static int bump(int value, int delta, int min, int max) {
        int out = value + delta;
        if (out < min) out = min;
        if (out > max) out = max;
        return out;
    }
}

package ru.romeo198533.durak;

import android.app.Activity;
import android.view.View;

/** Мелкие удобства интерфейса, общие для всех экранов. */
public final class Ui {

    private Ui() { }

    public static int dp(Activity a, int value) {
        return Math.round(value * a.getResources().getDisplayMetrics().density);
    }

    /** Сказать вслух через TalkBack — для того, у чего нет своего вида на экране. */
    public static void say(Activity a, String text) {
        View root = a.getWindow().getDecorView();
        root.announceForAccessibility(text);
    }
}

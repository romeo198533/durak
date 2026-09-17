package ru.romeo198533.durak;

import android.app.Activity;
import android.app.AlertDialog;

/**
 * Выбор из списка.
 *
 * Списком, а не рядом кнопок: слепой рукой выбирают из перечисления, где каждый
 * пункт диктор читает целиком и говорит, который из них отмечен. Ряд кнопок на
 * экране ничем не показывает, что это выбор одного из многих.
 */
public final class Ask {

    /** Что делать с выбранным пунктом. */
    public interface Pick {
        void picked(int index);
    }

    private Ask() {
    }

    public static void choose(Activity activity, String title, String[] names,
                              int checked, Pick pick) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setSingleChoiceItems(names, checked, (list, which) -> {
                    list.dismiss();
                    pick.picked(which);
                })
                .setNegativeButton("Отмена", null)
                .create();
        dialog.show();
    }

    /** Да или нет — с вопросом, на который хочется ответить словами. */
    public static void confirm(Activity activity, String title, String yes, String no,
                               Runnable agreed) {
        confirm(activity, title, yes, no, agreed, null);
    }

    public static void confirm(Activity activity, String title, String yes, String no,
                               Runnable agreed, Runnable declined) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setPositiveButton(yes, (dialog, which) -> agreed.run())
                .setNegativeButton(no, declined == null
                        ? null
                        : (dialog, which) -> declined.run())
                .create()
                .show();
    }

    /** Сказать, что сделать нельзя, и почему. */
    public static void explain(Activity activity, String message) {
        new AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton("Понятно", null)
                .create()
                .show();
    }
}

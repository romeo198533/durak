package ru.romeo198533.durak;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Кто игру сделал и как с ним связаться.
 *
 * Адрес живёт в одном месте: он и на экране инструкции, и на кнопке в
 * настройках, и в словах о том, куда написать, если что-то молчит. Разойтись
 * они не могут, потому что взять его больше неоткуда — а разошедшийся адрес
 * означал бы человека, который написал в пустоту и не дождался ответа.
 */
public final class About {

    /** Как звать в переписке: этим и называют себя, и это же пишут в письме. */
    public static final String HANDLE = "@Romeodvortsov";

    /** Тот же адрес, но открываемый: по нему уходит кнопка в настройках. */
    public static final String URL = "https://t.me/Romeodvortsov";

    /**
     * Номер сборки.
     *
     * Нужен затем, что по нему сразу видно, та ли версия стоит на телефоне:
     * обновление приходит файлом, и «у меня ничего не изменилось» чаще всего
     * значит, что новый файл не встал.
     */
    public static String version(Context context) {
        try {
            String name = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (PackageManager.NameNotFoundException missing) {
            return "";
        }
    }

    private About() {
    }
}

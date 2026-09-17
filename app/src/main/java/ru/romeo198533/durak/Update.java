package ru.romeo198533.durak;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Обновление по воздуху через GitHub.
 *
 * Пункт «Обновление» в настройках: спрашивает у GitHub последний выпуск, сверяет
 * его с тем, что стоит на телефоне, и, если вышел новый, скачивает и передаёт
 * системе на установку. Обновление встаёт поверх старого только тогда, когда
 * подписано тем же ключом, — поэтому сборки подписываются одним ключом, а не
 * тем, который Android придумывает каждой сборке заново.
 *
 * Ничего не делается молча: каждый шаг — «проверяю», «есть новая версия»,
 * «скачиваю», «не вышло». Слепой владелец не видит ни полоски загрузки, ни
 * мелькнувшего экрана установки, и единственный способ ему сообщить — сказать.
 */
public final class Update {

    private static final String LATEST =
            "https://api.github.com/repos/romeo198533/durak/releases/latest";

    /** Сколько ждать загрузку, прежде чем признать, что она не идёт. */
    private static final long WATCH_MS = 1000L;
    private static final int WATCH_TRIES = 180;

    /** Опрос загрузки идёт с главного потока: экраны трогают только оттуда. */
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private Update() {
    }

    public static void check(Activity activity) {
        Voice voice = new Voice(activity, true);
        voice.say("Проверяю обновление.");

        new Thread(() -> {
            String problem = null;
            String version = null;
            String apk = null;
            String local = localVersion(activity);
            try {
                JSONObject release = new JSONObject(fetch(LATEST));
                version = trimmed(release.optString("tag_name"));
                apk = findApk(release.optJSONArray("assets"));
            } catch (Exception failed) {
                problem = explain(failed);
            }

            final String fVersion = version;
            final String fApk = apk;
            final String fProblem = problem;
            final String fLocal = local;

            activity.runOnUiThread(() -> {
                if (fProblem != null) {
                    voice.say("Не удалось проверить обновление. " + fProblem);
                    return;
                }
                if (fApk == null) {
                    voice.say("В последнем выпуске нет файла игры. "
                            + "Обновление придётся поставить вручную.");
                    return;
                }
                if (!isNewer(fVersion, fLocal)) {
                    voice.say("Обновлений нет. У тебя последняя версия, " + fLocal + ".");
                    return;
                }
                voice.say("Есть новая версия " + fVersion + ". У тебя " + fLocal + ".");
                Ask.confirm(activity, "Есть новая версия " + fVersion
                                + ". У тебя " + fLocal + ". Скачать?",
                        "Скачать", "Потом", () -> download(activity, voice, fVersion, fApk));
            });
        }, "durak-update").start();
    }

    // ----- выпуск на GitHub -----

    private static String fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("GitHub ответил " + code);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            reader.close();
            return out.toString();
        } finally {
            connection.disconnect();
        }
    }

    /** Файл игры среди вложений выпуска. */
    private static String findApk(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name");
            if (name.endsWith(".apk")) return asset.optString("browser_download_url");
        }
        return null;
    }

    /** «v1.2» и «1.2» — одно и то же. */
    private static String trimmed(String tag) {
        String out = tag == null ? "" : tag.trim();
        return out.startsWith("v") || out.startsWith("V") ? out.substring(1) : out;
    }

    /** Выше ли выпуск того, что стоит на телефоне. Сравниваем по частям: 1.10 > 1.9. */
    static boolean isNewer(String remote, String local) {
        String[] a = remote.split("\\.");
        String[] b = local.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int left = i < a.length ? number(a[i]) : 0;
            int right = i < b.length ? number(b[i]) : 0;
            if (left != right) return left > right;
        }
        return false;
    }

    private static int number(String part) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < part.length(); i++) {
            if (Character.isDigit(part.charAt(i))) digits.append(part.charAt(i));
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException tooBig) {
            return Integer.MAX_VALUE;
        }
    }

    private static String localVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "1.0" : info.versionName;
        } catch (Exception missing) {
            return "1.0";
        }
    }

    private static String explain(Exception failed) {
        String message = failed.getMessage();
        if (message == null || message.isEmpty()) {
            return "Связи с GitHub нет.";
        }
        return message + ".";
    }

    // ----- загрузка и установка -----

    private static void download(Activity activity, Voice voice, String version, String url) {
        DownloadManager manager =
                (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            voice.say("Системная загрузка недоступна.");
            return;
        }

        String name = "durak-" + version + ".apk";
        long id;
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("Дурак " + version);
            request.setDescription("Новая версия игры");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            id = manager.enqueue(request);
        } catch (Exception failed) {
            voice.say("Не удалось начать загрузку. " + explain(failed));
            return;
        }

        voice.say("Скачиваю версию " + version + ". Как скачается, спрошу про установку.");
        watch(activity, voice, manager, id, 0);
    }

    /** Ждём загрузку опросом: так не нужен приёмник широковещания и его регистрация. */
    private static void watch(Activity activity, Voice voice, DownloadManager manager,
                              long id, int attempt) {
        if (attempt >= WATCH_TRIES || activity.isFinishing() || activity.isDestroyed()) return;

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        int status = DownloadManager.STATUS_RUNNING;
        android.database.Cursor cursor = null;
        try {
            cursor = manager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            }
        } catch (Exception broken) {
            status = DownloadManager.STATUS_FAILED;
        } finally {
            if (cursor != null) cursor.close();
        }

        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            voice.say("Скачано. Сейчас откроется установка. Разреши установку из этого источника.");
            install(activity, manager, id, voice);
            return;
        }
        if (status == DownloadManager.STATUS_FAILED) {
            voice.say("Загрузка не удалась. Попробуй позже.");
            return;
        }

        handler.postDelayed(() -> watch(activity, voice, manager, id, attempt + 1), WATCH_MS);
    }

    private static void install(Activity activity, DownloadManager manager, long id, Voice voice) {
        try {
            Uri file = manager.getUriForDownloadedFile(id);
            if (file == null) {
                voice.say("Файл обновления не найден. Открой папку Загрузки вручную.");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(file, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (Exception failed) {
            voice.say("Не удалось открыть установку. " + explain(failed)
                    + " Файл лежит в папке Загрузки.");
        }
    }
}

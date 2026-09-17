package ru.romeo198533.durak;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Настройки: четыре двери и два правила.
 *
 * Список короткий намеренно. Голос с проговариванием ушли под одну кнопку
 * «Голос и речь», цвета и размеры карт — под «Вид стола»: наверху висело
 * десять пунктов подряд, и до правил приходилось листать через весь экран.
 * За дверью те же настройки, что были, — просто на один шаг дальше.
 *
 * Каждый пункт — кнопка, на которой написан текущий ответ. Так экран читается
 * сверху вниз одним движением: не нужно заходить в каждый пункт, чтобы узнать,
 * что там стоит. За дверями так же: там на каждой кнопке видно выбранное.
 */
public class SettingsActivity extends Activity {

    private static final String[] PASSING_NAMES = {"Подкидной", "Переводной"};
    private static final String[] DECK_NAMES = {"36 карт", "52 карты"};

    /** Те же слова, что и внутри «Голоса и речи»: дверь называет то, что за ней. */
    private static final String[] SPEAK_NAMES = {"проговаривать всё", "только по касанию"};

    private Button speechButton;
    private Button lookButton;
    private Button foesButton;
    private Button passingButton;
    private Button deckButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Настройки");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Skin.column(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 24));

        speechButton = item(root, () -> startActivity(new Intent(this, SpeechActivity.class)));
        lookButton = item(root, () -> startActivity(new Intent(this, LookActivity.class)));
        foesButton = item(root, this::pickFoes);
        passingButton = item(root, this::pickPassing);
        deckButton = item(root, this::pickDeck);
        item(root, this::checkUpdate).setText("Обновление");

        scroll.addView(root, Skin.wide());
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    /**
     * Пункт настроек: кнопка во всю ширину.
     *
     * Кнопки, а не строки списка: до строки списка слепой палец доходит хуже, а
     * крупная кнопка отзывается и на промах рядом с собой.
     */
    private Button item(LinearLayout root, Runnable action) {
        Button out = Skin.button(this, "", 17);
        out.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = Skin.wide();
        params.setMargins(0, Skin.dp(this, 3), 0, Skin.dp(this, 3));
        root.addView(out, params);
        return out;
    }

    private void show() {
        // У дверей вместо значения — что за ними. Голос и цвет по названию не
        // угадать, а заглядывать в каждую, чтобы вспомнить, где что, — то же
        // листание, от которого эти двери и спасают.
        label(speechButton, "Голос и речь",
                Prefs.engineLabel(this) + ", " + Speech.speedName(Prefs.speed(this))
                        + ", " + SPEAK_NAMES[Prefs.speakAll(this) ? 0 : 1]);
        label(lookButton, "Вид стола", "цвета карт и стола, размер карт");

        String foes = Prefs.FOES_NAMES[Prefs.foes(this) - 1];
        label(foesButton, "Соперников", foes);

        label(passingButton, "Дурак", PASSING_NAMES[Prefs.passing(this) ? 1 : 0]);

        label(deckButton, "Колода",
                Prefs.deck(this) == Cards.DECK_52 ? DECK_NAMES[1] : DECK_NAMES[0]);
    }

    private void label(Button button, String name, String value) {
        button.setText(name + ": " + value);
        button.setContentDescription(name + ": " + value + ". Нажми, чтобы открыть");
    }

    // ----- правила -----

    private void pickFoes() {
        Ask.choose(this, "Сколько соперников за столом", Prefs.FOES_NAMES,
                Prefs.foes(this) - 1, which -> {
                    Prefs.setFoes(this, which + 1);
                    show();
                    Ui.say(this, "Соперников: " + Prefs.FOES_NAMES[which]);
                });
    }

    private void pickPassing() {
        Ask.choose(this, "Вид дурака", PASSING_NAMES, Prefs.passing(this) ? 1 : 0, which -> {
            if (which == 1 && Prefs.foes(this) < 2) {
                Ask.explain(this, "Переводной дурак — игра на троих и больше, "
                        + "а за столом пока один соперник. Как только соперников "
                        + "можно будет выбрать, переводной заработает.");
                return;
            }
            Prefs.setPassing(this, which == 1);
            show();
            Ui.say(this, "Дурак: " + PASSING_NAMES[which]);
        });
    }

    private void pickDeck() {
        Ask.choose(this, "Сколько карт в колоде", DECK_NAMES,
                Prefs.deck(this) == Cards.DECK_52 ? 1 : 0, which -> {
                    Prefs.setDeck(this, which == 1 ? Cards.DECK_52 : Cards.DECK_36);
                    show();
                    Ui.say(this, "Колода: " + DECK_NAMES[which]);
                });
    }

    private void checkUpdate() {
        Update.check(this);
    }
}

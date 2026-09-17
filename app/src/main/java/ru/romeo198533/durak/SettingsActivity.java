package ru.romeo198533.durak;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Настройки: двери в голос и вид стола, имя и правила.
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
    private Button nameButton;
    private Button foesButton;
    private Button passingButton;
    private Button deckButton;
    private Button helpButton;
    private Button contactButton;

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
        nameButton = item(root, this::pickName);
        foesButton = item(root, this::pickFoes);
        passingButton = item(root, this::pickPassing);
        deckButton = item(root, this::pickDeck);
        item(root, this::checkUpdate).setText("Обновление");

        // Инструкция и переписка стоят последними: за ними ходят реже всего, а
        // первым делом настройки открывают за голосом и видом стола.
        helpButton = item(root, () -> startActivity(new Intent(this, HelpActivity.class)));
        contactButton = item(root, this::writeToDeveloper);

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

        label(nameButton, "Как меня зовут", nameWords());

        String foes = Prefs.FOES_NAMES[Prefs.foes(this) - 1];
        label(foesButton, "Соперников", foes);

        label(passingButton, "Дурак", PASSING_NAMES[Prefs.passing(this) ? 1 : 0]);

        label(deckButton, "Колода",
                Prefs.deck(this) == Cards.DECK_52 ? DECK_NAMES[1] : DECK_NAMES[0]);

        label(helpButton, "Инструкция", "как играть и что делать, если молчит");
        labelOut(contactButton, "Написать разработчику", About.HANDLE,
                "Открывает переписку в телеграме");
    }

    /**
     * Переписка с разработчиком — телеграм.
     *
     * Адрес показывается и вслух, и на экране: телеграма в телефоне может не
     * быть вовсе, и тогда открывать нечего, а адрес всё равно нужен — по нему
     * напишут с другого устройства.
     */
    private void writeToDeveloper() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(About.URL)));
            // Сказано после, а не до: обещать «открываю», а потом не открыть —
            // хуже, чем промолчать. Не выйдет — объяснение скажет адрес.
            Ui.say(this, "Открываю переписку в телеграме.");
        } catch (RuntimeException missing) {
            // Телеграм не поставили или он не отозвался — говорим адрес словами.
            Ask.explain(this, "Телеграм не открылся. Наш адрес: " + About.HANDLE + ".");
        }
    }

    /**
     * Надпись на двери.
     *
     * «Нажми, чтобы открыть» — не пустая вежливость: за дверью бывает и чужое
     * приложение, и тогда сказано, что именно откроется. Иначе слепой игрок
     * слышит «открыть», а открывается телеграм — и непонятно, то ли это, чего
     * он хотел.
     */
    private void label(Button button, String name, String value) {
        button.setText(name + ": " + value);
        button.setContentDescription(name + ": " + value + ". Нажми, чтобы открыть");
    }

    /** То же, но для двери, за которой чужое приложение. */
    private void labelOut(Button button, String name, String value, String what) {
        button.setText(name + ": " + value);
        button.setContentDescription(name + ": " + value + ". " + what);
    }

    // ----- имя -----

    /** Имя или, если его не задали, чем его заменят за столом. */
    private String nameWords() {
        String name = Prefs.name(this);
        return name.isEmpty() ? "без имени, зовут по месту" : name;
    }

    /**
     * Как меня зовут.
     *
     * Имя нужно затем, чтобы за столом с живыми людьми назвать вслух того, кто
     * сдался, и того, чей ход: «Валера, семь пик» вместо «игрок два». Пишется
     * руками — а имя, которое человек себе выбрал, он и без диктора наберёт.
     *
     * Разделители провода из имени выкидываются при сохранении
     * ({@link Prefs#clean}): запятая или точка с запятой развалили бы строку
     * вида на полуслове, и партия бы сломалась на ровном месте.
     */
    private void pickName() {
        final EditText input = new EditText(this);
        input.setText(Prefs.name(this));
        input.setSelectAllOnFocus(true);
        input.setContentDescription("Имя за столом");

        new AlertDialog.Builder(this)
                .setTitle("Как меня зовут")
                .setView(input)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    Prefs.setName(this, input.getText().toString());
                    show();
                    Ui.say(this, "Как меня зовут: " + nameWords());
                })
                .setNegativeButton("Отмена", null)
                .create()
                .show();
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
            if (which == 1) {
                // Тот же ответ, что и перед раздачей: выбор, который ничего не
                // меняет, обещать нельзя.
                Ask.explain(this, "Переводной дурак ещё не сделан: партия идёт "
                        + "подкидная. Перевод меняет весь круг, и его сделаю "
                        + "отдельным шагом.");
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

package ru.romeo198533.durak;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * Перед раздачей: каким дураком играем.
 *
 * Правила — это настройки, а не жёсткий свод: за столом их меняют перед каждой
 * раздачей, и выбранное здесь остаётся до следующего раза. Три кнопки — три
 * вопроса, и на каждой видно текущий ответ, чтобы не приходилось спрашивать
 * заново.
 *
 * «Раздать» отдельной кнопкой внизу: выбор правил — это ещё не игра, и
 * случайное касание не должно начинать партию.
 *
 * Рядом с ней «Играть вместе»: правила у партии по проводу те же, и выбранное
 * здесь уедет на стол точно так же — разница только в том, кто сядет напротив:
 * программа или живой человек с другого телефона.
 */
public class SetupActivity extends Activity {

    private static final String[] PASSING_NAMES = {"Подкидной", "Переводной"};
    private static final String[] DECK_NAMES = {"36 карт", "52 карты"};

    private Button passing;
    private Button deck;
    private Button foes;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Начать игру");

        LinearLayout root = Skin.column(this);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 24));

        passing = Skin.button(this, "", 18);
        passing.setOnClickListener(view -> pickPassing());
        root.addView(passing, spaced());

        deck = Skin.button(this, "", 18);
        deck.setOnClickListener(view -> pickDeck());
        root.addView(deck, spaced());

        foes = Skin.button(this, "", 18);
        foes.setOnClickListener(view -> pickFoes());
        root.addView(foes, spaced());

        root.addView(Skin.spacer(this, 1f));

        Button deal = Skin.button(this, "Раздать", "Раздать карты и начать партию", 24);
        deal.setOnClickListener(view -> deal());
        root.addView(deal, spaced());

        // Игра по проводу стоит отдельной дверью, а не галочкой в правилах: это
        // не правило, а другой стол — за ним сидят живые люди, и колоду держит
        // телефон того, кто стол открыл.
        Button net = Skin.button(this, "Играть вместе", "Играть вместе с другого "
                + "телефона: создать стол или подключиться к чужому", 22);
        net.setOnClickListener(view -> startActivity(new Intent(this, NetActivity.class)));
        root.addView(net, spaced());

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams out = Skin.wide();
        out.setMargins(0, Skin.dp(this, 6), 0, Skin.dp(this, 6));
        return out;
    }

    private void show() {
        String pass = PASSING_NAMES[Prefs.passing(this) ? 1 : 0];
        passing.setText("Дурак: " + pass);
        passing.setContentDescription("Вид дурака: " + pass + ". Нажми, чтобы сменить");

        String cards = Prefs.deck(this) == Cards.DECK_52 ? DECK_NAMES[1] : DECK_NAMES[0];
        deck.setText("Колода: " + cards);
        deck.setContentDescription("Колода: " + cards + ". Нажми, чтобы сменить");

        String rival = Prefs.FOES_NAMES[Prefs.foes(this) - 1];
        foes.setText("Соперников: " + rival);
        foes.setContentDescription("Соперников за столом: " + rival
                + ". Нажми, чтобы сменить");
    }

    private void pickPassing() {
        Ask.choose(this, "Вид дурака", PASSING_NAMES, Prefs.passing(this) ? 1 : 0, which -> {
            if (which == 1) {
                // Выбор, который ничего не меняет, обещать нельзя: переводной
                // пока не играется, и сказать об этом надо прямо, а не молчать
                // до первой партии.
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

    private void pickFoes() {
        Ask.choose(this, "Сколько соперников за столом", Prefs.FOES_NAMES,
                Prefs.foes(this) - 1, which -> {
                    Prefs.setFoes(this, which + 1);
                    show();
                    Ui.say(this, "Соперников: " + Prefs.FOES_NAMES[which]);
                });
    }

    private void deal() {
        startActivity(new Intent(this, GameActivity.class));
    }
}

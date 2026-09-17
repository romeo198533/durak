package ru.romeo198533.durak;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDateTime;

/**
 * Главный экран: дата с часами сверху, две крупные двери внизу.
 *
 * Дата с часами занимает всю полосу до кнопок, а не прижимается к верхнему краю:
 * пустого места между ней и кнопками не остаётся, и попасть по надписи слепой
 * рукой легче — промахнуться мимо неё попросту некуда.
 *
 * Слева «Начать игру», справа «Настройки» — как в остальных приложениях на
 * этом телефоне, чтобы рука искала кнопку там же, где привыкла. Кнопки здесь
 * крупнее обычных: на главном экране спешат, и промахнуться по ним нельзя.
 *
 * Часы тикают, но вслух сами не говорят: время слышно по касанию, а говорящие
 * сами по себе часы перебивали бы игру.
 */
public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView clock;

    /** Дверь обратно за стол: показывается, только когда партия идёт. */
    private Button back;

    /** Перерисовка часов: раз в секунду, чтобы минута менялась вовремя. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            showClock();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = Skin.column(this);
        root.setPadding(Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 8), Skin.dp(this, 24));

        clock = Skin.text(this, "", 34);
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(Skin.dp(this, 8), Skin.dp(this, 8),
                Skin.dp(this, 8), Skin.dp(this, 8));
        // Вес, а не высота по содержимому: дата тянется до самых кнопок, и
        // пустой полосы под ней не остаётся.
        root.addView(clock, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Дверь обратно за стол. Партия по проводу живёт отдельно от экрана и
        // переживает уход с него — а дороги назад иначе нет: уведомление в
        // шторке показывается не на всяком телефоне, а без него игрок, ушедший
        // в другое окно и вернувшийся через значок игры, остался бы от стола
        // отрезанным. Видна она только тогда, когда партия правда идёт.
        back = Skin.button(this, "Вернуться за стол", "Вернуться к партии по "
                + "проводу, которая идёт прямо сейчас", 22);
        back.setOnClickListener(view -> startActivity(new Intent(this, NetGameActivity.class)));
        back.setVisibility(View.GONE);
        root.addView(back, Skin.wide());

        LinearLayout buttons = Skin.row(this);

        Button start = Skin.button(this, "Начать игру", 22);
        start.setContentDescription("Начать игру. Открывает выбор правил и раздачу");
        start.setOnClickListener(view -> open(SetupActivity.class));
        buttons.addView(start, tall());

        Button settings = Skin.button(this, "Настройки", 22);
        settings.setContentDescription("Настройки: голос, проговаривание, "
                + "размер карт, цвета, имя, правила, инструкция и обновление");
        settings.setOnClickListener(view -> open(SettingsActivity.class));
        buttons.addView(settings, tall());

        root.addView(buttons, Skin.wide());

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Партию могли закрыть, пока экран спал, — тогда двери обратно быть не
        // должно: кнопка, ведущая в пустоту, хуже её отсутствия.
        back.setVisibility(NetGame.live() == null ? View.GONE : View.VISIBLE);

        showClock();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 1000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    /** Кнопки главного экрана выше обычных: под них и вес, и высота. */
    private LinearLayout.LayoutParams tall() {
        LinearLayout.LayoutParams out = Skin.weight(1f);
        out.height = Skin.dp(this, Skin.BIG_DP);
        out.setMargins(Skin.dp(this, 4), 0, Skin.dp(this, 4), 0);
        return out;
    }

    /**
     * Показать время.
     *
     * Надпись ставится только тогда, когда она правда другая: диктор читает
     * вслух то, что меняется под пальцем, а часы стоят во всю верхнюю полосу, и
     * палец на них попадает легко. Тикают они раз в секунду, а меняются раз в
     * минуту — переставлять надпись шестьдесят раз в минуту значило бы читать
     * время вслух посреди хода соперника. Описания у часов нет: диктор читает
     * написанное, а написано ровно то, что нужно услышать.
     */
    private void showClock() {
        LocalDateTime now = LocalDateTime.now();
        String text = Ru.fullDate(now) + ", " + Ru.time(now);
        if (!text.equals(clock.getText().toString())) clock.setText(text);
    }

    private void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
    }
}

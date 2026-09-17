package ru.romeo198533.durak

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Главное окно. Пока только заготовка: она нужна, чтобы проверить,
 * что сборка на GitHub вообще выдаёт рабочий файл. Игра появится позже.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.textStatus)
        status.text = getString(R.string.hello)
    }
}

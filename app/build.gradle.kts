plugins {
    id("com.android.application")
}

// Проект на Java, без Kotlin и без AndroidX: так делается таймер, с которого
// взят голос, и лишние движущиеся части здесь ни к чему. Экраны — обычные
// Activity, разметка — обычные TextView и Button: их скринридер читает
// из коробки, а это в игре для незрячих главное.
android {
    namespace = "ru.romeo198533.durak"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.romeo198533.durak"
        // 26 — потому что голос берёт VibrationEffect и java.time, а они
        // появились именно там. Ниже пришлось бы писать обходы ради
        // телефонов, которых почти не осталось.
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подпись здесь не задаётся намеренно. Ключ подписи лежит в
            // контейнере, а не в этом репозитории: репозиторий публичный,
            // и выложенный в него ключ позволил бы любому подписать
            // «обновление», которое встанет поверх нашей игры. Сборка
            // отдаёт неподписанный файл, а подпись ставится снаружи.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

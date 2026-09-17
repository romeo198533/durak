package ru.romeo198533.durak;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Вай-фай: то, чем один телефон находит другой в одной сети.
 *
 * Здесь только радио — партия отсюда не видна, как и в {@link Radio}. Разница
 * одна: Блютус сам возит имена и сам показывает, кто рядом, а вай-фай умеет
 * только соединить два адреса. Поэтому рядом встаёт вторая половина — системный
 * поиск служб ({@link NsdManager}): один телефон объявляет про свой стол, второй
 * просит систему показать столы, и игра получает готовый {@link Link}.
 *
 * Адрес руками не вводят намеренно: набрать голосом «192.168.1.42» — работа, а
 * слепому игроку к тому же надо сперва узнать этот адрес от кого-то. Стол
 * называется именем телефона и находится сам.
 *
 * Разрешений новых не требуется. В манифесте уже объявлен интернет, а его
 * хватает и на сокет, и на системный поиск служб: NsdManager просит ровно то же
 * самое. Разрешение на «устройства рядом» тут ни при чём — его спрашивают
 * прямой вай-фай (Wi-Fi Direct) и Aware, а не поиск служб. Значит, ни одного
 * нового окна с разрешением игра не покажет: для слепого игрока это и есть
 * разница между «нажал и играешь» и «разбери голосом три диалога».
 *
 * Оговорка на будущее, не про сегодняшний день. С Android 17 система закрывает
 * приложению локальную сеть и спрашивает на неё отдельное разрешение. Игру это
 * не заденет, пока она метит в 34-ю версию, но когда цель поднимут, у неё
 * появится ещё одно окно — а его слепому игроку придётся разбирать голосом.
 *
 * Плата за это одна: оба телефона должны быть на одном вай-фае, и роутер не
 * должен запрещать им видеть друг друга (в настройках это называется «изоляция
 * клиентов»). Если стол не находится, дело обычно в этом.
 */
public final class Lan {

    /**
     * Признак игры в системном списке служб.
     *
     * Имя службы одинаково у всех и зашито в приложение: по нему один телефон
     * узнаёт стол другого. Точка в конце обязательна — так устроены имена служб
     * в mDNS, и без неё система имя не примет.
     */
    public static final String SERVICE_TYPE = "_durak._tcp.";

    /**
     * Сколько ждать чужой стол, прежде чем признать, что его нет.
     *
     * Система по умолчанию ждёт куда дольше: для неё «медленно» — это когда
     * сетевая карта ещё не ответила. Игроку столько ждать незачем: он сидит
     * рядом, и если стол не ответил за восемь секунд, его уже закрыли.
     */
    private static final int CONNECT_TIMEOUT_MS = 8000;

    private Lan() {
    }

    /**
     * Имя этого телефона — им же называется стол.
     *
     * Берём то, которым телефон зовётся в системе: владелец уже слышал его в
     * настройках, и в списке найденных столов ему не придётся выяснять, где чей.
     */
    public static String deviceName() {
        String model = Build.MODEL;
        return model == null || model.isEmpty() ? "Телефон" : model;
    }

    /**
     * Открыть стол: занять порт и объявить о себе в сети.
     *
     * Звать можно с любого потока: порт занимается сразу, а объявление идёт
     * своим ходом, и ответ системы придёт в {@code ready} — в главный поток
     * приложения. Поэтому сказать «стол открыт, жду игрока» сразу после возврата
     * из этого метода нельзя: пока система не подтвердила, стол есть только на
     * своём телефоне, и второй его не найдёт.
     *
     * Порт берётся не числом, а у системы: она сама выдаёт свободный. Свой номер
     * зашивать нельзя — на чужом телефоне он может быть занят, и стол бы просто
     * не открылся.
     *
     * @param name  как назвать стол; это имя увидят в списке найденных.
     * @param ready куда сказать, что стол виден, — или что не виден.
     * @throws IOException порт занять не удалось.
     */
    public static Waiting open(Context context, String name, Ready ready) throws IOException {
        ServerSocket server = new ServerSocket(0);
        try {
            return new Waiting(context, server, name, ready);
        } catch (RuntimeException failed) {
            // Порт занят, а стол не открылся — отпускаем порт сразу, иначе он
            // остался бы занят игрой до конца её работы.
            closeQuietly(server);
            throw failed;
        }
    }

    /**
     * Подключиться к найденному столу.
     *
     * Звать из своего потока: здесь ждут соединения. Хозяин стола в это время
     * ждёт в {@link Waiting#accept()} — ждать надо вдвоём, иначе никто никого не
     * дождётся.
     *
     * @throws IOException стол уже закрыт, телефон не отвечает или сеть
     *                     разошлась.
     */
    public static Link connect(Discovered table) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(table.address, table.port), CONNECT_TIMEOUT_MS);
            return new WifiLink(socket, table.name());
        } catch (IOException failed) {
            // Неудачное соединение оставляет сокет открытым, а он держит силы
            // телефона до конца партии. Закрываем сразу.
            closeQuietly(socket);
            throw failed;
        }
    }

    /**
     * Начать поиск чужих столов.
     *
     * Поиск не ждёт: метод возвращается сразу, а находки приходят в
     * {@code finder} по мере того, как система их находит. Готового списка на
     * выходе нет и быть не может — он пополняется всё время, пока идёт поиск;
     * кому нужен список, берёт его у {@link Discovery#tables()}.
     *
     * Звать можно с любого потока. Второй поиск на этом же телефоне система не
     * заводит: сперва надо остановить первый.
     */
    public static Discovery discover(Context context, Finder finder) {
        NsdManager manager = (NsdManager) context.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE);
        Discovery discovery = new Discovery(manager, finder);
        discovery.start();
        return discovery;
    }

    /**
     * Адрес, который назвала система.
     *
     * У стола бывает не один адрес: вай-фай живёт и на старом протоколе, и на
     * новом. Берём старый (IPv4) — домашние сети стоят на нём, а новый адрес
     * бывает только местным, и по нему сосед отвечает не всегда.
     *
     * Список адресов система отдаёт начиная с Android 14; на более старых
     * телефонах адрес один, и берётся он напрямую.
     */
    private static InetAddress address(NsdServiceInfo service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            List<InetAddress> all = service.getHostAddresses();
            if (all != null) {
                for (InetAddress one : all) {
                    if (one instanceof Inet4Address) return one;
                }
                if (!all.isEmpty()) return all.get(0);
            }
        }
        return service.getHost();
    }

    /**
     * Кто на том конце подключился — по адресу.
     *
     * Имени гость не называет: игра именами не обменивается, а спросить имя
     * первым делом — это правка в самом разговоре партии, а не в радио. Адрес
     * слушать приятного мало, зато он честный: экран по нему скажет, кто именно
     * отвалился, когда гостей станет больше одного.
     */
    private static String peer(Socket socket) {
        InetAddress host = socket.getInetAddress();
        return host == null ? "гость" : host.getHostAddress();
    }

    /** Закрыть что угодно молча: закрывать — не то место, где стоит падать. */
    private static void closeQuietly(Closeable thing) {
        try {
            thing.close();
        } catch (IOException ignored) {
            // уже закрыто — тем лучше
        }
    }

    /**
     * Куда сказать, что стол объявлен.
     *
     * Объявление — дело системы и идёт своим ходом, поэтому у открытия стола
     * два ответа, и оба приходят в главный поток приложения: виден или не
     * виден. Раньше ответа говорить «стол открыт» нельзя — вышло бы обещание,
     * которого второй телефон не увидит.
     */
    public interface Ready {

        /** Стол виден в сети. Имя — то, под которым его найдут другие. */
        void ready(String name);

        /** Стола в сети не будет: искать его на других телефонах нечего. */
        void failed(String why);
    }

    /**
     * Куда приходят находки.
     *
     * Все три зовутся в главный поток приложения — там же, где живут экраны,
     * поэтому трогать из них экран можно прямо, не оглядываясь на потоки.
     */
    public interface Finder {

        /** Нашёлся стол: адрес уже известен, можно подключаться. */
        void table(Discovered table);

        /** Стол пропал из виду — из списка его пора убрать. */
        void gone(String name);

        /** Поиск не пошёл вовсе: сети нет, служба занята чужой игрой. */
        void failed(String why);
    }

    /**
     * Найденный стол: имя, адрес и порт.
     *
     * Имя — то, чем назвался хозяин (по умолчанию имя его телефона); по нему
     * стол и узнают в списке. Адрес с портом нужны, чтобы подключиться:
     * показывать их игроку незачем, они для машины.
     */
    public static final class Discovered {

        private final String name;
        private final InetAddress address;
        private final int port;

        Discovered(String name, InetAddress address, int port) {
            this.name = name;
            this.address = address;
            this.port = port;
        }

        /** Как стол называется — это и слышит игрок. */
        public String name() {
            return name;
        }

        /** Адрес телефона в сети — для беды вроде «не отвечает». */
        public String host() {
            return address.getHostAddress();
        }

        /** Номер порта на том телефоне. */
        public int port() {
            return port;
        }

        /** Тот же стол? Решается адресом и портом: имя у столов бывает одно. */
        private boolean same(Discovered other) {
            return port == other.port && address.equals(other.address);
        }
    }

    /**
     * Открытый стол: порт занят, объявление отправлено, ждём гостей.
     *
     * Гостей бывает и несколько: за столом иногда трое. Пока играют вдвоём,
     * хозяин зовёт {@link #accept()}, а когда мест станет больше —
     * {@link #accept(int)}. Число гостей задаётся снаружи, а не зашито здесь:
     * зашитое пришлось бы править в двух местах, и рано или поздно они
     * разошлись бы.
     */
    public static final class Waiting {

        private final ServerSocket server;
        private final NsdManager manager;
        private final NsdManager.RegistrationListener registration;

        private volatile String name;
        private boolean closed;
        /** Про стол уже сказали — второй раз говорить нечего. */
        private boolean announced;

        Waiting(Context context, ServerSocket server, String name, Ready ready) {
            this.server = server;
            this.name = name;
            this.manager = (NsdManager) context.getApplicationContext()
                    .getSystemService(Context.NSD_SERVICE);

            if (manager == null) {
                this.registration = null;
                if (ready != null) {
                    ready.failed("Стол открыт, но объявить о нём некому: в телефоне "
                            + "нет системного поиска служб. Найти этот стол второй "
                            + "телефон не сможет.");
                }
                return;
            }

            // Слушатель живёт в поле, а не в стеке: система зовёт его много
            // позже, когда из этого метода давно вышли.
            this.registration = new NsdManager.RegistrationListener() {

                @Override
                public void onServiceRegistered(NsdServiceInfo service) {
                    announce(ready, service.getServiceName());
                }

                /**
                 * Тот же ответ, но для Android 13 и старше.
                 *
                 * Подпись этого ответа система меняла: до 14 она отдавала имя
                 * строкой, с 14 — целым описанием службы. В 34-й версии
                 * строковой подписи уже нет, поэтому {@code @Override} здесь
                 * поставить нельзя — компилятор её не знает. Но на старых
                 * телефонах система зовёт именно её, и без этого метода игра
                 * там упала бы с «метод не найден». Приходит всегда ровно один
                 * из двух — смотря какая версия стоит на телефоне.
                 */
                public void onServiceRegistered(String registered) {
                    announce(ready, registered);
                }

                @Override
                public void onServiceUnregistered(NsdServiceInfo service) {
                    // Объявление сняли — говорить нечего: стол закрывают на
                    // выходе из игры, и никто этого не ждёт.
                }

                /** То же для Android 13 и старше: см. про две подписи выше. */
                public void onServiceUnregistered(String registered) {
                }

                @Override
                public void onRegistrationFailed(NsdServiceInfo service, int error) {
                    if (ready != null) {
                        ready.failed("Стол открыт, но в сети его не видно: система "
                                + "отказала в службе, код " + error + ".");
                    }
                }

                @Override
                public void onUnregistrationFailed(NsdServiceInfo service, int error) {
                    // Снять объявление не вышло. Делать с этим нечего: снимают
                    // его на выходе из игры, и разбираться поздно. Система
                    // уберёт объявление сама при следующем запуске игры.
                }
            };

            NsdServiceInfo service = new NsdServiceInfo();
            service.setServiceName(name);
            service.setServiceType(SERVICE_TYPE);
            // Порт объявляем настоящий: второй телефон по нему и придёт.
            service.setPort(server.getLocalPort());
            try {
                manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, registration);
            } catch (RuntimeException failed) {
                if (ready != null) {
                    ready.failed("Стол открыть не удалось: система отказала в службе.");
                }
            }
        }

        /**
         * Дождаться гостя. Звать из своего потока: здесь ждут.
         *
         * Закрытие стола из другого потока будит это ожидание отказом — так
         * уходят с экрана, не дождавшись второго телефона.
         */
        public Link accept() throws IOException {
            Socket socket = server.accept();
            try {
                return new WifiLink(socket, peer(socket));
            } catch (IOException failed) {
                // Соединение состоялось, а говорить по нему нельзя — сокет
                // держал бы силы телефона до конца партии. Закрываем сразу.
                closeQuietly(socket);
                throw failed;
            }
        }

        /**
         * Дождаться нескольких гостей — по одному, в порядке подключения.
         *
         * Столько, сколько названо. За столом бывает и трое, и тогда хозяину
         * нужны все каналы разом, прежде чем начинать раздачу. Если кто-то не
         * дошёл, уже принятые каналы закрываются: недобранный стол — не стол, и
         * оставлять половину гостей в неведении незачем.
         */
        public Link[] accept(int count) throws IOException {
            if (count <= 0) return new Link[0];
            Link[] guests = new Link[count];
            try {
                for (int i = 0; i < count; i++) guests[i] = accept();
            } catch (IOException failed) {
                for (Link guest : guests) {
                    if (guest != null) guest.close();
                }
                throw failed;
            }
            return guests;
        }

        /** Номер порта, на котором стоит стол. */
        public int port() {
            return server.getLocalPort();
        }

        /**
         * Имя стола, как его видят другие телефоны.
         *
         * До ответа системы — то, которым его назвали при открытии: система
         * может добавить к имени номер, если такое уже занято.
         */
        public String name() {
            return name;
        }

        /**
         * Закрыть стол: снять объявление и отпустить порт.
         *
         * Снять объявление обязательно. Система держит объявленную службу, пока
         * её не снимут или пока телефон не перезапустят: закрытый стол висел бы
         * у соседей в списках мёртвым призраком — найти его можно, подключиться
         * нельзя. Звать можно с любого потока и повторно; ждущий
         * {@link #accept()} при этом проснётся и откажет.
         */
        public synchronized void close() {
            if (closed) return;
            closed = true;

            if (registration != null) {
                try {
                    manager.unregisterService(registration);
                } catch (RuntimeException failed) {
                    // Службы уже нет или система сняла её сама — тот случай,
                    // когда беда не стоит внимания: стол закрыт.
                }
            }
            closeQuietly(server);
        }

        /** Сказать про стол один раз, сколько бы ответов ни пришло. */
        private void announce(Ready ready, String registered) {
            if (announced) return;
            announced = true;
            this.name = registered;
            if (ready != null) ready.ready(registered);
        }
    }

    /**
     * Идущий поиск чужих столов.
     *
     * Живёт, пока его не остановят: система ищет службы всё это время, и новые
     * столы появляются в списке сами.
     */
    public static final class Discovery {

        private final NsdManager manager;
        private final Finder finder;
        private final ExecutorService asker;
        private final List<Discovered> found = new ArrayList<>();
        private final NsdManager.DiscoveryListener listener;

        /**
         * Главный поток приложения: в нём живут экраны, и только туда можно
         * отдавать находки.
         *
         * Система зовёт слушателя поиска со своего потока — не с того, в
         * котором экран. Экран про это не знает и знать не должен: он ведёт
         * свой список столов прямо из находок, а список, который правят из
         * двух потоков разом, однажды теряет запись или роняет приложение
         * прямо на поиске. Поэтому обещание {@link Finder} держится здесь.
         */
        private final Handler main = new Handler(Looper.getMainLooper());

        private volatile boolean stopped;

        Discovery(NsdManager manager, Finder finder) {
            this.manager = manager;
            this.finder = finder;
            // Один поток на все вопросы об адресах. Система отвечает на них по
            // одному: вопрос, заданный раньше ответа на прошлый, она отвергает.
            // Очередь из одного потока — самое простое, что из этого следует;
            // на телефоне с тремя столами вокруг это и незаметно.
            this.asker = Executors.newSingleThreadExecutor(work -> {
                Thread thread = new Thread(work, "адреса столов");
                thread.setDaemon(true);
                return thread;
            });

            this.listener = new NsdManager.DiscoveryListener() {

                @Override
                public void onDiscoveryStarted(String type) {
                    // Поиск пошёл — говорить нечего: находки скажут сами.
                }

                @Override
                public void onDiscoveryStopped(String type) {
                    // Поиск кончился: по нашей просьбе или сам. Не новость.
                }

                @Override
                public void onServiceFound(NsdServiceInfo service) {
                    ask(service);
                }

                @Override
                public void onServiceLost(NsdServiceInfo service) {
                    gone(service.getServiceName());
                }

                @Override
                public void onStartDiscoveryFailed(String type, int error) {
                    // Система не взялась искать. Службу за собой она не снимает,
                    // поэтому снимаем сами: иначе поиск остался бы занятым до
                    // перезапуска телефона, и следующая попытка отказала бы так
                    // же. Так советует и сама система в описании ошибки.
                    stop();
                    final int code = error;
                    tell(() -> {
                        if (finder != null) {
                            finder.failed("Столы искать не удалось: система отказала, "
                                    + "код " + code + ".");
                        }
                    });
                }

                @Override
                public void onStopDiscoveryFailed(String type, int error) {
                    // Остановить не вышло. Повторять тут же бессмысленно — ответ
                    // будет тот же, — но помнить об этом стоит: служба остаётся
                    // занятой до перезапуска телефона.
                }
            };
        }

        /** Сказать экрану — всегда в главном потоке, как обещает {@link Finder}. */
        private void tell(Runnable what) {
            main.post(what);
        }

        private void start() {
            if (manager == null) {
                tell(() -> {
                    if (finder != null) {
                        finder.failed("Столы искать не удалось: в телефоне нет "
                                + "системного поиска служб.");
                    }
                });
                return;
            }
            try {
                manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener);
            } catch (RuntimeException failed) {
                // Система отказала сразу — обычно потому, что прошлый поиск ещё
                // не остановлен. Ждать находок нечего, и молчать об этом нельзя.
                tell(() -> {
                    if (finder != null) {
                        finder.failed("Столы искать не удалось: система отказала.");
                    }
                });
            }
        }

        /** Спросить у системы адрес находки. Вопрос встаёт в очередь. */
        private void ask(NsdServiceInfo service) {
            if (stopped) return;
            try {
                asker.execute(() -> resolve(service));
            } catch (RejectedExecutionException stopped) {
                // Поиск остановили между проверкой и вопросом — спрашивать уже
                // некого и незачем.
            }
        }

        /**
         * Спросить адрес стола.
         *
         * Находка — это только имя: адреса в ней нет. Адрес система отдаёт
         * отдельным ответом, и до него стол в списке не появится — по имени
         * подключаться некуда.
         */
        private void resolve(NsdServiceInfo service) {
            if (stopped) return;
            try {
                manager.resolveService(service, new NsdManager.ResolveListener() {

                    @Override
                    public void onResolveFailed(NsdServiceInfo failed, int error) {
                        // Стол не назвал адреса — стало быть, его нет: имя без
                        // адреса не стол, и сказать о нём нечего. Поиск идёт
                        // дальше: живой стол система назовёт ещё раз.
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo resolved) {
                        if (stopped) return;
                        InetAddress host = address(resolved);
                        if (host == null) return;
                        remember(new Discovered(resolved.getServiceName(),
                                host, resolved.getPort()));
                    }
                });
            } catch (RuntimeException failed) {
                // Система отказала в вопросе. Стол останется ненайденным, но
                // ронять из-за него весь поиск незачем.
            }
        }

        /** Положить стол в список — но только если его там ещё нет. */
        private void remember(Discovered table) {
            synchronized (found) {
                for (Discovered known : found) {
                    // Система на новых версиях может назвать один стол дважды:
                    // у него бывает не один адрес. Второй раз это тот же стол.
                    if (known.same(table)) return;
                }
                found.add(table);
            }
            tell(() -> {
                if (finder != null) finder.table(table);
            });
        }

        /** Стол пропал из виду — убираем из списка: подключиться уже нельзя. */
        private void gone(String name) {
            boolean was;
            synchronized (found) {
                was = found.removeIf(table -> table.name().equals(name));
            }
            if (was) {
                tell(() -> {
                    if (finder != null) finder.gone(name);
                });
            }
        }

        /**
         * Столы, найденные к этой минуте, — снимок списка.
         *
         * Снимок, а не сам список: поиск пополняет его своим ходом, и отдавать
         * наружу то, что меняется под руками, нельзя.
         */
        public List<Discovered> tables() {
            synchronized (found) {
                return new ArrayList<>(found);
            }
        }

        /**
         * Перестать искать.
         *
         * Обязательно: система держит поиск, пока его не остановят или пока
         * телефон не перезапустят, — а поиск тратит силы телефона и держит
         * службу занятой. Звать можно с любого потока и повторно.
         */
        public void stop() {
            if (stopped) return;
            stopped = true;
            asker.shutdownNow();
            try {
                manager.stopServiceDiscovery(listener);
            } catch (RuntimeException failed) {
                // Поиска уже нет — тем лучше.
            }
        }
    }
}

package ru.romeo198533.durak;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Глава стола: ведёт партию и рассылает виды.
 *
 * Партия живёт у главы в одном экземпляре, а все остальные получают по каналу
 * {@link Seat} — ровно то, что им положено знать. Ход приходит строкой, стол
 * его судит и либо принимает, либо отвечает отказом; и то и другое уходит назад
 * тому, кто ходил. А после каждого хода всем едет свежий вид — его-то и рисуют
 * экраны.
 *
 * Глава играет за столом наравне со всеми, и своё место у него такое же: канал
 * ведёт не к другому телефону, а к его собственному экрану. Отдельного случая
 * «свой игрок» здесь нет — и ошибиться в нём негде.
 *
 * Слушают все каналы разом, а не один, чей черёд. Ход по очереди и правда
 * приходит только от одного игрока, но сдаться может любой и в любой момент —
 * а значит, ждать одного значило бы не услышать остальных. Читает при этом
 * по-прежнему ровно один поток на канал: сокет, который читают двое, теряет
 * строки на полуслове.
 *
 * Ходы, пришедшие не в свой черёд, стол разбирает и отвергает словами — это
 * дешевле, чем гадать, кого теперь слушать.
 */
public final class Host {

    private final Table table;
    private final Link[] links;

    /** Что пришло от игроков: кто и чем. */
    private final BlockingQueue<Talk> incoming = new LinkedBlockingQueue<>();

    /** Глава остановил партию сам — тогда оборванная связь не новость. */
    private volatile boolean stopped;

    /** Слушающие потоки заведены один раз: второй раз их не поднять. */
    private boolean listening;

    private static final class Talk {

        final int who;

        /** Ход игрока. null — канал закрылся. */
        final String line;

        Talk(int who, String line) {
            this.who = who;
            this.line = line;
        }
    }

    public Host(Table table, Link[] links) {
        if (links.length != table.seats()) {
            throw new IllegalArgumentException("каналов " + links.length
                    + ", а мест за столом " + table.seats());
        }
        this.table = table;
        this.links = links;
    }

    /**
     * Вести партию, пока она не кончится.
     *
     * Звать из своего потока: здесь всё ждёт — и хода игрока, и чтения из
     * канала. Экран в это время живёт своей жизнью и рисует то, что приехало.
     *
     * @throws IOException связь оборвалась на чьей-то стороне.
     */
    public void run() throws IOException {
        listen();
        broadcast();

        while (!table.isOver()) {
            Talk talk;
            try {
                talk = incoming.take();
            } catch (InterruptedException closed) {
                // Партию закрыли свои же: это не обрыв.
                Thread.currentThread().interrupt();
                close();
                return;
            }

            if (talk.line == null) {
                if (stopped) break;
                throw new IOException("связь с " + links[talk.who].name() + " оборвалась");
            }

            // Имя — не ход: его называют один раз, и отвечать на него нечем.
            // Зато после него тут же едет свежий вид, и в нём имя уже всем.
            String name = Wire.decodeName(talk.line);
            if (name != null) {
                table.setName(talk.who, name);
                broadcast();
                continue;
            }

            String reason = table.act(talk.who, Wire.decodeMove(talk.line));
            links[talk.who].send(Wire.encodeReply(reason));

            // Вид рассылается даже после отказа: отказ — тоже новость, и он
            // уезжает тому, кто ходил, отдельной строкой.
            broadcast();
        }
        // Последний вид уже разослан — в нём и итог партии. Закрываем каналы:
        // держать их открытыми после конца незачем.
        close();
    }

    /** Остановить партию: разойтись по домам, не доигрывая. */
    public void stop() {
        stopped = true;
        close();
    }

    /** Завести по слушателю на канал: у каждого канала ровно один читающий. */
    private void listen() {
        if (listening) return;
        listening = true;
        for (int p = 0; p < links.length; p++) {
            final int who = p;
            Thread thread = new Thread(() -> {
                try {
                    while (true) {
                        String line = links[who].read();
                        incoming.add(new Talk(who, line));
                        if (line == null) return;
                    }
                } catch (IOException | RuntimeException broken) {
                    // Кривая строка или оборванный провод — для стола это одно
                    // и то же: канала больше нет.
                    incoming.add(new Talk(who, null));
                }
            }, "слушаю " + links[p].name());

            // Сторож, а не работник: партия не должна держать телефон, когда
            // её уже никто не смотрит.
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void broadcast() throws IOException {
        for (int p = 0; p < links.length; p++) {
            links[p].send(Wire.encode(table.seatFor(p)));
        }
    }

    private void close() {
        for (Link link : links) {
            try {
                link.close();
            } catch (RuntimeException ignored) {
                // Закрывать — не то место, где стоит падать: партия уже
                // кончилась, и терять из-за этого её итог нельзя.
            }
        }
    }
}

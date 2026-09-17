package ru.romeo198533.durak;

import java.io.IOException;

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
 * Ходит всегда ровно тот, чей черёд: глава не спрашивает всех подряд, а ждёт
 * одного. Клиент, стало быть, шлёт ход только тогда, когда его об этом
 * попросит вид, — и ждёт ответа, прежде чем слать следующий.
 */
public final class Host {

    private final Table table;
    private final Link[] links;

    /** Глава остановил партию сам — тогда оборванная связь не новость. */
    private volatile boolean stopped;

    public Host(Table table, Link[] links) {
        if (links.length != Table.SEATS) {
            throw new IllegalArgumentException("каналов " + links.length
                    + ", а мест за столом " + Table.SEATS);
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
        broadcast();
        while (!table.isOver()) {
            int who = table.turn();
            String line = links[who].read();
            if (line == null) {
                if (stopped) return;
                throw new IOException("связь с " + links[who].name() + " оборвалась");
            }

            String reason = table.act(who, Wire.decodeMove(line));
            links[who].send(Wire.encodeReply(reason));
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

package ru.romeo198533.durak;

import java.io.IOException;

/**
 * Игрок за чужим столом: получает свой вид и шлёт ходы главе.
 *
 * Всё, что он знает о партии, приходит видом места. Считать правила самому ему
 * не нужно и нельзя: он их только подсказывает для экрана, а решает глава.
 *
 * Ход уходит вместе с ожиданием ответа — так и заведено у главы: сперва ответ
 * на ход, потом свежий вид. Ждать ответа обязательно: иначе второй ход ушёл бы
 * вдогонку первому и попал в партию тогда, когда его никто не ждёт.
 */
public final class Guest {

    private final Link link;

    private Seat seat;

    public Guest(Link link) {
        this.link = link;
    }

    /**
     * Дождаться свежего вида.
     *
     * Звать из своего потока: здесь ждут. Пока партия идёт, виды приходят
     * после каждого хода — и своего, и чужого.
     *
     * @return вид или null, если глава закрыл стол.
     */
    public Seat receive() throws IOException {
        String line = link.read();
        if (line == null) return null;
        seat = Wire.decode(line);
        return seat;
    }

    /** Последний полученный вид. */
    public Seat seat() {
        return seat;
    }

    /**
     * Отправить ход и дождаться ответа главы.
     *
     * @return null — ход принят; иначе причина отказа, словами для игрока.
     */
    public String send(Wire.Move move) throws IOException {
        link.send(Wire.encode(move));
        String line = link.read();
        if (line == null) throw new IOException("глава стола закрыл стол");
        return Wire.decodeReply(line);
    }

    /**
     * Назвать себя — первым словом, ещё до партии.
     *
     * Ответа здесь не ждут: имя не ход, и отказывать в нём нечего. Поэтому и
     * звать это можно только до того, как за канал взялся читающий поток, —
     * иначе ответ на ход и это слово разъехались бы.
     */
    public void hello(String name) throws IOException {
        link.send(Wire.encodeName(name));
    }

    /** Кто за столом на другом конце — для экрана. */
    public String host() {
        return link.name();
    }

    public void close() {
        link.close();
    }
}

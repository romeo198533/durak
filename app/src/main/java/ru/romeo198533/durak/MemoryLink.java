package ru.romeo198533.durak;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Канал в одной памяти: два конца, между ними очередь строк.
 *
 * Нужен затем, чтобы у главы стола его собственное место было устроено ровно
 * так же, как чужое. Глава держит партию и играет сам: своё место он получает
 * по такому же каналу, только на другом конце его ждёт не радио, а экран в
 * том же телефоне. Отдельного случая «свой игрок» в партии не остаётся, и
 * ошибка в нём невозможна — писать её негде.
 *
 * Тем же каналом пользуется проверка партии: там на обоих концах сидят
 * программы, и вся связь идёт всерьёз, только без радио.
 */
public final class MemoryLink {

    /**
     * Пара каналов, соединённых друг с другом.
     *
     * @return два конца: что отправили в первый — прочитается во втором, и
     *         наоборот. Первым в паре идёт тот, кого называет хозяином глава
     *         стола, — по нему и видно, кого из двоих позвали.
     */
    public static Link[] pair(String left, String right) {
        Pipe pipe = new Pipe();
        return new Link[]{
                new End(pipe, pipe.toRight, pipe.toLeft, right),
                new End(pipe, pipe.toLeft, pipe.toRight, left),
        };
    }

    /** Две очереди и общий признак закрытия — то, чем связаны концы. */
    private static final class Pipe {

        final BlockingQueue<String> toRight = new LinkedBlockingQueue<>();
        final BlockingQueue<String> toLeft = new LinkedBlockingQueue<>();

        /** Закрытие общее на оба конца — единственное, что едет вне очереди. */
        volatile boolean closed;
    }

    private static final class End implements Link {

        private final Pipe pipe;
        private final BlockingQueue<String> out;
        private final BlockingQueue<String> in;
        private final String peer;

        End(Pipe pipe, BlockingQueue<String> out, BlockingQueue<String> in, String peer) {
            this.pipe = pipe;
            this.out = out;
            this.in = in;
            this.peer = peer;
        }

        @Override
        public void send(String line) throws IOException {
            if (pipe.closed) throw new IOException("связь с " + peer + " закрыта");
            out.add(line);
        }

        @Override
        public String read() throws IOException {
            // Ждём с оглядкой на закрытие, а не вечно: закрытый канал не
            // положит в очередь ничего, и без оглядки чтение висело бы до
            // конца времён, не давая экрану сказать, что связь пропала.
            //
            // Закрытие общее на оба конца, поэтому закрытый сосед будит и это
            // чтение — так же, как разрыв соединения будит чтение из сокета.
            while (!pipe.closed) {
                try {
                    String line = in.poll(200, TimeUnit.MILLISECONDS);
                    if (line != null) return line;
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            // Успевшее лечь в очередь отдаём и после закрытия: последний вид
            // партии игрок обязан увидеть, даже если глава закрыл стол сразу
            // за ним.
            return in.poll();
        }

        @Override
        public void close() {
            pipe.closed = true;
        }

        @Override
        public String name() {
            return peer;
        }
    }

    private MemoryLink() {
    }
}

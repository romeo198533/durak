package ru.romeo198533.durak;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Канал по вай-фаю: строки поверх соединения между двумя телефонами.
 *
 * Партия про этот класс ничего не знает — она знает {@link Link}. Устройство
 * здесь то же, что у {@link BluetoothLink}: разница только в проводе, и ни один
 * ход от неё измениться не может. Поэтому и решения те же — замок на отправке,
 * null вместо ошибки при обрыве, молчаливое закрытие.
 *
 * Вай-фай — это поток байтов, а не сообщений: две строки, отправленные подряд,
 * на том конце могут приехать одной пачкой, а одна — по кускам. Поэтому перевод
 * строки в конце здесь не украшение, а сам договор: {@link BufferedReader#readLine()}
 * собирает строку из кусков и отдаёт её, только когда кусок кончился. В
 * {@link Wire} переводов строк в полях нет, так что строка всегда приезжает
 * целиком.
 *
 * Кодировка одна и та же на обоих концах — иначе русские имена разъехались бы.
 *
 * Чтение блокирующее: звать его можно только из своего потока, не из того,
 * который рисует экран.
 */
public final class WifiLink implements Link {

    private final Socket socket;
    private final BufferedReader in;
    private final Writer out;
    private final String peer;

    /**
     * @param socket соединение с тем концом. Закрывает его {@link #close()}.
     * @param peer   кто на том конце — для экрана.
     */
    public WifiLink(Socket socket, String peer) throws IOException {
        this.socket = socket;
        this.peer = peer;
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8);
    }

    /**
     * Отправить строку.
     *
     * Замок на отправке: с одного телефона пишут два потока — игрок за столом и
     * тот, кто ведёт партию, — и две строки, положенные в поток разом,
     * склеились бы в одну и разошлись бы на том конце как мусор.
     */
    @Override
    public synchronized void send(String line) throws IOException {
        out.write(line);
        out.write("\n");
        out.flush();
    }

    /**
     * Прочитать строку, дождавшись её.
     *
     * Обрыв связи — это конец, а не ошибка чтения: {@link Link} о том и
     * уговаривались. Поэтому оборванное соединение отдаётся как null, и экран
     * скажет «связь пропала», а не «что-то пошло не так».
     *
     * Закрытие сокета из другого потока — законный способ разбудить это
     * чтение: так останавливают партию, когда игрок вышел.
     */
    @Override
    public String read() throws IOException {
        try {
            return in.readLine();
        } catch (IOException broken) {
            return null;
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Закрывать — не то место, где стоит падать: связь уже кончилась,
            // и терять из-за этого итог партии нельзя.
        }
    }

    /** Кто на том конце — имя стола, а если его не назвали, то адрес телефона. */
    @Override
    public String name() {
        return peer;
    }
}

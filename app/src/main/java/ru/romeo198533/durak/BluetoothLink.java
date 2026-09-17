package ru.romeo198533.durak;

import android.bluetooth.BluetoothSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Канал по Блютусу: строки поверх соединения между двумя телефонами.
 *
 * Партия про этот класс ничего не знает — она знает {@link Link}. Блютус
 * надевается на тот же провод, что уже проверен в одиночной игре и на каналах
 * в одной памяти, и по устройству не может изменить ни одного хода.
 *
 * Каждая строка кончается переводом строки: {@link BufferedReader#readLine()}
 * ждёт его, а {@link Wire} переводов строк в свои поля не кладёт, поэтому
 * строка всегда приезжает целиком. Кодировка одна и та же на обоих концах —
 * иначе русские имена устройств разъехались бы.
 *
 * Чтение блокирующее: звать его можно только из своего потока, не из того,
 * который рисует экран.
 */
public final class BluetoothLink implements Link {

    private final BluetoothSocket socket;
    private final BufferedReader in;
    private final Writer out;
    private final String peer;

    public BluetoothLink(BluetoothSocket socket, String peer) throws IOException {
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

    /** Кто на том конце — имя телефона, как его видно в списке спаренных. */
    @Override
    public String name() {
        return peer;
    }
}

package protocol;

import java.nio.ByteBuffer;
import java.util.List;

public class MessageCreator {
    private final ByteBuffer messageBuffer;

    public MessageCreator(List<Integer> array, ListTransferringProtocol protocol) {
        ByteBuffer messageBodyBuffer = protocol.encode(array);
        ByteBuffer messageLengthBuffer = ByteBuffer.allocate(Integer.BYTES);
        messageLengthBuffer.putInt(messageBodyBuffer.capacity());
        messageLengthBuffer.flip();
        messageBuffer = ByteBuffer.allocate(messageLengthBuffer.capacity() + messageBodyBuffer.capacity());
        while (messageLengthBuffer.hasRemaining()) {
            messageBuffer.put(messageLengthBuffer.get());
        }
        while (messageBodyBuffer.hasRemaining()) {
            messageBuffer.put(messageBodyBuffer.get());
        }
        messageBuffer.flip();
    }

    public ByteBuffer createdBuffer() {
        return messageBuffer;
    }
}

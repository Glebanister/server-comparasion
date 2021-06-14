package protocol;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

public class MessageAccepter {
    private static final int UNDEFINED_LENGTH = -1;

    private final ByteBuffer messageLengthBuffer = ByteBuffer.allocate(Integer.BYTES);
    private ByteBuffer messageBodyBuffer = null;
    private int messageLength = UNDEFINED_LENGTH;
    private List<Integer> acceptedList = null;
    private final ListTransferringProtocol protocol;

    public MessageAccepter(ListTransferringProtocol protocol) {
        this.protocol = protocol;
    }

    public void accept(ByteBuffer readData) throws ProtocolException {
        while (messageLengthBuffer.hasRemaining() && readData.hasRemaining()) {
            messageLengthBuffer.put(readData.get());
        }
        if (!messageLengthBuffer.hasRemaining() && messageLength == UNDEFINED_LENGTH) {
            messageLengthBuffer.flip();
            messageLength = messageLengthBuffer.getInt();
            messageBodyBuffer = ByteBuffer.allocate(messageLength);
        }
        if (messageBodyBuffer != null) {
            while (messageBodyBuffer.hasRemaining() && readData.hasRemaining()) {
                messageBodyBuffer.put(readData.get());
            }
        }
        if (messageBodyBuffer == null || messageBodyBuffer.hasRemaining()) {
            return;
        }
        messageBodyBuffer.flip();
        acceptedList = protocol.decode(messageBodyBuffer);
    }

    public Optional<List<Integer>> accepted() {
        return Optional.ofNullable(acceptedList);
    }

    public int getRemaining() {
        if (messageLength == UNDEFINED_LENGTH) {
            return messageLengthBuffer.remaining();
        }
        if (acceptedList == null) {
            return messageBodyBuffer.remaining();
        }
        return 0;
    }
}

package protocol;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class PrimitiveListTransferringProtocol implements ListTransferringProtocol {
    @Override
    public ByteBuffer encode(List<Integer> list) {
        ByteBuffer bytes = ByteBuffer.allocate(list.size() * Integer.BYTES);
        for (int elem : list) {
            bytes.putInt(elem);
        }
        bytes.flip();
        return bytes;
    }

    @Override
    public List<Integer> decode(ByteBuffer bytes) throws ProtocolException {
        List<Integer> ints = new ArrayList<>();
        while (bytes.hasRemaining()) {
            if (bytes.remaining() < Integer.BYTES) {
                throw new ProtocolException("The length of the byte array is not divisible by the integer length");
            }
            ints.add(bytes.getInt());
        }
        return ints;
    }
}

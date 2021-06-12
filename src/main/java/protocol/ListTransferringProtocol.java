package protocol;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.List;

public interface ListTransferringProtocol {
    ByteBuffer encode(List<Integer> list);

    List<Integer> decode(ByteBuffer bytes) throws ProtocolException;
}

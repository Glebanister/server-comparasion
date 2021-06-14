package protocol;

import com.google.protobuf.InvalidProtocolBufferException;
import protocol.protobuf.ProtoList;

import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.List;

public class ProtobufListTransferringProtocol implements ListTransferringProtocol {
    @Override
    public ByteBuffer encode(List<Integer> list) {
        var builder = ProtoList.newBuilder();
        builder.addAllInts(list);
        return ByteBuffer.wrap(builder.build().toByteArray());
    }

    @Override
    public List<Integer> decode(ByteBuffer bytes) throws ProtocolException {
        try {
            var instance = ProtoList.parseFrom(bytes.array());
            return instance.getIntsList();
        } catch (InvalidProtocolBufferException e) {
            throw new ProtocolException("Unable to parse bytes as protobuf list");
        }
    }
}

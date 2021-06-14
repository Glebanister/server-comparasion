package server;

import protocol.ListTransferringProtocol;
import protocol.MessageAccepter;
import protocol.MessageCreator;
import protocol.PrimitiveListTransferringProtocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockingArraySortingServer extends ClientAcceptingServer {
    private final boolean logInfo;

    public BlockingArraySortingServer(ListTransferringProtocol protocol, int port, boolean logInfo) {
        super(protocol, port, logInfo);
        this.logInfo = logInfo;
    }

    public static void main(String[] args) {
        ArraySortingServer server = new BlockingArraySortingServer(
                new PrimitiveListTransferringProtocol(),
                8000,
                false
        );
        server.run();
    }

    @Override
    protected ClientHandler makeClientHandler(SocketChannel socket) {
        return new BlockingClientHandler(socket, logInfo);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    private class BlockingClientHandler extends ClientHandler {
        private final ExecutorService reader = Executors.newSingleThreadExecutor();
        private final ExecutorService writer = Executors.newSingleThreadExecutor();
        private volatile boolean isWorking = true;

        protected BlockingClientHandler(SocketChannel socket, boolean logInfo) {
            super(socket, logInfo);
        }

        @Override
        public void close() throws IOException {
            super.close();
            isWorking = false;
        }

        public void handle() {
            reader.submit(() -> {
                while (isWorking) {
                    List<Integer> clientArray;
                    try {
                        clientArray = readArray();
                    } catch (IOException e) {
                        isWorking = false;
                        break;
                    }
                    submitClientTask(() -> {
                        sortArray(clientArray);
                        writer.submit(() -> {
                            try {
                                writeArray(clientArray);
                            } catch (IOException e) {
                                handlerLogger.handleException(e);
                            }
                        });
                    });
                }
            });
        }

        private List<Integer> readArray() throws IOException {
            MessageAccepter accepter = new MessageAccepter(getProtocol());
            handlerLogger.info(String.format("Reading array from %s", socket.getLocalAddress()));
            while (accepter.getRemaining() != 0) {
                ByteBuffer anotherPart = ByteBuffer.allocate(accepter.getRemaining());
                socket.read(anotherPart);
                anotherPart.flip();
                accepter.accept(anotherPart);
            }
            if (accepter.accepted().isEmpty()) {
                throw new RuntimeException("Server didn't receive all array");
            }
            handlerLogger.info(String.format("Read array: %s", accepter.accepted().get()));
            return accepter.accepted().get();
        }

        private void writeArray(List<Integer> array) throws IOException {
            handlerLogger.info(String.format("Writing array: %s", array));
            MessageCreator creator = new MessageCreator(array, getProtocol());
            socket.write(creator.createdBuffer());
            handlerLogger.info(String.format("Array is written: %s", array));
        }
    }
}

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
import java.util.concurrent.TimeUnit;

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

    private class BlockingClientHandler extends ClientHandler {
        private final ExecutorService reader = Executors.newSingleThreadExecutor();
        private final ExecutorService writer = Executors.newSingleThreadExecutor();
        private volatile boolean isWorking = true;

        protected BlockingClientHandler(SocketChannel socket, boolean logInfo) {
            super(socket, logInfo);
        }

        @Override
        public void close() throws IOException {
            handlerLogger.info("Closing");
            isWorking = false;
            reader.shutdown();
            writer.shutdown();
            try {
                boolean finished = writer.awaitTermination(5, TimeUnit.SECONDS)
                        && reader.awaitTermination(5, TimeUnit.SECONDS);
                if (!finished) {
                    throw new RuntimeException("Blocking client handler reader or writer won't close");
                }
            } catch (InterruptedException ignored) {
            }
            super.close();
            handlerLogger.info("Closed");
        }

        public void handle() {
            reader.submit(() -> {
                while (isWorking) {
                    List<Integer> clientArray;
                    try {
                        clientArray = readArray();
                        if (clientArray == null) {
                            isWorking = false;
                            break;
                        }
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
                while (anotherPart.hasRemaining()) {
                    int bytesRead = socket.read(anotherPart);
                    if (bytesRead < 0) {
                        return null;
                    }
                }
                anotherPart.flip();
                accepter.accept(anotherPart);
            }
            if (accepter.accepted().isEmpty()) {
                throw new RuntimeException("Server didn't receive all array");
            }
            handlerLogger.info("Read array");
            return accepter.accepted().get();
        }

        private void writeArray(List<Integer> array) throws IOException {
            handlerLogger.info("Writing array");
            MessageCreator creator = new MessageCreator(array, getProtocol());
            while (creator.createdBuffer().hasRemaining()) {
                int bytesWritten = socket.write(creator.createdBuffer());
                if (bytesWritten < 0) {
                    throw new IOException("Server couldn't send an array");
                }
            }
            handlerLogger.info("Array is written");
        }
    }
}

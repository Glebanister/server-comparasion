package server;

import logger.ContextLogger;
import protocol.ListTransferringProtocol;
import protocol.MessageAccepter;
import protocol.MessageCreator;
import protocol.PrimitiveListTransferringProtocol;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockingArraySortingServer extends ArraySortingServer implements Closeable {
    private volatile boolean notClosed;
    private final int port;
    private final ExecutorService arraySorter;
    private final List<ClientHandler> clientHandlers;

    public BlockingArraySortingServer(int port, int nThreads, ContextLogger logger, ListTransferringProtocol protocol) {
        super(logger, protocol);
        this.notClosed = true;
        this.port = port;
        this.arraySorter = Executors.newFixedThreadPool(nThreads);
        this.clientHandlers = new ArrayList<>();
    }

    public static void main(String[] args) {
        ArraySortingServer server = new BlockingArraySortingServer(
                8000,
                5,
                new ContextLogger("Server", true),
                new PrimitiveListTransferringProtocol()
        );
        server.run();
    }

    @Override
    public void run() {
        printInfo("Running");
        try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {
            serverSocket.bind(new InetSocketAddress(port));
            while (notClosed) {
                ClientHandler newClientHandler = new ClientHandler(
                        serverSocket.accept()
                );
                printInfo(String.format("Client accepted: %s", newClientHandler.socket.getLocalAddress()));
                clientHandlers.add(newClientHandler);
                newClientHandler.handle();
            }
        } catch (IOException e) {
            handleIOException(e);
        }
    }

    @Override
    public void close() {
        notClosed = false;
        clientHandlers.forEach(ClientHandler::close);
    }

    private class ClientHandler implements Closeable {
        private final ExecutorService reader = Executors.newSingleThreadExecutor();
        private final ExecutorService writer = Executors.newSingleThreadExecutor();
        private final SocketChannel socket;
        private volatile boolean isWorking;

        public ClientHandler(SocketChannel socket) {
            this.socket = socket;
            this.isWorking = true;
        }

        @Override
        public void close() {
            isWorking = false;
            try {
                socket.close();
            } catch (IOException e) {
                handleIOException(e);
            }
        }

        public void handle() {
            reader.submit(() -> {
                while (isWorking) {
                    List<Integer> clientArray;
                    try {
                        clientArray = readArray();
                    } catch (IOException e) {
                        close();
                        break;
                    }
                    arraySorter.submit(() -> {
                        sortArray(clientArray);
                        writer.submit(() -> {
                            try {
                                writeArray(clientArray);
                            } catch (IOException e) {
                                handleIOException(e);
                            }
                        });
                    });
                }
            });
        }

        private List<Integer> readArray() throws IOException {
            MessageAccepter accepter = new MessageAccepter(protocol);
            printInfo(String.format("Reading array from %s", socket.getLocalAddress()));
            while (accepter.getRemaining() != 0) {
                ByteBuffer anotherPart = ByteBuffer.allocate(accepter.getRemaining());
                socket.read(anotherPart);
                anotherPart.flip();
                accepter.accept(anotherPart);
            }
            if (accepter.accepted().isEmpty()) {
                throw new RuntimeException("Server didn't receive all array");
            }
            printInfo(String.format("Read array: %s", accepter.accepted().get()));
            return accepter.accepted().get();
        }

        private void writeArray(List<Integer> array) throws IOException {
            printInfo(String.format("Writing array: %s", array));
            MessageCreator creator = new MessageCreator(array, protocol);
            socket.write(creator.createdBuffer());
            printInfo(String.format("Array is written: %s", array));
        }
    }
}

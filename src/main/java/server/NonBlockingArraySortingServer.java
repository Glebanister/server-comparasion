package server;

import logger.ContextLogger;
import protocol.ListTransferringProtocol;
import protocol.MessageAccepter;
import protocol.MessageCreator;
import protocol.PrimitiveListTransferringProtocol;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NonBlockingArraySortingServer extends ClientAcceptingServer {
    private final ReadingSelectorHolder readingSelector = new ReadingSelectorHolder();
    private final WritingSelectorHolder writingSelector = new WritingSelectorHolder();
    private final ExecutorService selectorRunners = Executors.newFixedThreadPool(2);
    private final boolean logInfo;

    public static void main(String[] args) {
        try {
            ArraySortingServer server = new NonBlockingArraySortingServer(
                    new PrimitiveListTransferringProtocol(),
                    8000,
                    false);
            server.run();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public NonBlockingArraySortingServer(ListTransferringProtocol protocol, int port, boolean logInfo) {
        super(protocol, port, logInfo);
        selectorRunners.submit(readingSelector);
        selectorRunners.submit(writingSelector);
        this.logInfo = logInfo;
    }

    @Override
    protected ClientHandler makeClientHandler(SocketChannel channel) {
        return new NonBlockingClientHandler(channel, logInfo);
    }

    @Override
    public void close() throws IOException {
        readingSelector.close();
        writingSelector.close();
        selectorRunners.shutdown();
        try {
            if (!selectorRunners.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new TimeoutException("Selectors won't terminate");
            }
        } catch (InterruptedException ignored) {
        } catch (TimeoutException e) {
            serverLogger.handleException(e);
        }
        super.close();
    }

    private class NonBlockingClientHandler extends ClientHandler {
        private final Queue<ByteBuffer> toSendQueue = new ConcurrentLinkedQueue<>();
        private MessageAccepter messageAccepter = new MessageAccepter(getProtocol());

        private NonBlockingClientHandler(SocketChannel socket, boolean logInfo) {
            super(socket, logInfo);
        }

        @Override
        public void handle() {
            readingSelector.registerClient(this);
        }

        public void addBufferToSend(ByteBuffer buffer) {
            toSendQueue.add(buffer);
        }

        public boolean write() throws IOException {
            while (!toSendQueue.isEmpty()) {
                handlerLogger.info("Writing buffer to socket");
                int bytesWritten = socket.write(toSendQueue.peek());
                handlerLogger.info(String.format("Written %d bytes", bytesWritten));
                assert toSendQueue.peek() != null;
                if (toSendQueue.peek().hasRemaining()) {
                    return false;
                }
                toSendQueue.poll();
            }
            return true;
        }

        public boolean read() throws IOException {
            while (true) {
                ByteBuffer buffer = ByteBuffer.allocate(messageAccepter.getRemaining());
                handlerLogger.info("Reading from socket");
                int bytesRead = socket.read(buffer);
                handlerLogger.info(String.format("Read from socket %d bytes", bytesRead));
                if (bytesRead < 0) {
                    return true;
                }
                if (bytesRead == 0) {
                    break;
                }
                buffer.flip();
                messageAccepter.accept(buffer);
                if (messageAccepter.accepted().isPresent()) {
                    submitClientTask(new ArraySortingTask(
                            new ArrayList<>(messageAccepter.accepted().get()),
                            this));
                    messageAccepter = new MessageAccepter(getProtocol());
                }
            }
            return false;
        }
    }

    private class ArraySortingTask implements Runnable {
        private final List<Integer> array;
        private final NonBlockingClientHandler client;

        private ArraySortingTask(List<Integer> array, NonBlockingClientHandler client) {
            this.array = array;
            this.client = client;
        }

        @Override
        public void run() {
            sortArray(array);
            MessageCreator messageCreator = new MessageCreator(array, getProtocol());
            client.addBufferToSend(messageCreator.createdBuffer());
            writingSelector.registerClient(client);
        }
    }

    private abstract class SelectorHolder implements Closeable, Runnable {
        private volatile boolean isRunning;
        private final Selector selector;
        private final int selectorInterests;
        private final Queue<NonBlockingClientHandler> nonRegisteredHandlers;
        private final Lock socketRegistrationLock = new ReentrantLock();
        protected final ContextLogger selectorLogger = new ContextLogger("Selector", false);

        protected SelectorHolder(int selectorInterests) {
            try {
                this.selector = Selector.open();
            } catch (IOException e) {
                throw new RuntimeException("Unable to open selector");
            }
            this.selectorInterests = selectorInterests;
            this.nonRegisteredHandlers = new ArrayDeque<>();
        }

        public void registerClient(NonBlockingClientHandler client) {
            socketRegistrationLock.lock();
            try {
                if (client.socket.isBlocking()) {
                    client.socket.configureBlocking(false);
                }
                nonRegisteredHandlers.add(client);
                selector.wakeup();
            } catch (IOException e) {
                selectorLogger.handleException(e);
            } finally {
                socketRegistrationLock.unlock();
            }
        }

        protected abstract void handleSelectedClient(SelectionKey key) throws IOException;

        @Override
        public void close() throws IOException {
            isRunning = false;
            selector.close();
        }

        @Override
        public void run() {
            isRunning = true;
            while (isRunning) {
                try {
                    selector.select();
                    socketRegistrationLock.lock();
                    try {
                        for (ClientHandler newClient : nonRegisteredHandlers) {
                            newClient.socket.register(
                                    selector,
                                    selectorInterests,
                                    newClient);
                        }
                        nonRegisteredHandlers.clear();
                    } finally {
                        socketRegistrationLock.unlock();
                    }

                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        if ((key.interestOps() & selectorInterests) > 0) {
                            handleSelectedClient(key);
                        }
                        iterator.remove();
                    }
                } catch (IOException e) {
                    selectorLogger.handleException(e);
                    isRunning = false;
                }
            }
        }
    }

    private class WritingSelectorHolder extends SelectorHolder {
        private WritingSelectorHolder() {
            super(SelectionKey.OP_WRITE);
        }

        @Override
        protected void handleSelectedClient(SelectionKey key) throws IOException {
            assert key.attachment() instanceof NonBlockingClientHandler;
            NonBlockingClientHandler client = (NonBlockingClientHandler) key.attachment();
            if (client.write()) {
                key.cancel();
            }
        }
    }

    private class ReadingSelectorHolder extends SelectorHolder {
        private ReadingSelectorHolder() {
            super(SelectionKey.OP_READ);
        }

        @Override
        protected void handleSelectedClient(SelectionKey key) throws IOException {
            assert key.attachment() instanceof NonBlockingClientHandler;
            NonBlockingClientHandler client = (NonBlockingClientHandler) key.attachment();
            if (client.read()) {
                key.cancel();
            }
        }
    }
}

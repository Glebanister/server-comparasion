package server;

import logger.ContextLogger;
import protocol.ListTransferringProtocol;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ClientAcceptingServer extends ArraySortingServer {
    private final List<ClientHandler> clients;
    private final ExecutorService clientTaskExecutor;
    private final Lock serverServeLock = new ReentrantLock();
    private final Condition serverServed = serverServeLock.newCondition();
    private boolean isServerServed = false;

    public ClientAcceptingServer(ListTransferringProtocol protocol,
                                 int port,
                                 int taskExecutorThreads,
                                 boolean logInfo) {
        super(protocol, port, logInfo);
        this.clients = new ArrayList<>();
        this.clientTaskExecutor = Executors.newFixedThreadPool(taskExecutorThreads);
    }

    public ClientAcceptingServer(ListTransferringProtocol protocol,
                                 int port,
                                 boolean logInfo) {
        this(protocol, port, Runtime.getRuntime().availableProcessors() - 2, logInfo);
    }

    public void submitClientTask(Runnable task) {
        clientTaskExecutor.submit(task);
    }

    protected abstract ClientHandler makeClientHandler(SocketChannel channel);

    @Override
    public void run() {
        super.run();
        serverLogger.info("Running");
        try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {
            serverSocket.bind(new InetSocketAddress(getPort()));
            serverLogger.info("Bound to port");
            serverServeLock.lock();
            try {
                isServerServed = true;
                serverServed.signal();
            } finally {
                serverServeLock.unlock();
            }
            while (running()) {
                try {
                    ClientHandler handler = makeClientHandler(serverSocket.accept());
                    serverLogger.info(String.format("Client connected: %s", handler.socket.getRemoteAddress()));
                    clients.add(handler);
                    handler.handle();
                } catch (ClosedByInterruptException ignored) {
                    serverLogger.info("Server interrupted");
                    close();
                }
            }
        } catch (IOException e) {
            serverLogger.handleException(e);
        }
    }

    @Override
    public void awaitServed() {
        serverServeLock.lock();
        try {
            while (!isServerServed) {
                serverServed.await();
            }
        } catch (InterruptedException e) {
            serverLogger.handleException(e);
        } finally {
            serverServeLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        serverLogger.info("Closing");
        super.close();
        for (ClientHandler client : clients) {
            client.close();
        }
        clientTaskExecutor.shutdownNow();
        try {
            if (!clientTaskExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new RuntimeException("Client task executor won't close");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        serverLogger.info("Closed");
    }

    public abstract static class ClientHandler {
        protected final SocketChannel socket;
        protected final ContextLogger handlerLogger;

        protected ClientHandler(SocketChannel socket, boolean logInfo) {
            this.socket = socket;
            this.handlerLogger = new ContextLogger("ClientHandler", logInfo);
        }

        public abstract void handle();

        public void close() throws IOException {
            socket.close();
        }
    }
}

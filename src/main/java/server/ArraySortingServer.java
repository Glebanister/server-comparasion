package server;

import logger.ContextLogger;
import protocol.ListTransferringProtocol;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

public abstract class ArraySortingServer implements Runnable, Closeable {
    protected final ContextLogger serverLogger;
    protected volatile boolean isRunning;
    private final ListTransferringProtocol protocol;
    private final int port;

    public ArraySortingServer(ListTransferringProtocol protocol, int port, boolean logInfo) {
        this.serverLogger = new ContextLogger("Server", logInfo);
        this.port = port;
        this.isRunning = true;
        this.protocol = protocol;
    }

    public abstract void awaitServed();

    protected void sortArray(List<Integer> ints) {
        for (int i = 0; i < ints.size(); ++i) {
            for (int j = i + 1; j < ints.size(); ++j) {
                if (ints.get(i) > ints.get(j)) {
                    Collections.swap(ints, i, j);
                }
            }
        }
    }

    public boolean running() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    public ListTransferringProtocol getProtocol() {
        return protocol;
    }

    @Override
    public void close() throws IOException {
        isRunning = false;
    }

    @Override
    public void run() {
        isRunning = true;
    }
}

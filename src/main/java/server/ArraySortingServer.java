package server;

import logger.ContextLogger;
import protocol.ListTransferringProtocol;

import javax.naming.Context;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;

public abstract class ArraySortingServer implements Runnable {
    private final ContextLogger logger;
    protected final ListTransferringProtocol protocol;

    public ArraySortingServer(ContextLogger logger, ListTransferringProtocol protocol) {
        this.logger = logger;
        this.protocol = protocol;
    }

    protected void sortArray(List<Integer> ints) {
        for (int i = 0; i < ints.size(); ++i) {
            for (int j = i + 1; j < ints.size(); ++j) {
                if (ints.get(i) > ints.get(j)) {
                    Collections.swap(ints, i, j);
                }
            }
        }
    }

    protected void handleIOException(IOException e) {
        logger.handleException(e);
    }

    protected void printInfo(String message) {
        logger.info(message);
    }
}

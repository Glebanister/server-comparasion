package client;

import logger.ContextLogger;
import protocol.ListTransferringProtocol;
import protocol.MessageAccepter;
import protocol.MessageCreator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ArraySortingClient implements Runnable {
    private final int arrayLength;
    private final int requestDeltaMs;
    private final int requestsTotal;
    private final ListTransferringProtocol listProtocol;
    private final InetSocketAddress serverAddress;
    private final SimultaneousJobsStats.SingleSimultaneousJobStats statsCounter;
    private final ContextLogger contextLogger;

    public ArraySortingClient(int arrayLength,
                              int requestDeltaMs,
                              int requestsTotal,
                              ListTransferringProtocol listProtocol,
                              InetSocketAddress serverAddress,
                              SimultaneousJobsStats statsCounter,
                              boolean logInfo) {
        this.arrayLength = arrayLength;
        this.requestDeltaMs = requestDeltaMs;
        this.requestsTotal = requestsTotal;
        this.listProtocol = listProtocol;
        this.serverAddress = serverAddress;
        this.statsCounter = statsCounter.registerStats();
        this.contextLogger = new ContextLogger(String.format("Client %s", this), logInfo);
    }

    @Override
    public void run() {
        List<Integer> arrayToSort = new ArrayList<>();
        List<Integer> sortedArray = new ArrayList<>();
        Random rand = new Random();
        for (int i = 0; i < arrayLength; ++i) {
            Integer randInt = rand.nextInt();
            arrayToSort.add(randInt);
            sortedArray.add(randInt);
        }
        sortedArray.sort(Comparator.naturalOrder());
        int arrayBufferSize = new MessageCreator(sortedArray, listProtocol).createdBuffer().capacity();

        contextLogger.info(String.format("Connecting to server at %s", serverAddress.toString()));
        try (SocketChannel socket = SocketChannel.open()) {
            statsCounter.start();
            ExecutorService reader = Executors.newSingleThreadExecutor();
            ExecutorService writer = Executors.newSingleThreadExecutor();
            socket.connect(serverAddress);
            contextLogger.info("Connected");

            contextLogger.info("Running");
            for (int requestN = 0; requestN < requestsTotal; ++requestN) {
                long iterationStart = System.nanoTime();

                reader.submit(() -> {
                    ByteBuffer arrayBuffer = ByteBuffer.allocate(arrayBufferSize);
                    MessageAccepter accepter = new MessageAccepter(listProtocol);
                    try {
                        contextLogger.info("Waiting for sorted array");
                        while (arrayBuffer.hasRemaining()) {
                            if (socket.read(arrayBuffer) < 0) {
                                throw new IOException("Not all bytes were received");
                            }
                        }

                        statsCounter.pushStat(System.nanoTime() - iterationStart);
                        arrayBuffer.flip();
                        accepter.accept(arrayBuffer);
                        if (accepter.accepted().isEmpty()) {
                            throw new IOException("Received bytes are ill-formatted");
                        }
                        if (!accepter.accepted().get().equals(sortedArray)) {
                            throw new RuntimeException("Array is not sorted");
                        }
                        contextLogger.info("Array received");
                    } catch (IOException e) {
                        contextLogger.handleException(e);
                    }
                });

                writer.submit(() -> {
                    MessageCreator messageCreator = new MessageCreator(arrayToSort, listProtocol);
                    ByteBuffer arrayBuffer = messageCreator.createdBuffer();
                    try {
                        while (arrayBuffer.hasRemaining()) {
                            if (socket.write(arrayBuffer) < 0) {
                                throw new IOException("Not all bytes were sent");
                            }
                        }
                        contextLogger.info("Array sent");
                    } catch (IOException e) {
                        contextLogger.handleException(e);
                    }
                });

                long iterationTimeElapsed = System.nanoTime() - iterationStart;
                long sleepTime = Math.max(0, TimeUnit.MILLISECONDS.toNanos(requestDeltaMs) - iterationTimeElapsed);
                if (requestN + 1 < requestsTotal) {
                    Thread.sleep(TimeUnit.NANOSECONDS.toMillis(sleepTime));
                }
            }

            reader.shutdown();
            writer.shutdown();

            boolean finished = reader.awaitTermination(5, TimeUnit.MINUTES) && writer.awaitTermination(5, TimeUnit.MINUTES);
            if (!finished) {
                throw new RuntimeException("Client's reader and writers are running too long");
            }
        } catch (IOException e) {
            contextLogger.handleException(e);
        } catch (InterruptedException ignored) {
        } finally {
            statsCounter.finish();
        }
        contextLogger.info("Finished");
    }
}

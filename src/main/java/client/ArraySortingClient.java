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
        contextLogger.info("Running");
        List<Integer> arrayToSort = new ArrayList<>();
        List<Integer> sortedArray = new ArrayList<>();
        Random rand = new Random();
        for (int i = 0; i < arrayLength; ++i) {
            Integer randInt = rand.nextInt();
            arrayToSort.add(randInt);
            sortedArray.add(randInt);
        }
        sortedArray.sort(Comparator.naturalOrder());
        statsCounter.start();
        contextLogger.info(String.format("Connecting to server at %s", serverAddress.toString()));
        try (SocketChannel socket = SocketChannel.open()) {
            socket.connect(serverAddress);
            contextLogger.info("Connected");

            for (int requestN = 0; requestN < requestsTotal; ++requestN) {
                Collections.shuffle(arrayToSort, rand);
                MessageCreator messageCreator = new MessageCreator(arrayToSort, listProtocol);
                ByteBuffer arrayBuffer = messageCreator.createdBuffer();
                contextLogger.info(String.format("Sending array %s", arrayToSort.stream().map(Object::toString).collect(Collectors.joining(", "))));
                long startTime = System.nanoTime();
                if (socket.write(arrayBuffer) != arrayBuffer.capacity()) {
                    throw new IOException("Not all bytes were sent");
                }
                contextLogger.info("Array sent");
                arrayBuffer.clear();
                contextLogger.info("Waiting for sorted array");
                if (socket.read(arrayBuffer) != arrayBuffer.capacity()) {
                    throw new IOException("Not all bytes were received");
                }
                long timeElapsed = System.nanoTime() - startTime;
                contextLogger.info(String.format("Time elapsed: %d", timeElapsed));
                statsCounter.pushStat(timeElapsed);
                contextLogger.info("Array received");
                arrayBuffer.flip();
                MessageAccepter messageAccepter = new MessageAccepter(listProtocol);
                messageAccepter.accept(arrayBuffer);
                if (messageAccepter.accepted().isEmpty()) {
                    throw new IOException("Accepted message has illegal format");
                }
                List<Integer> receivedArray = messageAccepter.accepted().get();
                if (!receivedArray.equals(sortedArray)) {
                    throw new IOException("Received array is not sorted version of the sent one");
                }
                long sleepTime = Math.max(0, TimeUnit.MILLISECONDS.toNanos(requestDeltaMs) - timeElapsed);
                if (requestN + 1 < requestsTotal) {
                    Thread.sleep(TimeUnit.NANOSECONDS.toMillis(sleepTime));
                }
            }
        } catch (IOException | InterruptedException e) {
            contextLogger.handleException(e);
        } finally {
            statsCounter.finish();
        }
        contextLogger.info("Finished");
    }
}

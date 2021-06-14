package client;

import logger.ContextLogger;
import protocol.ListTransferringProtocol;
import protocol.PrimitiveListTransferringProtocol;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ClientService {
    private final List<ArraySortingClient> allClients = new ArrayList<>();
    private final SimultaneousJobsStats stats;
    private final ContextLogger logger = new ContextLogger("ClientService", false);

    public static void main(String[] args) {
        try {
            ClientService clientService = new ClientService(
                    100,
                    50,
                    20,
                    50,
                    new PrimitiveListTransferringProtocol(),
                    new InetSocketAddress(8000),
                    false
            );
            System.out.printf("Average request millis: %d",  clientService.runClients());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public ClientService(int clientsTotal,
                         int arrayLength,
                         int clientRequestDelta,
                         int clientRequestsTotal,
                         ListTransferringProtocol listProtocol,
                         InetSocketAddress serverAddress,
                         boolean logInfo) {
        stats = new SimultaneousJobsStats(clientsTotal);
        for (int i = 0; i < clientsTotal; ++i) {
            allClients.add(new ArraySortingClient(
                    arrayLength,
                    clientRequestDelta,
                    clientRequestsTotal,
                    listProtocol,
                    serverAddress,
                    stats,
                    logInfo));
        }
    }

    public long runClients() {
        ExecutorService executor = Executors.newFixedThreadPool(allClients.size());
        logger.info("Starting client service");
        allClients.forEach(executor::submit);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                throw new RuntimeException("ClientService run is too long");
            }
        } catch (InterruptedException e) {
            logger.handleException(e);
        }
        return TimeUnit.NANOSECONDS.toMillis(stats.getAllJobsAverageStat());
    }
}

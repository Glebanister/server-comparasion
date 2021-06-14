package bench;

import bench.input.EnumParameterReader;
import bench.input.IntParameterReader;
import bench.input.ParameterReader;
import bench.input.RangeReader;
import client.ClientService;
import protocol.ListTransferringProtocol;
import protocol.PrimitiveListTransferringProtocol;
import server.ArraySortingServer;
import server.BlockingArraySortingServer;
import server.NonBlockingArraySortingServer;

import static bench.input.EnumParameterReader.option;
import static bench.VaryingParamsIterator.VaryingParameter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ServerBenchmark implements Runnable {
    private final ArraySortingServer server;
    private final ClientService clientService;

    public static void main(String[] args) {
        ListTransferringProtocol protocol = new PrimitiveListTransferringProtocol();
        int port = 8000;

        System.out.println("Server benchmark, choose options");
        ServerSupplierAndName blockingServer = new ServerSupplierAndName(
                () -> new BlockingArraySortingServer(protocol, port, false),
                "Blocking");
        ServerSupplierAndName nonBlockingServer = new ServerSupplierAndName(
                () -> new NonBlockingArraySortingServer(protocol, port, false),
                "Non Blocking");

        ParameterReader<ServerSupplierAndName> serverR = new EnumParameterReader<>(
                "Server architecture",
                Map.of(
                        "b", option(blockingServer, "Blocking"),
                        "n", option(nonBlockingServer, "Non Blocking")
                )
        );
        IntParameterReader arrayLengthR = new IntParameterReader("Array length", 1, true);
        IntParameterReader clientsR = new IntParameterReader("Total clients", 1, true);
        IntParameterReader deltaR = new IntParameterReader("Client queries time delta, ms", 0, true);
        IntParameterReader queriesR = new IntParameterReader("Client queries total", 1, true);
        EnumParameterReader<VaryingParameter> varyingR = new EnumParameterReader<>(
                "Varying parameter",
                Map.of(
                        "n", option(VaryingParameter.AR_LENGTH, "Array length"),
                        "m", option(VaryingParameter.TOTAL_CLIENTS, "Clients total"),
                        "d", option(VaryingParameter.DELTA, "Time delta")
                )
        );

        RangeReader arrayLengthRangeR = new RangeReader("Array length range", 1, true);
        RangeReader clientsRangeR = new RangeReader("Total clients range", 1, true);
        RangeReader deltaRangeR = new RangeReader("Time delta range, ms", 0, true);

        ServerSupplierAndName server = serverR.get();
        int clientQueries = queriesR.get();
        VaryingParameter param = varyingR.get();
        VaryingParamsIterator allParams;
        switch (param) {
            case AR_LENGTH:
                allParams = new VaryingParamsIterator(arrayLengthRangeR.get(), clientsR.get(), deltaR.get());
                break;
            case TOTAL_CLIENTS:
                allParams = new VaryingParamsIterator(arrayLengthR.get(), clientsRangeR.get(), deltaR.get());
                break;
            case DELTA:
                allParams = new VaryingParamsIterator(arrayLengthR.get(), clientsR.get(), deltaRangeR.get());
                break;
            default:
                throw new IllegalStateException("Unexpected varying: " + param);
        }
        System.out.println("Starting benchmark");
        int rounds = 0;
        while (allParams.hasNext()) {
            var params = allParams.next();
            rounds += 1;
            System.out.printf("Round #%d\n", rounds);
            System.out.printf("\t- Architecture: %s\n", server.name);
            System.out.printf("\t- Clients: %d\n", params.clients);
            System.out.printf("\t- Array length: %d\n", params.arrayLength);
            System.out.printf("\t- Time delta: %d\n", params.delta);
            System.out.printf("\t- One client queries: %d\n", clientQueries);
            System.out.flush();
            ServerBenchmark benchmark = new ServerBenchmark(server.serverSupplier.get(), new ClientService(
                    params.clients,
                    params.arrayLength,
                    params.delta,
                    clientQueries,
                    protocol,
                    port,
                    false
            ));
            benchmark.run();
            System.out.printf("\t- Average client waiting time: %d\n\n", benchmark.getAverageClientWaitingTime());
        }
    }

    public ServerBenchmark(ArraySortingServer server, ClientService clientService) {
        this.server = server;
        this.clientService = clientService;
    }

    public long getAverageClientWaitingTime() {
        return clientService.getAverageRun();
    }

    @Override
    public void run() {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(server);
        server.awaitServed();
        clientService.run();
        try {
            server.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        serverExecutor.shutdownNow();
        try {
            if (!serverExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("Server won't close");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static class ServerSupplierAndName {
        public final Supplier<ArraySortingServer> serverSupplier;
        public final String name;

        private ServerSupplierAndName(Supplier<ArraySortingServer> serverSupplier, String name) {
            this.serverSupplier = serverSupplier;
            this.name = name;
        }
    }
}

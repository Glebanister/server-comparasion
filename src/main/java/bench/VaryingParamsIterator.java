package bench;

import bench.input.RangeReader;

import java.util.Iterator;
import java.util.Map;

public class VaryingParamsIterator implements Iterator<VaryingParamsIterator.VaryingParams> {
    private int arrayLength;
    private int clients;
    private int delta;
    private final VaryingParameter param;
    private final RangeReader.Range paramRange;

    public VaryingParamsIterator(RangeReader.Range arrayLength, int clients, int delta) {
        this.arrayLength = arrayLength.min - arrayLength.step;
        this.clients = clients;
        this.delta = delta;
        this.param = VaryingParameter.AR_LENGTH;
        this.paramRange = arrayLength;
    }

    public VaryingParamsIterator(int arrayLength, RangeReader.Range clients, int delta) {
        this.arrayLength = arrayLength;
        this.clients = clients.min - clients.step;
        this.delta = delta;
        this.param = VaryingParameter.TOTAL_CLIENTS;
        this.paramRange = clients;
    }

    public VaryingParamsIterator(int arrayLength, int clients, RangeReader.Range delta) {
        this.arrayLength = arrayLength;
        this.clients = clients;
        this.delta = delta.min - delta.step;
        this.param = VaryingParameter.DELTA;
        this.paramRange = delta;
    }

    @Override
    public boolean hasNext() {
        Map<VaryingParameter, Integer> curValMap = Map.of(
                VaryingParameter.DELTA, delta,
                VaryingParameter.AR_LENGTH, arrayLength,
                VaryingParameter.TOTAL_CLIENTS, clients
        );

        return curValMap.get(param) + paramRange.step <= paramRange.max;
    }

    @Override
    public VaryingParams next() {
        switch (param) {
            case DELTA:
                delta += paramRange.step;
                break;
            case AR_LENGTH:
                arrayLength += paramRange.step;
                break;
            case TOTAL_CLIENTS:
                clients += paramRange.step;
                break;
        }
        return new VaryingParams(arrayLength, clients, delta);
    }

    enum VaryingParameter {
        AR_LENGTH,
        TOTAL_CLIENTS,
        DELTA
    }

    public static class VaryingParams {
        public final int arrayLength;
        public final int clients;
        public final int delta;

        public VaryingParams(int arrayLength, int clients, int delta) {
            this.arrayLength = arrayLength;
            this.clients = clients;
            this.delta = delta;
        }
    }
}

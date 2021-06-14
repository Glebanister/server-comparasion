package bench.input;

import java.util.Optional;

public class RangeReader extends ParameterReader<RangeReader.Range> {
    private final int begin;
    private final int end;

    protected RangeReader(String description, int begin, int end) {
        super(description, String.format("Range: <min> <max> <step>, where %d <= min <= max <= %d", begin, end));
        this.begin = begin;
        this.end = end;
    }

    public RangeReader(String description, int limit, boolean isBegin) {
        super(description,
                String.format("Range: <min> <max> <step>, where %smin <= max%s",
                        isBegin ? String.format("%d <= ", limit) : "",
                        !isBegin ? String.format(" <= %d", limit) : ""));
        if (isBegin) {
            this.begin = limit;
            this.end = Integer.MAX_VALUE;
        } else {
            this.begin = Integer.MIN_VALUE;
            this.end = limit;
        }
    }

    @Override
    public Optional<Range> validate(String input) {
        try {
            String[] parts = input.split(" ");
            if (parts.length != 3) {
                return Optional.empty();
            }
            int min = Integer.parseInt(parts[0]);
            int max = Integer.parseInt(parts[1]);
            int step = Integer.parseInt(parts[2]);
            if (!(begin <= min && min <= max && max <= end)) {
                return Optional.empty();
            }
            return Optional.of(new Range(min, max, step));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static class Range {
        public final int min;
        public final int max;
        public final int step;

        public Range(int min, int max, int step) {
            this.min = min;
            this.max = max;
            this.step = step;
        }
    }
}

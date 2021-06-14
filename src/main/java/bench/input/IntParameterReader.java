package bench.input;

import java.util.Optional;

public class IntParameterReader extends ParameterReader<Integer> {
    private final int lower;
    private final int upper;

    public IntParameterReader(String description, int begin, int end) {
        super(description, String.format("%d - %d", begin, end));
        this.lower = begin;
        this.upper = end;
    }

    public IntParameterReader(String description, int limit, boolean isLimitBegin) {
        super(description, String.format("No %s than %d", isLimitBegin ? "less" : "more", limit));
        if (isLimitBegin) {
            this.lower = limit;
            this.upper = Integer.MAX_VALUE;
        } else {
            this.lower = Integer.MIN_VALUE;
            this.upper = limit;
        }
    }

    @Override
    public Optional<Integer> validate(String input) {
        try {
            int result = Integer.parseInt(input);
            if (result < lower || result > upper) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}

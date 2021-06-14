package bench.input;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class EnumParameterReader<T> extends ParameterReader<T> {
    private final Map<String, Option<T>> options;

    public EnumParameterReader(String description, Map<String, Option<T>> options) {
        super(
                description,
                options.entrySet()
                        .stream()
                        .map(e -> String.format("\t- '%s': %s", e.getKey(), e.getValue().description))
                        .collect(Collectors.joining("\n"))
        );
        this.options = options;
    }

    public static <T> Option<T> option(T option, String description) {
        return new Option<>(option, description);
    }

    @Override
    public Optional<T> validate(String input) {
        if (options.containsKey(input)) {
            return Optional.ofNullable(options.get(input).option);
        }
        return Optional.empty();
    }

    public static class Option<T> {
        public final T option;
        public final String description;

        public Option(T option, String description) {
            this.option = option;
            this.description = description;
        }
    }
}

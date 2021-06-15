package bench.input;

import java.io.InputStream;
import java.util.Optional;
import java.util.Scanner;

public abstract class ParameterReader<T> {
    private final String description;
    private final String options;

    protected ParameterReader(String description, String options) {
        this.description = description;
        this.options = options;
    }

    public abstract Optional<T> validate(String input);

    public T get(InputStream is) {
        System.out.println(description);
        System.out.println(options);
        Scanner scanner = new Scanner(is);
        String input = readLine(scanner);
        Optional<T> result = validate(input);
        while (result.isEmpty()) {
            System.out.println("Invalid input");
            System.out.println(options);
            input = readLine(scanner);
            result = validate(input);
        }
        return result.get();
    }

    private String readLine(Scanner scanner) {
        String input = scanner.nextLine().trim();
        while (input.isEmpty()) {
            input = scanner.nextLine().trim();
        }
        return input;
    }
}

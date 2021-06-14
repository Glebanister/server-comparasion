package logger;

import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ContextLogger {
    private final String context;
    private final boolean actuallyLogInfo;

    public ContextLogger(String context, boolean actuallyLogInfo) {
        this.context = context;
        this.actuallyLogInfo = actuallyLogInfo;
    }

    public void handleException(Throwable e) {
        Logger.getGlobal().warning(String.format("%s: Error: %s\n Stack trace: %s\n",
                context,
                e,
                Arrays.stream(e.getStackTrace())
                        .map(Object::toString)
                        .collect(Collectors.joining("\n"))));
    }

    public void info(String message) {
        if (actuallyLogInfo) {
            Logger.getGlobal().info(String.format("%s: %s", context, message));
        }
    }
}

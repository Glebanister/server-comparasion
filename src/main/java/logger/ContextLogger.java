package logger;

import java.util.logging.Logger;

public class ContextLogger {
    private final String context;
    private final boolean actuallyLogInfo;

    public ContextLogger(String context, boolean actuallyLogInfo) {
        this.context = context;
        this.actuallyLogInfo = actuallyLogInfo;
    }

    public void handleException(Throwable e) {
        Logger.getGlobal().warning(String.format("%s: %s", context, e));
    }

    public void info(String message) {
        if (actuallyLogInfo) {
            Logger.getGlobal().info(String.format("%s: %s", context, message));
        }
    }
}

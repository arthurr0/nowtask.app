package app.nowtask.shared;

public final class ActorContext {

    private static final ThreadLocal<String> LABEL = new ThreadLocal<>();

    private ActorContext() {
    }

    public static String currentLabel() {
        return LABEL.get();
    }

    public static void set(String label) {
        LABEL.set(label);
    }

    public static void clear() {
        LABEL.remove();
    }

    public static void runAs(String label, Runnable work) {
        String previous = LABEL.get();
        LABEL.set(label);
        try {
            work.run();
        } finally {
            if (previous == null) {
                LABEL.remove();
            } else {
                LABEL.set(previous);
            }
        }
    }
}

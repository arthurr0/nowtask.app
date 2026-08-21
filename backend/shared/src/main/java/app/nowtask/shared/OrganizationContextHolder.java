package app.nowtask.shared;

import java.util.UUID;

public final class OrganizationContextHolder {

    private static final ThreadLocal<OrganizationContext> CONTEXT = new ThreadLocal<>();

    private OrganizationContextHolder() {
    }

    public static OrganizationContext current() {
        OrganizationContext context = CONTEXT.get();
        if (context == null) {
            throw new NoOrganizationException();
        }
        return context;
    }

    public static OrganizationContext currentOrNull() {
        return CONTEXT.get();
    }

    public static UUID currentOrganizationId() {
        return current().requireOrganizationId();
    }

    public static void set(OrganizationContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static void runAs(OrganizationContext context, Runnable work) {
        OrganizationContext previous = CONTEXT.get();
        CONTEXT.set(context);
        try {
            work.run();
        } finally {
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }
}

package app.nowtask.identity;

final class InstancePaging {

    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 200;

    private InstancePaging() {
    }

    static int page(int page) {
        return Math.max(page, 0);
    }

    static int size(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    static String pattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

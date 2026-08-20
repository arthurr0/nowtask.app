package app.nowtask.identity;

final class UserNames {

    private UserNames() {
    }

    static String shortNameOf(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length < 2) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    static String initialsOf(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length < 2) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }
}

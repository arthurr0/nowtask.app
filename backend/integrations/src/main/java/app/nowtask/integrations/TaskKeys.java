package app.nowtask.integrations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TaskKeys {

    private static final Pattern PATTERN = Pattern.compile("\\b([A-Z][A-Z0-9]{0,15})-(\\d{1,9})\\b");

    private TaskKeys() {
    }

    static String projectOf(String taskKey) {
        if (taskKey == null) {
            return "";
        }

        int dash = taskKey.lastIndexOf('-');
        return dash <= 0 ? "" : taskKey.substring(0, dash);
    }

    static List<String> find(String... sources) {
        Set<String> keys = new LinkedHashSet<>();

        for (String source : sources) {
            if (source == null || source.isBlank()) {
                continue;
            }

            Matcher matcher = PATTERN.matcher(source.toUpperCase(java.util.Locale.ROOT));
            while (matcher.find()) {
                keys.add(matcher.group());
            }
        }

        return List.copyOf(keys);
    }
}

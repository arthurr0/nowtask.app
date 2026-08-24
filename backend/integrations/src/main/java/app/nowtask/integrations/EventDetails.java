package app.nowtask.integrations;

import java.util.List;

record EventDetails(
        String title,
        String assignee,
        String priority,
        String due,
        List<String> labels,
        String actor) {

    static EventDetails of(String actor) {
        return new EventDetails(null, null, null, null, List.of(), actor);
    }
}

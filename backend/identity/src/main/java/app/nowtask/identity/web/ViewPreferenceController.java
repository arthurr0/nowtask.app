package app.nowtask.identity.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.identity.api.ViewPreferences;
import app.nowtask.shared.TaskOpenMode;
import app.nowtask.shared.TaskView;

@RestController
@RequestMapping("/api/me")
class ViewPreferenceController {

    private final ViewPreferences preferences;

    ViewPreferenceController(ViewPreferences preferences) {
        this.preferences = preferences;
    }

    record DefaultView(TaskView view) {
    }

    record OpenMode(TaskOpenMode mode) {
    }

    @GetMapping("/default-view")
    DefaultView defaultView() {
        return new DefaultView(preferences.currentDefaultView());
    }

    @PutMapping("/default-view")
    DefaultView replaceDefaultView(@RequestBody DefaultView request) {
        return new DefaultView(preferences.replaceDefaultView(request.view()));
    }

    @GetMapping("/task-open-mode")
    OpenMode taskOpenMode() {
        return new OpenMode(preferences.currentTaskOpenMode());
    }

    @PutMapping("/task-open-mode")
    OpenMode replaceTaskOpenMode(@RequestBody OpenMode request) {
        return new OpenMode(preferences.replaceTaskOpenMode(request.mode()));
    }
}

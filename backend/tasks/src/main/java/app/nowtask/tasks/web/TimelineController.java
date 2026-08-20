package app.nowtask.tasks.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.tasks.api.TaskViews.ScheduledTask;
import app.nowtask.tasks.api.Tasks;

@RestController
@RequestMapping("/api/timeline")
class TimelineController {

    private final Tasks tasks;

    TimelineController(Tasks tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    List<ScheduledTask> scheduled() {
        return tasks.scheduled();
    }
}

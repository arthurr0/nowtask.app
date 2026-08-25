package app.nowtask.integrations.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.integrations.GitHubTaskService;
import app.nowtask.integrations.api.GitHubTasks.TaskLink;

@RestController
@RequestMapping("/api/integrations/github/task")
class GitHubTaskController {

    private final GitHubTaskService tasks;

    GitHubTaskController(GitHubTaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/{taskKey}")
    List<TaskLink> links(@PathVariable String taskKey) {
        return tasks.linksOf(taskKey);
    }
}

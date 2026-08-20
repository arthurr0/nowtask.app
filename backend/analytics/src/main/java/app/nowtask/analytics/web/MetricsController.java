package app.nowtask.analytics.web;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.analytics.MetricsService;

@RestController
@RequestMapping("/api/metrics")
class MetricsController {
    private final MetricsService metrics;

    MetricsController(MetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/overview")
    MetricsService.Overview overview(@RequestParam(name = "days", defaultValue = "7") int days) {
        return metrics.overview(days);
    }
}

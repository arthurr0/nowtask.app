package app.nowtask.workspace.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.workspace.api.PresetViews.PresetDetailView;
import app.nowtask.workspace.api.PresetViews.PresetSummaryView;
import app.nowtask.workspace.api.Presets;

@RestController
@RequestMapping("/api/presets")
class PresetController {

    private final Presets presets;

    PresetController(Presets presets) {
        this.presets = presets;
    }

    @GetMapping
    List<PresetSummaryView> catalog() {
        return presets.catalog();
    }

    @GetMapping("/{code}")
    PresetDetailView detail(@PathVariable String code) {
        return presets.detail(code);
    }
}

package app.nowtask.web;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class MetaController {
    private final String version;

    MetaController(@Value("${nowtask.version:0.1.0-SNAPSHOT}") String version) {
        this.version = version;
    }

    @GetMapping("/meta")
    Map<String, Object> meta() {
        return Map.of(
                "name", "nowtask",
                "version", version,
                "defaultLanguage", "en",
                "languages", List.of("pl", "en", "de"));
    }
}

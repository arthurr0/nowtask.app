package app.nowtask.exports.web;

import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.exports.ExportService;
import app.nowtask.exports.ExportService.ExportFile;
import app.nowtask.shared.TaskQuery;

@RestController
@RequestMapping("/api/export")
class ExportController {

    private final ExportService exports;

    ExportController(ExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/tasks")
    ResponseEntity<byte[]> tasks(
            @ModelAttribute TaskQuery query,
            @RequestParam(name = "format", defaultValue = "csv") String format) {
        return file(exports.tasks(query, format));
    }

    @GetMapping("/rule-runs")
    ResponseEntity<byte[]> ruleRuns(
            @RequestParam(name = "ruleId", required = false) UUID ruleId,
            @RequestParam(name = "format", defaultValue = "csv") String format) {
        return file(exports.ruleRuns(ruleId, format));
    }

    private ResponseEntity<byte[]> file(ExportFile export) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename()).build().toString())
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.content());
    }
}

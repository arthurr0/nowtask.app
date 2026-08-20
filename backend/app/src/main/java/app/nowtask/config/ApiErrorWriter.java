package app.nowtask.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

final class ApiErrorWriter {
    private final ObjectMapper json;

    ApiErrorWriter(ObjectMapper json) {
        this.json = json;
    }

    void write(HttpServletResponse response, int status, String message, Map<String, Object> extra)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("message", message);
        body.put("at", Instant.now().toString());
        body.putAll(extra);

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(body));
    }
}

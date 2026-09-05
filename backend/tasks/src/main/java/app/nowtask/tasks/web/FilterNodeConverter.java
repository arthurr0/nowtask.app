package app.nowtask.tasks.web;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import app.nowtask.shared.FilterNode;

@Component
class FilterNodeConverter implements Converter<String, FilterNode> {

    private final ObjectMapper mapper;

    FilterNodeConverter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FilterNode convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(source, FilterNode.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid filter: " + e.getMessage(), e);
        }
    }
}

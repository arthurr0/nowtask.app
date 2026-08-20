package app.nowtask.shared;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record PatchBody(Map<String, Object> values) {

    public PatchBody(Map<String, Object> values) {
        this.values = values == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public boolean has(String field) {
        return values.containsKey(field);
    }

    public Object raw(String field) {
        return values.get(field);
    }

    public String text(String field) {
        Object value = values.get(field);
        return value == null ? null : value.toString();
    }

    public UUID id(String field) {
        String value = text(field);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Field " + field + " is not an identifier: " + value);
        }
    }

    public Integer number(String field) {
        Object value = values.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Number numeric) {
            return numeric.intValue();
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field " + field + " is not a number: " + value);
        }
    }

    public Boolean flag(String field) {
        Object value = values.get(field);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean logical) {
            return logical;
        }
        return Boolean.valueOf(value.toString());
    }

    public LocalDate date(String field) {
        String value = text(field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Field " + field + " is not a date: " + value);
        }
    }
}

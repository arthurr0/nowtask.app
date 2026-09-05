package app.nowtask.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FilterNode(String field, String op, List<String> values, String join, List<FilterNode> conditions) {

    public static final String AND = "and";
    public static final String OR = "or";

    public static FilterNode condition(String field, String op, List<String> values) {
        return new FilterNode(field, op, values == null ? List.of() : List.copyOf(values), null, null);
    }

    public static FilterNode condition(String field, String op, String value) {
        return condition(field, op, List.of(value));
    }

    public static FilterNode group(String join, List<FilterNode> conditions) {
        return new FilterNode(null, null, null, OR.equalsIgnoreCase(join) ? OR : AND,
                conditions == null ? List.of() : List.copyOf(conditions));
    }

    public static FilterNode all() {
        return group(AND, List.of());
    }

    @JsonIgnore
    public boolean isGroup() {
        return conditions != null;
    }

    @JsonIgnore
    public boolean isOr() {
        return OR.equalsIgnoreCase(join);
    }

    public List<String> valueList() {
        return values == null ? List.of() : values;
    }

    public List<FilterNode> children() {
        return conditions == null ? List.of() : conditions;
    }

    @JsonIgnore
    public boolean isBlank() {
        if (isGroup()) {
            return children().stream().allMatch(FilterNode::isBlank);
        }
        return field == null || field.isBlank();
    }

    public FilterNode prepend(List<FilterNode> extra) {
        if (extra.isEmpty()) {
            return this;
        }
        List<FilterNode> merged = new ArrayList<>(extra);
        if (isGroup() && !isOr()) {
            merged.addAll(children());
        } else if (!isBlank()) {
            merged.add(this);
        }
        return group(AND, merged);
    }
}

package com.gagent.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Parameters(
    String type,
    Map<String, Property> properties,
    List<String> required
) {
    public static Parameters object(Map<String, Property> properties, List<String> required) {
        return new Parameters("object", properties, required);
    }
}

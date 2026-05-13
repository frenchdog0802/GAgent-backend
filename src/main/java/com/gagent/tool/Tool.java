package com.gagent.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Tool(String type, Function function) {
    public static Tool function(Function function) {
        return new Tool("function", function);
    }
}

package com.gagent.tool.executor;

import java.util.Map;

public interface GagentToolExecutor {
    String getFunctionName();
    String execute(String userId, Map<String, Object> arguments);
}

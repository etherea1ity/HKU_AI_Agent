package com.hku.hkuaiagent.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Wrapper around an existing {@link ToolCallbackProvider} that filters out tool callbacks
 * whose names do not satisfy the supplied predicate.
 */
public class FilteredToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;
    private final Predicate<String> allowPredicate;

    public FilteredToolCallbackProvider(ToolCallbackProvider delegate, Predicate<String> allowPredicate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.allowPredicate = Objects.requireNonNull(allowPredicate, "allowPredicate");
    }

    @Override
    public @org.springframework.lang.NonNull ToolCallback[] getToolCallbacks() {
        ToolCallback[] original = delegate.getToolCallbacks();
        if (original == null || original.length == 0) {
            return new ToolCallback[0];
        }
            ToolCallback[] filtered = Arrays.stream(original)
            .filter(callback -> allowPredicate.test(extractName(callback)))
            .toArray(ToolCallback[]::new);
            return Objects.requireNonNull(filtered);
    }

    private String extractName(ToolCallback callback) {
        if (callback == null) {
            return null;
        }
        try {
            Method toolDefinitionMethod = callback.getClass().getMethod("toolDefinition");
            Object toolDefinition = toolDefinitionMethod.invoke(callback);
            if (toolDefinition != null) {
                Method nameMethod = toolDefinition.getClass().getMethod("name");
                Object nameValue = nameMethod.invoke(toolDefinition);
                if (nameValue != null) {
                    return nameValue.toString();
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall back to the callback's string representation
        }
        return callback.toString();
    }
}

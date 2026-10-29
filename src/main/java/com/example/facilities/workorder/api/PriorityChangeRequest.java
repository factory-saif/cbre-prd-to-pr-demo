package com.example.facilities.workorder.api;

import com.example.facilities.workorder.Priority;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;

public record PriorityChangeRequest(
        @NotNull @JsonDeserialize(using = PriorityChangeRequest.NamedPriorityDeserializer.class) Priority priority,
        @NotBlank @Size(max = 500) String reason) {

    // Jackson maps numeric enum ordinals by default (both 0 and "0" would mean URGENT).
    // Only the documented names are accepted; anything else becomes a 400.
    static class NamedPriorityDeserializer extends StdDeserializer<Priority> {

        NamedPriorityDeserializer() {
            super(Priority.class);
        }

        @Override
        public Priority deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (Priority) context.handleUnexpectedToken(Priority.class, parser);
            }
            String name = parser.getText();
            for (Priority priority : Priority.values()) {
                if (priority.name().equals(name)) {
                    return priority;
                }
            }
            return (Priority) context.handleWeirdStringValue(Priority.class, name, "not a priority name");
        }
    }
}

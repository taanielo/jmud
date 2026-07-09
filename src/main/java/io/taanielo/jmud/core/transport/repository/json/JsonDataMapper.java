package io.taanielo.jmud.core.transport.repository.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Factory for the snake-case {@link ObjectMapper} used to read ferry definition files.
 */
public final class JsonDataMapper {
    private JsonDataMapper() {
    }

    /**
     * Creates a configured mapper matching the project's JSON conventions (snake_case fields,
     * indented output, strict on unknown properties).
     *
     * @return a configured object mapper
     */
    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}

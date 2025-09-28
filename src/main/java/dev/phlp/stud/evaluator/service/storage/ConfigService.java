package dev.phlp.stud.evaluator.service.storage;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phlp.stud.evaluator.model.config.EvaluationConfig;
import dev.phlp.stud.evaluator.util.JsonMapperFactory;

public class ConfigService {
    private final ObjectMapper objectMapper;

    public ConfigService() {
        this(JsonMapperFactory.createDefaultMapper());
    }

    public ConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvaluationConfig load(File file) throws IOException {
        return objectMapper.readValue(file, EvaluationConfig.class);
    }

    public void save(EvaluationConfig config, File file) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, config);
    }

    public EvaluationConfig clone(EvaluationConfig original) throws IOException {
        try {
            String json = objectMapper.writeValueAsString(original);
            return objectMapper.readValue(json, EvaluationConfig.class);
        } catch (JsonProcessingException ex) {
            throw new IOException("Konfiguration konnte nicht kopiert werden", ex);
        }
    }
}

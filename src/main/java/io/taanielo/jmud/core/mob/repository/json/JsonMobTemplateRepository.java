package io.taanielo.jmud.core.mob.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.mob.MobRepositoryException;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.MobTemplateRepository;
import io.taanielo.jmud.core.mob.dto.MobTemplateDto;
import io.taanielo.jmud.core.mob.dto.MobTemplateDtoMapper;

public class JsonMobTemplateRepository implements MobTemplateRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String MOBS_DIR = "mobs";

    private final ObjectMapper objectMapper;
    private final MobTemplateDtoMapper mapper;
    private final Path mobsDirPath;

    public JsonMobTemplateRepository() throws MobRepositoryException {
        this(Path.of("data"));
    }

    public JsonMobTemplateRepository(Path dataRoot) throws MobRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.mapper = new MobTemplateDtoMapper();
        this.mobsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(MOBS_DIR);
        ensureDirectory(mobsDirPath);
    }

    @Override
    public List<MobTemplate> findAll() throws MobRepositoryException {
        List<MobTemplate> templates = new ArrayList<>();
        try (var stream = Files.list(mobsDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                MobTemplateDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new MobRepositoryException(
                        "Unsupported mob schema version " + dto.schemaVersion() + " in " + path);
                }
                try {
                    templates.add(mapper.toDomain(dto));
                } catch (IllegalArgumentException e) {
                    throw new MobRepositoryException("Invalid mob data in " + path + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new MobRepositoryException("Failed to list mob data files: " + e.getMessage(), e);
        }
        return List.copyOf(templates);
    }

    private MobTemplateDto readDto(Path path) throws MobRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), MobTemplateDto.class);
        } catch (IOException e) {
            throw new MobRepositoryException("Failed to read mob data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws MobRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new MobRepositoryException("Failed to create mobs directory " + path, e);
        }
    }
}

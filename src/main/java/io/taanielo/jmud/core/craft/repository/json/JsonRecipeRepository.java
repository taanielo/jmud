package io.taanielo.jmud.core.craft.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.craft.Recipe;
import io.taanielo.jmud.core.craft.RecipeRepository;
import io.taanielo.jmud.core.craft.RecipeRepositoryException;
import io.taanielo.jmud.core.craft.dto.RecipeDto;
import io.taanielo.jmud.core.craft.dto.RecipeMapper;

/**
 * Loads {@link Recipe} definitions from {@code data/recipes/*.json} files.
 *
 * <p>All recipes are eagerly loaded and cached on first access.
 */
@Slf4j
public class JsonRecipeRepository implements RecipeRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String RECIPES_DIR = "recipes";

    private final ObjectMapper objectMapper;
    private final RecipeMapper recipeMapper;
    private final Path recipesDirPath;
    @Nullable
    private List<Recipe> cache;

    public JsonRecipeRepository() throws RecipeRepositoryException {
        this(Path.of("data"));
    }

    public JsonRecipeRepository(Path dataRoot) throws RecipeRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.recipeMapper = new RecipeMapper();
        this.recipesDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(RECIPES_DIR);
        ensureDirectory(recipesDirPath);
    }

    @Override
    public List<Recipe> findAll() throws RecipeRepositoryException {
        List<Recipe> loaded = cache;
        if (loaded == null) {
            loaded = load();
            cache = loaded;
        }
        return loaded;
    }

    private List<Recipe> load() throws RecipeRepositoryException {
        List<Recipe> recipes = new ArrayList<>();
        try (var stream = Files.list(recipesDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                RecipeDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new RecipeRepositoryException(
                        "Unsupported recipe schema version " + dto.schemaVersion() + " in " + path);
                }
                try {
                    recipes.add(recipeMapper.toDomain(dto));
                } catch (IllegalArgumentException e) {
                    throw new RecipeRepositoryException(
                        "Invalid recipe data in " + path + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new RecipeRepositoryException("Failed to list recipe data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} recipe(s) from {}", recipes.size(), recipesDirPath);
        return List.copyOf(recipes);
    }

    private RecipeDto readDto(Path path) throws RecipeRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), RecipeDto.class);
        } catch (IOException e) {
            throw new RecipeRepositoryException(
                "Failed to read recipe data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws RecipeRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RecipeRepositoryException("Failed to create recipes directory " + path, e);
        }
    }
}

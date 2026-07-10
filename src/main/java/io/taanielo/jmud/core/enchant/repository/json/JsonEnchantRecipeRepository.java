package io.taanielo.jmud.core.enchant.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.craft.RecipeMaterial;
import io.taanielo.jmud.core.craft.dto.RecipeMaterialDto;
import io.taanielo.jmud.core.enchant.EnchantRecipe;
import io.taanielo.jmud.core.enchant.EnchantRecipeRepository;
import io.taanielo.jmud.core.enchant.EnchantRecipeRepositoryException;
import io.taanielo.jmud.core.world.AffixId;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Loads {@link EnchantRecipe} definitions from {@code data/recipes/enchanting/*.json} files.
 *
 * <p>All recipes are eagerly loaded and cached on first access.
 */
@Slf4j
public class JsonEnchantRecipeRepository implements EnchantRecipeRepository {

    private static final int SCHEMA_VERSION = 1;
    private static final String RECIPES_SUBDIR = "recipes/enchanting";

    private final ObjectMapper objectMapper;
    private final Path recipesDirPath;
    @Nullable
    private List<EnchantRecipe> cache;

    public JsonEnchantRecipeRepository() throws EnchantRecipeRepositoryException {
        this(Path.of("data"));
    }

    /**
     * Creates a repository loading enchant recipes from {@code <dataRoot>/recipes/enchanting}.
     *
     * @param dataRoot the data root directory
     * @throws EnchantRecipeRepositoryException if the recipes directory cannot be created
     */
    public JsonEnchantRecipeRepository(Path dataRoot) throws EnchantRecipeRepositoryException {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.recipesDirPath = Objects.requireNonNull(dataRoot, "Data root is required")
            .resolve(RECIPES_SUBDIR);
        ensureDirectory(recipesDirPath);
    }

    @Override
    public List<EnchantRecipe> findAll() throws EnchantRecipeRepositoryException {
        List<EnchantRecipe> loaded = cache;
        if (loaded == null) {
            loaded = load();
            cache = loaded;
        }
        return loaded;
    }

    private List<EnchantRecipe> load() throws EnchantRecipeRepositoryException {
        List<EnchantRecipe> recipes = new ArrayList<>();
        try (var stream = Files.list(recipesDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                EnchantRecipeDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION) {
                    throw new EnchantRecipeRepositoryException(
                        "Unsupported enchant recipe schema version " + dto.schemaVersion() + " in " + path);
                }
                try {
                    recipes.add(toDomain(dto));
                } catch (IllegalArgumentException e) {
                    throw new EnchantRecipeRepositoryException(
                        "Invalid enchant recipe data in " + path + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new EnchantRecipeRepositoryException(
                "Failed to list enchant recipe data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} enchant recipe(s) from {}", recipes.size(), recipesDirPath);
        return List.copyOf(recipes);
    }

    private EnchantRecipe toDomain(EnchantRecipeDto dto) {
        String id = requireText(dto.id(), "id");
        String affix = requireText(dto.affix(), "affix");
        int goldCost = dto.goldCost() == null ? 0 : dto.goldCost();
        List<RecipeMaterialDto> materialDtos =
            Objects.requireNonNull(dto.materials(), "Enchant recipe '" + id + "' requires materials");
        List<RecipeMaterial> materials = materialDtos.stream()
            .map(m -> toMaterial(id, m))
            .toList();
        return new EnchantRecipe(id, AffixId.of(affix), goldCost, materials);
    }

    private RecipeMaterial toMaterial(String recipeId, RecipeMaterialDto dto) {
        String item = requireText(dto.item(), "material item in enchant recipe '" + recipeId + "'");
        int quantity = dto.quantity() == null ? 1 : dto.quantity();
        return new RecipeMaterial(ItemId.of(item), quantity);
    }

    private EnchantRecipeDto readDto(Path path) throws EnchantRecipeRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), EnchantRecipeDto.class);
        } catch (IOException e) {
            throw new EnchantRecipeRepositoryException(
                "Failed to read enchant recipe data from " + path + ": " + e.getMessage(), e);
        }
    }

    private static String requireText(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enchant recipe field '" + field + "' is required");
        }
        return value;
    }

    private void ensureDirectory(Path path) throws EnchantRecipeRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new EnchantRecipeRepositoryException("Failed to create enchant recipes directory " + path, e);
        }
    }
}

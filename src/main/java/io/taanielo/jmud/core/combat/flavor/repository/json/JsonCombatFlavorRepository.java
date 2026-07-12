package io.taanielo.jmud.core.combat.flavor.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.taanielo.jmud.core.combat.flavor.CombatFlavor;
import io.taanielo.jmud.core.combat.flavor.DamageVerbTable;
import io.taanielo.jmud.core.combat.flavor.TargetConditionTable;
import io.taanielo.jmud.core.combat.flavor.dto.CombatFlavorMapper;
import io.taanielo.jmud.core.combat.flavor.dto.ConditionsDto;
import io.taanielo.jmud.core.combat.flavor.dto.DamageVerbsDto;
import io.taanielo.jmud.core.combat.flavor.repository.CombatFlavorRepository;
import io.taanielo.jmud.core.combat.repository.json.JsonDataMapper;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Loads the combat-flavor word tables from {@code data/combat/damage-verbs.json} and
 * {@code data/combat/conditions.json}. Constructed only by the composition root (AGENTS.md §3.3).
 */
public class JsonCombatFlavorRepository implements CombatFlavorRepository {
    private static final String COMBAT_DIR = "combat";
    private static final String DAMAGE_VERBS_FILE = "damage-verbs.json";
    private static final String CONDITIONS_FILE = "conditions.json";

    private final ObjectMapper objectMapper;
    private final CombatFlavorMapper mapper;
    private final Path combatDirPath;

    public JsonCombatFlavorRepository() {
        this(Path.of("data"));
    }

    public JsonCombatFlavorRepository(Path dataRoot) {
        this.objectMapper = JsonDataMapper.create();
        this.mapper = new CombatFlavorMapper();
        this.combatDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(COMBAT_DIR);
    }

    @Override
    public CombatFlavor load() throws RepositoryException {
        DamageVerbTable verbs = mapper.toDamageVerbTable(readVerbs());
        TargetConditionTable conditions = mapper.toConditionTable(readConditions());
        return new CombatFlavor(verbs, conditions);
    }

    private DamageVerbsDto readVerbs() throws RepositoryException {
        Path path = combatDirPath.resolve(DAMAGE_VERBS_FILE);
        requireFile(path);
        try {
            return objectMapper.readValue(path.toFile(), DamageVerbsDto.class);
        } catch (IOException e) {
            throw new RepositoryException("Failed to read damage verbs from " + path + ": " + e.getMessage(), e);
        }
    }

    private ConditionsDto readConditions() throws RepositoryException {
        Path path = combatDirPath.resolve(CONDITIONS_FILE);
        requireFile(path);
        try {
            return objectMapper.readValue(path.toFile(), ConditionsDto.class);
        } catch (IOException e) {
            throw new RepositoryException("Failed to read conditions from " + path + ": " + e.getMessage(), e);
        }
    }

    private void requireFile(Path path) throws RepositoryException {
        if (!Files.exists(path)) {
            throw new RepositoryException("Missing combat flavor file " + path);
        }
    }
}

package io.taanielo.jmud.core.dialogue.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.dialogue.DialogueId;
import io.taanielo.jmud.core.dialogue.DialogueNode;
import io.taanielo.jmud.core.dialogue.DialogueRepository;
import io.taanielo.jmud.core.dialogue.DialogueRepositoryException;
import io.taanielo.jmud.core.dialogue.DialogueResponse;
import io.taanielo.jmud.core.dialogue.DialogueTree;

/**
 * Loads {@link DialogueTree} definitions from {@code data/dialogues/*.json} files.
 *
 * <p>All dialogue trees are eagerly loaded and cached at construction time so that the {@code TALK}
 * and {@code RESPOND} commands never perform disk I/O on the tick thread (AGENTS.md §5). Each file's
 * {@code responses[].target} is validated to reference a defined node, failing fast on bad data.
 */
@Slf4j
public class JsonDialogueRepository implements DialogueRepository {

    private static final int SCHEMA_VERSION_BASE = 1;
    private static final int SCHEMA_VERSION_QUEST_GRANT = 2;
    private static final String DIALOGUES_DIR = "dialogues";

    private final ObjectMapper objectMapper;
    private final Path dialoguesDirPath;
    private final Map<DialogueId, DialogueTree> cache;

    public JsonDialogueRepository() throws DialogueRepositoryException {
        this(Path.of("data"));
    }

    public JsonDialogueRepository(Path dataRoot) throws DialogueRepositoryException {
        this.objectMapper = JsonDataMapper.create();
        this.dialoguesDirPath =
            Objects.requireNonNull(dataRoot, "Data root is required").resolve(DIALOGUES_DIR);
        ensureDirectory(dialoguesDirPath);
        this.cache = load();
    }

    @Override
    public List<DialogueTree> findAll() {
        return List.copyOf(cache.values());
    }

    @Override
    public Optional<DialogueTree> findById(DialogueId id) {
        Objects.requireNonNull(id, "id is required");
        return Optional.ofNullable(cache.get(id));
    }

    private Map<DialogueId, DialogueTree> load() throws DialogueRepositoryException {
        Map<DialogueId, DialogueTree> loaded = new LinkedHashMap<>();
        try (var stream = Files.list(dialoguesDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                DialogueTreeDto dto = readDto(path);
                if (dto.schemaVersion() != SCHEMA_VERSION_BASE
                        && dto.schemaVersion() != SCHEMA_VERSION_QUEST_GRANT) {
                    throw new DialogueRepositoryException(
                        "Unsupported dialogue schema version " + dto.schemaVersion() + " in " + path);
                }
                DialogueTree tree = toDomain(dto, path);
                if (loaded.putIfAbsent(tree.id(), tree) != null) {
                    throw new DialogueRepositoryException(
                        "Duplicate dialogue id '" + tree.id().value() + "' in " + path);
                }
            }
        } catch (IOException e) {
            throw new DialogueRepositoryException("Failed to list dialogue data files: " + e.getMessage(), e);
        }
        log.info("Loaded {} dialogue tree(s) from {}", loaded.size(), dialoguesDirPath);
        return loaded;
    }

    private DialogueTree toDomain(DialogueTreeDto dto, Path source) throws DialogueRepositoryException {
        String id = dto.id();
        String npcId = dto.npcId();
        String startNode = dto.startNode();
        Map<String, DialogueNodeDto> dtoNodes = dto.nodes();
        if (id == null || npcId == null || startNode == null || dtoNodes == null) {
            throw new DialogueRepositoryException(
                "Dialogue data in " + source + " is missing id, npc_id, start_node, or nodes");
        }
        Map<String, DialogueNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, DialogueNodeDto> entry : dtoNodes.entrySet()) {
            String nodeId = entry.getKey();
            DialogueNodeDto nodeDto = entry.getValue();
            if (nodeDto == null || nodeDto.text() == null) {
                throw new DialogueRepositoryException(
                    "Dialogue node '" + nodeId + "' in " + source + " is missing text");
            }
            String text = nodeDto.text();
            List<DialogueResponse> responses = new ArrayList<>();
            List<DialogueResponseDto> responseDtos = nodeDto.responses();
            if (responseDtos != null) {
                for (DialogueResponseDto r : responseDtos) {
                    if (r == null || r.text() == null || r.target() == null) {
                        throw new DialogueRepositoryException(
                            "Dialogue response in node '" + nodeId + "' of " + source
                                + " is missing text or target");
                    }
                    responses.add(new DialogueResponse(r.text(), r.target(), r.grantQuestId()));
                }
            }
            nodes.put(nodeId, new DialogueNode(nodeId, text, responses));
        }
        try {
            DialogueTree tree = new DialogueTree(DialogueId.of(id), npcId, startNode, nodes);
            validateTargets(tree, source);
            return tree;
        } catch (IllegalArgumentException e) {
            throw new DialogueRepositoryException(
                "Invalid dialogue data in " + source + ": " + e.getMessage(), e);
        }
    }

    private void validateTargets(DialogueTree tree, Path source) throws DialogueRepositoryException {
        for (DialogueNode node : tree.nodes().values()) {
            for (DialogueResponse response : node.responses()) {
                if (tree.node(response.target()).isEmpty()) {
                    throw new DialogueRepositoryException(
                        "Dialogue node '" + node.id() + "' in " + source
                            + " references undefined target node '" + response.target() + "'");
                }
            }
        }
    }

    private DialogueTreeDto readDto(Path path) throws DialogueRepositoryException {
        try {
            return objectMapper.readValue(path.toFile(), DialogueTreeDto.class);
        } catch (IOException e) {
            throw new DialogueRepositoryException(
                "Failed to read dialogue data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) throws DialogueRepositoryException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new DialogueRepositoryException("Failed to create dialogues directory " + path, e);
        }
    }
}

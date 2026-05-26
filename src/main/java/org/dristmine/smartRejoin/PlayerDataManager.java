package org.dristmine.smartRejoin;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages storing and retrieving the last server a player was on.
 * This implementation persists data to data.yml.
 */
public class PlayerDataManager {

    private final Logger logger;
    private final Path dataFile;
    private CommentedConfigurationNode config;
    private YamlConfigurationLoader loader;
    private final Map<UUID, String> lastServerMap = new ConcurrentHashMap<>();

    public PlayerDataManager(SmartRejoin plugin, Path dataDirectory, Logger logger) {
        this.logger = logger;
        this.dataFile = dataDirectory.resolve("data.yml");
        loader = YamlConfigurationLoader.builder().path(dataFile).build();

        try {
            if (Files.notExists(dataDirectory)) {
                Files.createDirectory(dataDirectory);
                if (Files.notExists(dataFile)) {
                    try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream("data.yml")) {
                        Files.copy(stream, dataFile);
                    }
                }
            }

            config = loader.load();
            loadData();
        } catch (IOException e) {
            logger.error("Error while loading config", e);
        }
    }

    public void setLastServer(UUID playerUuid, String serverName) {
        lastServerMap.put(playerUuid, serverName);
        saveData();
    }

    public Optional<String> getLastServer(UUID playerUuid) {
        return Optional.ofNullable(lastServerMap.get(playerUuid));
    }

    private void loadData() {
        for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : config.childrenMap().entrySet()) {
            Object keyObj = entry.getKey();
            CommentedConfigurationNode childNode = entry.getValue();

            String uuidString = keyObj.toString();
            String valueString = childNode.getString();

            try {
                lastServerMap.put(UUID.fromString(uuidString), valueString);
            } catch (IllegalArgumentException e) {
                logger.warn("Skipping invalid UUID in data.yml: " + uuidString);
            }
        }
    }

    private void saveData() {
        config.childrenMap().clear();

        for (Map.Entry<UUID, String> entry : lastServerMap.entrySet()) {
            UUID uuid = entry.getKey();
            String value = entry.getValue();
            try {
                config.node(uuid.toString()).set(value);
            } catch (SerializationException e) {
                logger.error("Error while Saving data in data.yml: " + uuid);
            }
        }

        try {
            loader.save(config);
        } catch (ConfigurateException e) {
            logger.error("Could not save data.yml", e);
        }
    }
}
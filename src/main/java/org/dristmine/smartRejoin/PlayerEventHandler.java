package org.dristmine.smartRejoin;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerEventHandler {

    private final SmartRejoin plugin;

    private final Map<UUID, String> lastServerMap = new ConcurrentHashMap<>();

    public PlayerEventHandler(SmartRejoin plugin) {
        this.plugin = plugin;
    }


    /**
     * Fired when a player disconnects from the proxy.
     * We use this to record the server they were on.
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (!lastServerMap.containsKey(player.getUniqueId())) {
            return;
        }
        String serverName = lastServerMap.remove(player.getUniqueId());
        plugin.getPlayerDataManager().setLastServer(player.getUniqueId(), serverName);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastServerMap.put(uuid, event.getServer().getServerInfo().getName());
    }

    /**
     * Fired when a player is logging in and Velocity needs to decide which server to send them to.
     * This is the perfect place to implement our custom logic.
     */
    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        Optional<String> lastServerNameOpt = plugin.getPlayerDataManager().getLastServer(player.getUniqueId());

        CompletableFuture<Optional<RegisteredServer>> futureServer;

        if (lastServerNameOpt.isPresent()) {
            String lastServerName = lastServerNameOpt.get();
            plugin.logInfo("Player " + player.getUsername() + " is rejoining. Last seen on: " + lastServerName);
            futureServer = plugin.getServerFinder().findServerFor(player, lastServerName);
        } else {
            plugin.logInfo("Player " + player.getUsername() + " has no previous server data. Using fallback logic.");
            futureServer = plugin.getServerFinder().findFallbackServer();
        }

        try {
            futureServer.join().ifPresent(event::setInitialServer);
        } catch (Exception e) {
            plugin.getLogger().error("An exception occurred while finding an initial server for " + player.getUsername(), e);
        }
    }
}

package com.aaltoes;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.TimeUnit;

public final class Aaltoes extends JavaPlugin implements Listener {
    
    private DiscordWebSocketClient webSocketClient;
    private String webSocketUrl;
    private String authToken;
    private String discordPrefix;
    private boolean logConsole;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        loadConfig();
        
        getServer().getPluginManager().registerEvents(this, this);
        
        connectWebSocket();
        
        getLogger().info("Aaltoes has been enabled!");
    }
    
    @Override
    public void onDisable() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
            getLogger().info("WebSocket connection closed");
        }
        
        getLogger().info("Aaltoes has been disabled!");
    }
    
    private void loadConfig() {
        FileConfiguration config = getConfig();
        
        webSocketUrl = config.getString("websocket.url", "");
        authToken = config.getString("websocket.auth-token", "");
        logConsole = config.getBoolean("websocket.log-to-console", true);
        
        discordPrefix = config.getString("chat.discord-prefix", "&b[Discord] &f");
        
        if (webSocketUrl.isEmpty()) {
            getLogger().warning("WebSocket URL not configured! Please set it in the config.yml");
        } else {
            getLogger().info("WebSocket URL configured successfully!");
        }
    }
    
    private void connectWebSocket() {
        if (webSocketUrl.isEmpty()) {
            getLogger().warning("Cannot connect to WebSocket: URL not configured");
            return;
        }
        
        try {
            webSocketClient = new DiscordWebSocketClient(new URI(webSocketUrl));
            webSocketClient.connect();
            getLogger().info("Connecting to WebSocket server...");
        } catch (Exception e) {
            getLogger().severe("Failed to connect to WebSocket server: " + e.getMessage());
            e.printStackTrace();
            
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, this::connectWebSocket, 200L); // 10 seconds
        }
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        
        sendToDiscord(player.getName(), message);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sendPlayerJoinToDiscord(player.getName());
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sendPlayerLeaveToDiscord(player.getName());
    }
    
    private void sendToDiscord(String username, String message) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            getLogger().warning("Cannot send message to Discord: WebSocket not connected");
            return;
        }
        
        if (logConsole) {
            getLogger().info("Sending to Discord: " + username + ": " + message);
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("type", "chat");
        json.addProperty("username", username);
        json.addProperty("content", message);
        
        webSocketClient.send(json.toString());
    }
    
    private void sendPlayerJoinToDiscord(String username) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            getLogger().warning("Cannot send join notification to Discord: WebSocket not connected");
            return;
        }
        
        if (logConsole) {
            getLogger().info("Sending join notification to Discord: " + username + " joined the server");
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("type", "player_join");
        json.addProperty("username", username);
        json.addProperty("online", Bukkit.getOnlinePlayers().size());
        json.addProperty("max", Bukkit.getMaxPlayers());
        
        webSocketClient.send(json.toString());
    }
    
    private void sendPlayerLeaveToDiscord(String username) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            getLogger().warning("Cannot send leave notification to Discord: WebSocket not connected");
            return;
        }
        
        if (logConsole) {
            getLogger().info("Sending leave notification to Discord: " + username + " left the server");
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("type", "player_leave");
        json.addProperty("username", username);
        json.addProperty("online", Bukkit.getOnlinePlayers().size() - 1); // Subtract 1 as player is still counted in getOnlinePlayers()
        json.addProperty("max", Bukkit.getMaxPlayers());
        
        webSocketClient.send(json.toString());
    }
    
    private class DiscordWebSocketClient extends WebSocketClient {
        
        private boolean authenticated = false;
        private int reconnectAttempts = 0;
        
        public DiscordWebSocketClient(URI serverUri) {
            super(serverUri);
        }
        
        @Override
        public void onOpen(ServerHandshake handshakedata) {
            getLogger().info("WebSocket connection established");
            reconnectAttempts = 0;
            
            if (!authToken.isEmpty()) {
                JsonObject auth = new JsonObject();
                auth.addProperty("type", "auth");
                auth.addProperty("token", authToken);
                send(auth.toString());
            } else {
                authenticated = true;
            }
            
            JsonObject serverInfo = new JsonObject();
            serverInfo.addProperty("type", "server_info");
            serverInfo.addProperty("name", Bukkit.getServer().getName());
            serverInfo.addProperty("version", Bukkit.getVersion());
            serverInfo.addProperty("online", Bukkit.getOnlinePlayers().size());
            serverInfo.addProperty("max", Bukkit.getMaxPlayers());
            send(serverInfo.toString());
        }
        
        @Override
        public void onMessage(String message) {
            try {
                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "";
                
                if ("auth".equals(type)) {
                    String status = json.get("status").getAsString();
                    if ("success".equals(status)) {
                        authenticated = true;
                        getLogger().info("Successfully authenticated with Discord WebSocket server");
                    } else {
                        getLogger().severe("Authentication failed: " + json.get("message").getAsString());
                        close();
                    }
                    return;
                }
                
                if (!authenticated && !authToken.isEmpty()) {
                    return;
                }
                
                if ("chat".equals(type)) {
                    String username = json.get("username").getAsString();
                    String content = json.get("content").getAsString();
                    
                    Bukkit.getScheduler().runTask(Aaltoes.this, () -> {
                        String formattedMessage = ChatColor.translateAlternateColorCodes('&', 
                            discordPrefix + username + ": " + content);
                        Bukkit.broadcastMessage(formattedMessage);
                        
                        if (logConsole) {
                            getLogger().info("Discord message broadcast: " + username + ": " + content);
                        }
                    });
                }
                
                if ("request_players".equals(type)) {
                    JsonObject response = new JsonObject();
                    response.addProperty("type", "player_list");
                    
                    JsonObject players = new JsonObject();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        players.addProperty(player.getUniqueId().toString(), player.getName());
                    }
                    
                    response.add("players", players);
                    send(response.toString());
                }
            } catch (Exception e) {
                getLogger().severe("Error processing WebSocket message: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        @Override
        public void onClose(int code, String reason, boolean remote) {
            authenticated = false;
            getLogger().info("WebSocket connection closed: " + reason + " (code: " + code + ")");
            
            if (reconnectAttempts < 10) {
                long delay = (long) Math.min(30, Math.pow(2, reconnectAttempts));
                reconnectAttempts++;
                
                getLogger().info("Attempting to reconnect in " + delay + " seconds... (Attempt " + reconnectAttempts + "/10)");
                
                Bukkit.getScheduler().runTaskLaterAsynchronously(Aaltoes.this, () -> {
                    getLogger().info("Reconnecting to WebSocket server...");
                    reconnect();
                }, delay * 20L); 
            } else {
                getLogger().severe("Failed to reconnect after 10 attempts. Please restart the server or check your configuration.");
            }
        }
        
        @Override
        public void onError(Exception ex) {
            getLogger().severe("WebSocket error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
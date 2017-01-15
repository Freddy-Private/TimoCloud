package at.TimoCraft.TimoCloud.bungeecord;


import at.TimoCraft.TimoCloud.bungeecord.commands.LobbyCommand;
import at.TimoCraft.TimoCloud.bungeecord.commands.TimoCloudCommand;
import at.TimoCraft.TimoCloud.bungeecord.listeners.PlayerConnect;
import at.TimoCraft.TimoCloud.bungeecord.listeners.ServerKick;
import at.TimoCraft.TimoCloud.bungeecord.managers.FileManager;
import at.TimoCraft.TimoCloud.bungeecord.managers.ServerManager;
import at.TimoCraft.TimoCloud.bungeecord.sockets.BungeeSocketServer;
import at.TimoCraft.TimoCloud.bungeecord.sockets.BungeeSocketServerHandler;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Created by Timo on 26.12.16.
 */
public class TimoCloud extends Plugin {

    private static TimoCloud instance;
    private ServerManager serverManager;
    private FileManager fileManager;
    private BungeeSocketServer socketServer;
    private BungeeSocketServerHandler socketServerHandler;
    private String prefix;
    private boolean shuttingDown = false;

    public static void info(String message) {
        getInstance().getLogger().info(" " + message);
    }

    public static void severe(String message) {
        getInstance().getLogger().severe(" " + message);
    }

    public void onEnable() {
        instance = this;
        //info("Starting TimoCloud in 5 seconds...");
        getProxy().getScheduler().schedule(this, (Runnable) () -> enableDelayed(), 1, 0, TimeUnit.SECONDS);
    }

    private void enableDelayed() {
        makeInstances();
        registerCommands();
        registerListeners();
        registerTasks();
        getServerManager().startAllServers();

        info("Successfully started TimoCloud!");
    }

    public void onDisable() {
        setShuttingDown(true);
        getServerManager().stopAllServers();
        info("Successfully stopped TimoCloud!");
    }

    private void makeInstances() {
        fileManager = new FileManager();
        serverManager = new ServerManager();
        socketServer = new BungeeSocketServer();
        socketServerHandler = new BungeeSocketServerHandler();
    }

    private void registerCommands() {
        getProxy().getPluginManager().registerCommand(this, new TimoCloudCommand());
        getProxy().getPluginManager().registerCommand(this, new LobbyCommand());
    }

    private void registerTasks() {
        getProxy().getScheduler().runAsync(this, () -> {
            info("Starting socket-server...");
            try {
                socketServer.init("127.0.0.1", getFileManager().getConfig().getInt("socket-port"));
            } catch (Exception e) {
                severe("Error while initializing socketServer:");
                e.printStackTrace();
            }
        });

        getProxy().getScheduler().schedule(this, () -> getSocketServerHandler().flush(), 0L, 1L, TimeUnit.SECONDS);
    }

    private void registerListeners() {
        getProxy().getPluginManager().registerListener(this, new PlayerConnect());
        getProxy().getPluginManager().registerListener(this, new ServerKick());
    }

    public static TimoCloud getInstance() {
        return instance;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public FileManager getFileManager() {
        return fileManager;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }


    public String getFileName() {
        Plugin plugin = (Plugin) this;
        String path = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().getFile();
        String[] spl = path.split("/");
        String name = spl[spl.length - 1];
        return name;
    }

    public BungeeSocketServer getSocketServer() {
        return socketServer;
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public void setShuttingDown(boolean shuttingDown) {
        this.shuttingDown = shuttingDown;
    }

    public BungeeSocketServerHandler getSocketServerHandler() {
        return socketServerHandler;
    }
}

package at.TimoCraft.TimoCloud.bungeecord.managers;

import at.TimoCraft.TimoCloud.bungeecord.TimoCloud;
import at.TimoCraft.TimoCloud.bungeecord.objects.ServerGroup;
import at.TimoCraft.TimoCloud.bungeecord.objects.TemporaryServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import org.apache.commons.io.FileDeleteStrategy;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Timo on 27.12.16.
 */
public class ServerManager {

    private List<Integer> portsInUse = new ArrayList<>();
    private List<ServerGroup> groups;
    private List<String> startedServers = new ArrayList<>();

    public ServerManager() {
        init();
    }

    private void init() {
        readGroups();
    }

    public void startAllServers() {
        for (ServerGroup group : groups) {
            group.startAllServers();
        }
    }

    public void stopAllServers() {
        for (ServerGroup group : groups) {
            group.stopAllServers();
        }
    }

    public void readGroups() {
        groups = new ArrayList<>();
        Configuration groupsConfig = TimoCloud.getInstance().getFileManager().getGroups();
        for (String group : groupsConfig.getKeys()) {
            ServerGroup serverGroup = new ServerGroup(
                    group,
                    groupsConfig.getInt(group + ".onlineAmount"),
                    groupsConfig.getInt(group + ".ramInMegabyte") == 0 ? groupsConfig.getInt(group + ".ramInGigabyte")*1024 : groupsConfig.getInt(group + ".ramInMegabyte")
            );
            groups.add(serverGroup);
        }
    }

    public int getFreePort() {
        for (int i = 40000; i < 50000; i++) {
            if (isPortFree(i)) {
                i = registerPort(i);
                return i;
            }
        }
        return 25565;
    }

    public boolean isPortFree(int port) {
        return !portsInUse.contains(port);
    }

    public int registerPort(int port) {
        if (isPortFree(port)) {
            portsInUse.add(port);
            return port;
        }
        return getFreePort();
    }

    public void unregisterPort(int port) {
        if (!isPortFree(port)) {
            portsInUse.remove(Integer.valueOf(port));
            return;
        }
        TimoCloud.severe("Error: Attemping to unregister not registered port: " + port);
    }

    public ServerInfo getRandomLobbyServer(ServerInfo notThis) {
        for (ServerGroup group : getGroups()) {
            if (group.getName().equalsIgnoreCase("lobby")) {
                if (group.getTemporaryServers().size() == 0) {
                    continue;
                }
                List<TemporaryServer> servers;
                if (notThis == null) {
                    servers = group.getTemporaryServers();
                } else {
                    servers = new ArrayList<>();
                    for (TemporaryServer server : group.getTemporaryServers()) {
                        if (! server.getServerInfo().equals(notThis)) {
                            servers.add(server);
                        }
                    }
                    if (servers.size() == 0) {
                        TimoCloud.severe("No running fallback server found. Maybe you should start more lobby servers.");
                        return notThis;
                    }
                }

                return servers.get(new Random().nextInt(servers.size())).getServerInfo();
            }
        }

        return TimoCloud.getInstance().getProxy().getServerInfo(TimoCloud.getInstance().getFileManager().getConfig().getString("fallback"));
    }

    public void startServer(ServerGroup group, String name, boolean once) {
        int port = getFreePort();
        TimoCloud.getInstance().getProxy().getScheduler().runAsync(TimoCloud.getInstance(), () -> startServerFromAsyncContext(group, name, port, once));
    }

    public void startServerFromAsyncContext(ServerGroup group, String name, int port, boolean once) {
        TimoCloud.getInstance().info("Starting server " + name + "...");
        double millisBefore = System.currentTimeMillis();

        File templatesDir = new File(TimoCloud.getInstance().getFileManager().getTemplatesDirectory() + group.getName());
        File spigot = new File(templatesDir, "spigot.jar");
        if (!spigot.exists()) {
            TimoCloud.severe("Could not start server " + name + " because spigot.jar does not exist.");
            return;
        }

        try {
            File directory = new File(TimoCloud.getInstance().getFileManager().getTemporaryDirectory() + name);
            if (directory.exists()) {
                FileDeleteStrategy.FORCE.deleteQuietly(directory);
            }
            FileUtils.copyDirectory(new File(TimoCloud.getInstance().getFileManager().getTemplatesDirectory() + group.getName()), directory);

            File plugin = new File(TimoCloud.getInstance().getFileManager().getTemporaryDirectory() + name, "plugins/" + TimoCloud.getInstance().getFileName());
            if (plugin.exists()) {
                plugin.delete();
            }
            try {
                Files.copy(new File("plugins/" + TimoCloud.getInstance().getFileName()).toPath(), plugin.toPath());
            } catch (Exception e) {
                TimoCloud.severe("Error while copying plugin into template:");
                e.printStackTrace();
                if (!plugin.exists()) {
                    return;
                }
            }

            TemporaryServer server = new TemporaryServer(name, group, port, once);
            server.start();
            group.addStartingServer(server);

            double millisNow = System.currentTimeMillis();
            TimoCloud.getInstance().info("Successfully started server " + name + " in " + (millisNow - millisBefore) / 1000 + " seconds.");
        } catch (Exception e) {
            TimoCloud.severe("Error while starting server " + name + ":");
            e.printStackTrace();
        }
    }

    public boolean isRunning(String name) {
        return startedServers.contains(name);
    }

    public void addServer(String name) {
        if (!isRunning(name)) {
            startedServers.add(name);
            return;
        }
        TimoCloud.severe("Tried to add already running server: " + name);
    }

    public void removeServer(String name) {
        try {
            if (TimoCloud.getInstance().getProxy().getServers().containsKey(name)) {
                TimoCloud.getInstance().getProxy().getServers().remove(name);
            }
            if (startedServers.contains(name)) {
                startedServers.remove(name);
                return;
            }
        } catch (Exception e) {}
        TimoCloud.severe("Tried to remove not started server: " + name);
    }

    public boolean groupExists(ServerGroup group) {
        return groups.contains(group);
    }

    public ServerGroup getGroupByName(String name) {
        for (ServerGroup group : groups) {
            if (group.getName().equals(name)) {
                return group;
            }
        }
        return null;
    }

    public void addGroup(ServerGroup group) throws IOException {
        TimoCloud.getInstance().getFileManager().getGroups().set(group.getName() + ".onlineAmount", group.getStartupAmount());
        TimoCloud.getInstance().getFileManager().getGroups().set(group.getName() + (group.getRam() < 128 ? ".ramInGigabyte" : ".ramInMegabyte"), group.getRam());
        ConfigurationProvider.getProvider(YamlConfiguration.class).save(TimoCloud.getInstance().getFileManager().getGroups(), TimoCloud.getInstance().getFileManager().getGroupsFile());
        if (!groups.contains(group)) {
            groups.add(group);
        }
        group.startAllServers();
    }

    public void removeGroup(String name) throws IOException {
        ServerGroup group = getGroupByName(name);
        group.stopAllServers();
        groups.remove(group);
        TimoCloud.getInstance().getFileManager().getGroups().set(name, null);
        ConfigurationProvider.getProvider(YamlConfiguration.class).save(TimoCloud.getInstance().getFileManager().getGroups(), TimoCloud.getInstance().getFileManager().getGroupsFile());
    }

    public List<ServerGroup> getGroups() {
        return groups;
    }

    public TemporaryServer getServerByName(String name) {
        for (ServerGroup group : getGroups()) {
            for (TemporaryServer server : group.getStartingServers()) {
                if (server.getName().equals(name)) {
                    return server;
                }
            }
            for (TemporaryServer server : group.getTemporaryServers()) {
                if (server.getName().equals(name)) {
                    return server;
                }
            }
        }
        return null;
    }
}

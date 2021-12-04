package net.lapismc.afkplusreconnect;

import net.lapismc.afkplus.api.AFKActionEvent;
import net.lapismc.afkplus.api.AFKPlusPlayerAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class AFKPlusReconnect extends JavaPlugin implements Listener {

    //Players who have been kicked by AFKPlus, the long is when they were kicked
    private final HashMap<UUID, Long> actionedPlayers = new HashMap<>();
    //Players who have rejoined and must move before Long occurs
    private final HashMap<UUID, Long> allowedPlayers = new HashMap<>();
    //Repeating task to be canceled on disable
    private BukkitTask repeatingTask;
    //AFKPlus player API for all our API access
    AFKPlusPlayerAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        api = new AFKPlusPlayerAPI();
        new DisconnectConsoleFilter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        repeatingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, getRepeatingTask(), 20, 20);
    }

    @Override
    public void onDisable() {
        repeatingTask.cancel();
    }

    /**
     * Check if the player has been kicked by action, if so start monitoring them
     */
    @EventHandler
    public void onAction(AFKActionEvent e) {
        UUID uuid = e.getPlayer().getUUID();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            //Ignore them if they are in the allowed players list as that means they are about to be banned
            if (!Bukkit.getOfflinePlayer(uuid).isOnline() && !allowedPlayers.containsKey(uuid)) {
                //The player is no longer online 1 tick after the action, so we can assume they were kicked
                actionedPlayers.put(uuid, new Date().getTime());
            }
        }, 1);
    }

    @EventHandler
    public void onPlayerJoin(PlayerLoginEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (actionedPlayers.containsKey(uuid)) {
            if (getConfig().getBoolean("Rejoin.AllowRejoin")) {
                //Set the player as AFK and add to allowed list
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    api.getPlayer(uuid).forceStartAFK();
                    Bukkit.getPlayer(uuid).sendMessage(getConfig().getString("Messages.Rejoin",
                            "You have a few seconds to prove you arent AFK or you will be temp-banned for auto reconnecting"));
                }, 10);
                if (allowedPlayers.containsKey(uuid)) {
                    return;
                }
                allowedPlayers.put(uuid, new Date().getTime());
            } else {
                String msg = ChatColor.translateAlternateColorCodes('&', getConfig().getString("Messages.Kick",
                        "&4You cannot rejoin as you were recently kicked for AFKing"));
                //Message console since our console filter will hide the kick message
                String consoleMsg = getConfig().getString("Messages.KickConsole", "");
                if (!consoleMsg.equals("")) {
                    getLogger().info(consoleMsg.replace("{PLAYER}", e.getPlayer().getName()));
                }
                //Disallow join
                e.disallow(PlayerLoginEvent.Result.KICK_BANNED, msg);
            }
        }
    }

    public Runnable getRepeatingTask() {
        return () -> {
            //Clean Actioned Players
            long rejoinTimeToWait = getConfig().getInt("Rejoin.TimeToWait") * 1000L;
            for (UUID uuid : actionedPlayers.keySet()) {
                long timeSinceAction = new Date().getTime() - actionedPlayers.get(uuid);
                if (timeSinceAction > rejoinTimeToWait) {
                    //They've been offline long enough that we can stop monitoring them
                    actionedPlayers.remove(uuid);
                }
            }

            //Check allowed players and kick if needed
            long reactionTimeToAction = getConfig().getInt("Reaction.TimeToAction") * 1000L;
            for (UUID uuid : allowedPlayers.keySet()) {
                long timeSinceRejoin = new Date().getTime() - allowedPlayers.get(uuid);
                if (timeSinceRejoin > reactionTimeToAction) {
                    if (api.getPlayer(uuid).isAFK()) {
                        //The player has not left AFK and had been online too long
                        //Run the command from the config
                        String msg = getConfig().getString("ActionCommand",
                                "tempban {PLAYER} 30m Attempting AFK kick avoidance")
                                .replace("{PLAYER}", Bukkit.getOfflinePlayer(uuid).getName());
                        Bukkit.getScheduler().runTask(this, () -> {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), msg);
                        });
                        actionedPlayers.remove(uuid);
                        allowedPlayers.remove(uuid);
                    }
                }
            }
        };
    }
}

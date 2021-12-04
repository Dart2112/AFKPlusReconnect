package net.lapismc.afkplusreconnect;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;
import org.bukkit.ChatColor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.WildcardType;

public class DisconnectConsoleFilter {

    private final AFKPlusReconnect plugin;

    public DisconnectConsoleFilter(AFKPlusReconnect plugin) {
        this.plugin = plugin;
        setLog4JFilter();
    }

    private void setLog4JFilter() {
        AbstractFilter abstractConsoleLogListener = new AbstractFilter() {

            private Result validateMessage(Message message) {
                if (message == null) {
                    return Result.NEUTRAL;
                }
                return validateMessage(message.getFormattedMessage());
            }

            private Result validateMessage(String message) {
                return shouldBlock(message) ? Result.DENY : Result.ACCEPT;
            }

            @Override
            public Result filter(LogEvent event) {
                Message candidate = null;
                if (event != null) {
                    candidate = event.getMessage();
                }
                return validateMessage(candidate);
            }

            @Override
            public Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
                return validateMessage(msg);
            }

            @Override
            public Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
                String candidate = null;
                if (msg != null) {
                    candidate = msg.toString();
                }
                return validateMessage(candidate);
            }

            @Override
            public Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
                return validateMessage(msg);
            }
        };
        Logger logger = (Logger) LogManager.getRootLogger();
        logger.addFilter(abstractConsoleLogListener);
    }

    private boolean shouldBlock(String msg) {
        boolean isDisconnect = msg.contains("com.mojang.authlib.GameProfile");
        String strippedMsg = ChatColor.stripColor(msg);
        String strippedTemplate = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("Messages.Kick", "&4You cannot rejoin as you were recently kicked for AFKing")));
        File f = new File("./OUTPUT.txt");
        try {
            FileWriter writer = new FileWriter(f, true);
            writer.append("MSG: ").append(strippedMsg).append("\n");
            writer.append("TEMPLATE: ").append(strippedTemplate).append("\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        boolean isKickedPlayer = strippedMsg.contains(strippedTemplate);
        return isDisconnect && isKickedPlayer;
    }


}

package com.qprint.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.item.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.qprint.utils.ItemUtils.getItemFromName;

public class MessageUtils {
    // TODO: could probably hook directly into Baritone somehow if i weren't such a lazy bastard
    private static final Pattern materialHeaderPattern = Pattern.compile("\\[Baritone] Missing materials for at least:");
    private static final Pattern materialFooterPattern = Pattern.compile("\\[Baritone] Unable to do it. Pausing. resume to resume, cancel to cancel");
    private static final Pattern materialPattern = Pattern.compile("\\[Baritone]\\s+(\\d+)x\\s+Block\\{([^}]+)}");
    private static final Pattern donePattern = Pattern.compile("\\[Baritone] Done building");
    private static final Pattern pausedPattern = Pattern.compile("\\[Baritone] Paused");
    private static final Pattern resumedPattern = Pattern.compile("^\\[Baritone] (Resumed|Not paused)$");

    private static final int MESSAGE_BUFFER_SIZE = 50;

    private static final FixedSizeList<String> messageQueue = new FixedSizeList<>(MESSAGE_BUFFER_SIZE);

    private static Map<Item, Integer> lastMaterialDump;

    public static void addMessage(String msg) {
        messageQueue.add(msg);
    }

    public static void clearMessageQueue() {
        messageQueue.clear();
        lastMaterialDump = null;
    }

    public static boolean hasDoneBuildingMessage() {
        for (var i = 0; i < Math.min(10, messageQueue.size()); i++) {
            if (donePattern.matcher(messageQueue.get(i)).find())
                return true;
        }

        return false;
    }

    public static boolean hasPausedMessage() {
        int pausedIdx = 12, resumedIdx = 12;
        for (var i = 0; i < Math.min(10, messageQueue.size()); i++) {
            if (pausedPattern.matcher(messageQueue.get(i)).find() && pausedIdx == 12)
                pausedIdx = i;
            else if (resumedPattern.matcher(messageQueue.get(i)).find() && resumedIdx == 12)
                resumedIdx = i;
        }

        return pausedIdx != 12 && pausedIdx < resumedIdx;
    }

    public static boolean hasResumedMessage() {
        int pausedIdx = 12, resumedIdx = 12;
        for (var i = 0; i < Math.min(10, messageQueue.size()); i++) {
            if (pausedPattern.matcher(messageQueue.get(i)).find() && pausedIdx == 12)
                pausedIdx = i;
            else if (resumedPattern.matcher(messageQueue.get(i)).find() && resumedIdx == 12)
                resumedIdx = i;
        }

        return resumedIdx != 12 && resumedIdx < pausedIdx;
    }

    public static Map<Item, Integer> getLastBaritoneMaterialDump() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.inGameHud == null) return null;

        ChatHud chatHud = client.inGameHud.getChatHud();

        int headerIdx = -1, footerIdx = -1;

        // find message with the header & footer
        for (int i = 0; i < messageQueue.size(); i++) {
            if (footerIdx == -1) {
                var footerMatcher = materialFooterPattern.matcher(messageQueue.get(i));

                if (footerMatcher.find()) {
                    footerIdx = i;
                }
            } else if (headerIdx == -1) {
                var headerMatcher = materialHeaderPattern.matcher(messageQueue.get(i));

                if (headerMatcher.find()) {
                    headerIdx = i;
                }
            } else {
                break;
            }
        }

        if (footerIdx == -1 || headerIdx == -1) {
            return null;    // no material list found
        }

        var result = new HashMap<Item, Integer>();

        for (int i = footerIdx; i < headerIdx; i++) {
            var matcher = materialPattern.matcher(messageQueue.get(i));

            if (matcher.find()) {
                try {
                    var quantity = Integer.parseInt(matcher.group(1));
                    var blockName = matcher.group(2);
                    result.put(getItemFromName(blockName), quantity);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (lastMaterialDump != null) {
            if (lastMaterialDump.size() == result.size() && lastMaterialDump.equals(result)) {
                return null;    // we found the same material list as last time
            }
        }

        lastMaterialDump = result;
        return result;
    }
}

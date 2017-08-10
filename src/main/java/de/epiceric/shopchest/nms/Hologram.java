package de.epiceric.shopchest.nms;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Hologram {

    private static final List<Hologram> HOLOGRAMS = new ArrayList<>();

    /**
     * @param armorStand Armor stand that's part of a hologram
     * @return Hologram, the armor stand is part of
     */
    public static Hologram getHologram(ArmorStand armorStand) {
        for (Hologram hologram : HOLOGRAMS) {
            if (hologram.contains(armorStand)) {
                return hologram;
            }
        }

        return null;
    }

    /**
     * @param armorStand Armor stand to check
     * @return Whether the armor stand is part of a hologram
     */
    public static boolean isPartOfHologram(ArmorStand armorStand) {
        return getHologram(armorStand) != null;
    }

    // concurrent since update task is in async thread
    // since this is a fake entity, hologram is hidden per default
    private final Set<UUID> viewers = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final List<ArmorStandWrapper> wrappers = new ArrayList<>();
    private final Location location;
    private final ShopChest plugin;
    private final Config config;

    private boolean exists;
    private ArmorStandWrapper interactArmorStandWrapper;

    public Hologram(ShopChest plugin, String[] lines, Location location) {
        this.plugin = plugin;
        this.config = plugin.getShopChestConfig();
        this.location = location;

        for (int i = 0; i < lines.length; i++) {
            addLine(i, lines[i]);
        }

        if (plugin.getShopChestConfig().enable_hologram_interaction) {
            double y = 0.6;
            if (config.hologram_fixed_bottom) y = 0.85;

            Location loc = getLocation().add(0, y, 0);
            interactArmorStandWrapper = new ArmorStandWrapper(plugin, loc, null, true);
        }

        this.exists = true;
        HOLOGRAMS.add(this);
    }

    /**
     * @return Location of the hologram
     */
    public Location getLocation() {
        return location.clone();
    }

    /**
     * @return Whether the hologram exists and is not dead
     */
    public boolean exists() {
        return exists;
    }

    /**
     * @param armorStand Armor stand to check
     * @return Whether the given armor stand is part of the hologram
     */
    public boolean contains(ArmorStand armorStand) {
        for (ArmorStandWrapper wrapper : wrappers) {
            if (wrapper.getUuid().equals(armorStand.getUniqueId())) {
                return true;
            }
        }
        return interactArmorStandWrapper != null && interactArmorStandWrapper.getUuid().equals(armorStand.getUniqueId());
    }

    /** Returns the ArmorStandWrappers of this hologram */
    public List<ArmorStandWrapper> getArmorStandWrappers() {
        return wrappers;
    }

    /** Returns the ArmorStandWrapper of this hologram that is positioned higher to be used for interaction */
    public ArmorStandWrapper getInteractArmorStandWrapper() {
        return interactArmorStandWrapper;
    }

    /**
     * @param p Player to check
     * @return Whether the hologram is visible to the player
     */
    public boolean isVisible(Player p) {
        return viewers.contains(p.getUniqueId());
    }

    /**
     * @param p Player to which the hologram should be shown
     */
    public void showPlayer(Player p) {
        showPlayer(p, false);
    }

    /**
     * @param p Player to which the hologram should be shown
     * @param force whether to force or not
     */
    public void showPlayer(Player p, boolean force) {
        if (viewers.add(p.getUniqueId()) || force) {
            togglePlayer(p, true);
        }
    }

    /**
     * @param p Player from which the hologram should be hidden
     */
    public void hidePlayer(Player p) {
        hidePlayer(p, false);
    }

    /**
     * @param p Player from which the hologram should be hidden
     * @param force whether to force or not
     */
    public void hidePlayer(Player p, boolean force) {
        if (viewers.remove(p.getUniqueId()) || force) {
            togglePlayer(p, false);
        }
    }

    /**
     * Removes the hologram. <br>
     * Hologram will be hidden from all players and will be killed
     */
    public void remove() {
        viewers.clear();

        for (ArmorStandWrapper wrapper : wrappers) {
            wrapper.remove();
        }
        wrappers.clear();

        if (interactArmorStandWrapper != null) {
            interactArmorStandWrapper.remove();
        }
        interactArmorStandWrapper = null;

        exists = false;
        HOLOGRAMS.remove(this);
    }

    public void resetVisible(Player p) {
        viewers.remove(p.getUniqueId());
    }

    private void togglePlayer(Player p, boolean visible) {
        for (ArmorStandWrapper wrapper : wrappers) {
            wrapper.setVisible(p, visible);
        }

        if (interactArmorStandWrapper != null) {
            interactArmorStandWrapper.setVisible(p, visible);
        }
    }

    /**
     * Get all hologram lines
     *
     * @return Hologram lines
     */
    public String[] getLines() {
        List<String> lines = new ArrayList<>();
        for (ArmorStandWrapper wrapper : wrappers) {
            lines.add(wrapper.getCustomName());
        }

        return lines.toArray(new String[lines.size()]);
    }

    /**
     * Add a line
     *
     * @param line where to insert
     * @param text text to display
     */
    public void addLine(int line, String text) {
        addLine(line, text, false);
    }

    private void addLine(int line, String text, boolean forceUpdateLine) {
        if (text == null || text.isEmpty()) return;

        if (line >= wrappers.size()) {
            line = wrappers.size();
        }

        text = ChatColor.translateAlternateColorCodes('&', text);

        if (config.hologram_fixed_bottom) {
            for (int i = 0; i < line; i++) {
                ArmorStandWrapper wrapper = wrappers.get(i);
                wrapper.setLocation(wrapper.getLocation().add(0, 0.25, 0));
            }
        } else {
            for (int i = line; i < wrappers.size(); i++) {
                ArmorStandWrapper wrapper = wrappers.get(i);
                wrapper.setLocation(wrapper.getLocation().subtract(0, 0.25, 0));
            }
        }

        Location loc = getLocation();

        if (!config.hologram_fixed_bottom) {
            loc.subtract(0, line * 0.25, 0);
        }

        ArmorStandWrapper wrapper = new ArmorStandWrapper(plugin, loc, text, false);
        wrappers.add(line, wrapper);

        if (forceUpdateLine) {
            for (Player player : location.getWorld().getPlayers()) {
                if (viewers.contains(player.getUniqueId())) {
                    wrapper.setVisible(player, true);
                }
            }
        }
    }

    /**
     * Set a line
     *
     * @param line index to change
     * @param text text to display
     */
    public void setLine(int line, String text) {
        if (text == null ||text.isEmpty()) {
            removeLine(line);
            return;
        }

        text = ChatColor.translateAlternateColorCodes('&', text);

        if (line >= wrappers.size()) {
            addLine(line, text, true);
            return;
        }

        wrappers.get(line).setCustomName(text);
    }

    /**
     * Remove a line
     *
     * @param line index to remove
     */
    public void removeLine(int line) {
        if (line < wrappers.size()) {
            if (config.hologram_fixed_bottom) {
                for (int i = 0; i < line; i++) {
                    ArmorStandWrapper wrapper = wrappers.get(i);
                    wrapper.setLocation(wrapper.getLocation().subtract(0, 0.25, 0));
                }
            } else {
                for (int i = line + 1; i < wrappers.size(); i++) {
                    ArmorStandWrapper wrapper = wrappers.get(i);
                    wrapper.setLocation(wrapper.getLocation().add(0, 0.25, 0));
                }
            }

            wrappers.get(line).remove();
            wrappers.remove(line);
        }
    }
}

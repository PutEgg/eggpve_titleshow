package org.totemcraft.eggpve;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class Eggpve extends JavaPlugin implements Listener {
    private final Map<String, Region> titleRegions = new HashMap<>();
    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();
    private final Map<UUID, String> playerCurrentRegion = new HashMap<>();
    private File regionsFile;
    private FileConfiguration regionsConfig;

    @Override
    public void onEnable() {
        createRegionsFile();
        loadRegions();
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveRegions();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        String newRegionId = null;

        for (Map.Entry<String, Region> entry : titleRegions.entrySet()) {
            if (entry.getValue().isInRegion(location)) {
                newRegionId = entry.getKey();
                break;
            }
        }

        UUID playerId = player.getUniqueId();
        String currentRegionId = playerCurrentRegion.get(playerId);

        if (newRegionId != null && !newRegionId.equals(currentRegionId)) {
            // 玩家进入了新区域
            Region region = titleRegions.get(newRegionId);
            String title = region.getTitle();
            showAnimatedTitle(player, title);
            playerCurrentRegion.put(playerId, newRegionId);
        } else if (newRegionId == null && currentRegionId != null) {
            // 玩家离开了区域，不做任何操作，只更新当前区域状态
            playerCurrentRegion.remove(playerId);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("此命令只能由玩家执行。");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("eggpve")) {
            if (args.length < 1) {
                return false;
            }

            String subCommand = args[0];

            if (subCommand.equalsIgnoreCase("pos1")) {
                Location pos1 = player.getLocation();
                pos1Map.put(player.getUniqueId(), pos1);
                player.sendMessage(ChatColor.GREEN + "Pos1 已设置: " + locationToString(pos1));
                return true;
            } else if (subCommand.equalsIgnoreCase("pos2")) {
                Location pos2 = player.getLocation();
                pos2Map.put(player.getUniqueId(), pos2);
                player.sendMessage(ChatColor.GREEN + "Pos2 已设置: " + locationToString(pos2));
                return true;
            } else if (subCommand.equalsIgnoreCase("add")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用方法: /eggpve add <id> <title>");
                    return true;
                }

                String id = args[1];
                StringBuilder titleBuilder = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i != 2) {
                        titleBuilder.append(" ");
                    }
                    titleBuilder.append(args[i]);
                }
                String title = titleBuilder.toString();

                Location pos1 = pos1Map.get(player.getUniqueId());
                Location pos2 = pos2Map.get(player.getUniqueId());

                if (pos1 == null || pos2 == null) {
                    player.sendMessage(ChatColor.RED + "请先设置 Pos1 和 Pos2。");
                    return true;
                }

                Region region = new Region(id, pos1, pos2, title);
                titleRegions.put(id, region);
                saveRegion(id, region);
                player.sendMessage(ChatColor.GREEN + "标题区域已成功创建。");
                return true;
            } else if (subCommand.equalsIgnoreCase("list")) {
                player.sendMessage(ChatColor.YELLOW + "已创建的标题区域:");
                titleRegions.forEach((regionId, region) -> {
                    player.sendMessage(ChatColor.AQUA + regionId + " - " +
                            region.locationToString(region.getPos1()) + " to " +
                            region.locationToString(region.getPos2()) + " - " +
                            ChatColor.YELLOW + region.getTitle());
                });
                return true;
            } else if (subCommand.equalsIgnoreCase("del")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用方法: /eggpve del <id>");
                    return true;
                }

                String id = args[1];
                if (titleRegions.remove(id) != null) {
                    regionsConfig.set(id, null);
                    saveRegionsFile();
                    player.sendMessage(ChatColor.GREEN + "标题区域已成功删除。");
                } else {
                    player.sendMessage(ChatColor.RED + "选区不存在。");
                }
                return true;
            } else if (subCommand.equalsIgnoreCase("reload")) {
                loadRegions();
                player.sendMessage(ChatColor.GREEN + "插件配置已重新加载。");
                return true;
            } else if (subCommand.equalsIgnoreCase("tp")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用方法: /eggpve tp <id>");
                    return true;
                }

                String id = args[1];
                Region region = titleRegions.get(id);

                if (region == null) {
                    player.sendMessage(ChatColor.RED + "指定区域不存在。");
                    return true;
                }

                Location center = region.getCenter();
                player.teleport(center);
                player.sendMessage(ChatColor.GREEN + "你已传送到区域 " + id);
                return true;
            } else if (subCommand.equalsIgnoreCase("update")) {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "使用方法: /eggpve update <id> <new_title>");
                    return true;
                }

                String id = args[1];
                StringBuilder titleBuilder = new StringBuilder();
                for (int i = 2; i < args.length; i++) {
                    if (i != 2) {
                        titleBuilder.append(" ");
                    }
                    titleBuilder.append(args[i]);
                }
                String newTitle = titleBuilder.toString();

                Region region = titleRegions.get(id);
                if (region != null) {
                    region.setTitle(newTitle);
                    saveRegion(id, region);
                    player.sendMessage(ChatColor.GREEN + "区域 " + id + " 的标题已更新。");
                } else {
                    player.sendMessage(ChatColor.RED + "指定区域不存在。");
                }
                return true;
            } else if (subCommand.equalsIgnoreCase("show")) {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "使用方法: /eggpve show <id>");
                    return true;
                }

                String id = args[1];
                Region region = titleRegions.get(id);

                if (region == null) {
                    player.sendMessage(ChatColor.RED + "指定区域不存在。");
                    return true;
                }

                showRegionBoundary(player, region);
                player.sendMessage(ChatColor.GREEN + "正在显示区域 " + id + " 的边界。");
                return true;
            }
        }

        return false;
    }

    private void createRegionsFile() {
        regionsFile = new File(getDataFolder(), "regions.yml");
        if (!regionsFile.exists()) {
            regionsFile.getParentFile().mkdirs();
            saveResource("regions.yml", false);
        }

        regionsConfig = YamlConfiguration.loadConfiguration(regionsFile);
    }

    private void saveRegionsFile() {
        try {
            regionsConfig.save(regionsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveRegion(String id, Region region) {
        regionsConfig.set(id + ".pos1", locationToString(region.getPos1()));
        regionsConfig.set(id + ".pos2", locationToString(region.getPos2()));
        regionsConfig.set(id + ".title", region.getTitle());
        saveRegionsFile();
    }

    private void loadRegions() {
        titleRegions.clear();

        for (String id : regionsConfig.getKeys(false)) {
            String title = regionsConfig.getString(id + ".title");
            Location pos1 = stringToLocation(regionsConfig.getString(id + ".pos1"));
            Location pos2 = stringToLocation(regionsConfig.getString(id + ".pos2"));
            if (pos1 == null || pos2 == null) {
                continue;
            }

            Region region = new Region(id, pos1, pos2, title);
            titleRegions.put(id, region);
        }
    }

    private void saveRegions() {
        titleRegions.forEach((id, region) -> saveRegion(id, region));
    }

    private Location stringToLocation(String locString) {
        String[] parts = locString.split(",");
        return new Location(Bukkit.getWorld(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
    }

    private String locationToString(Location location) {
        return String.format("%s,%.2f,%.2f,%.2f",
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ());
    }

    private void showRegionBoundary(Player player, Region region) {
        Location pos1 = region.getPos1();
        Location pos2 = region.getPos2();

        double x1 = Math.min(pos1.getX(), pos2.getX());
        double x2 = Math.max(pos1.getX(), pos2.getX());
        double y1 = Math.min(pos1.getY(), pos2.getY());
        double y2 = Math.max(pos1.getY(), pos2.getY());
        double z1 = Math.min(pos1.getZ(), pos2.getZ());
        double z2 = Math.max(pos1.getZ(), pos2.getZ());

        for (double x = x1; x <= x2; x += 0.5) {
            player.spawnParticle(Particle.VILLAGER_HAPPY, x, y1, z1, 1);
            player.spawnParticle(Particle.VILLAGER_HAPPY, x, y1, z2, 1);
            player.spawnParticle(Particle.VILLAGER_HAPPY, x, y2, z1, 1);
            player.spawnParticle(Particle.VILLAGER_HAPPY, x, y2, z2, 1);
        }

        for (double y = y1; y <= y2; y += 0.5) {
            player.spawnParticle(Particle.VILLAGER_HAPPY, x1, y, z1, 1);
            player.spawnParticle(Particle.VILLAGER_HAPPY, x1, y, z2, 1);
            player.spawnParticle(Particle.VILLAGER_HAPPY, x2, y, z1, 1);
            player.spawnParticle(Particle.VILLAGER_HAPPY, x2, y, z2, 1);
        }

        for (double z = z1; z <= z2; z += 0.5) {
            player.spawnParticle(Particle.VILLAGER_HAPPY, x1, y1, z, 1);
            player.spawnParticle(Particle.VILLAGER_HAPPY, x1, y2, z, 1);
            player.spawnParticle(Particle.VILLAGER_HAPPY, x2, y1, z, 1);
            player.spawnParticle(Particle.VILLAGER_HAPPY, x2, y2, z, 1);
        }
    }

    private void showAnimatedTitle(Player player, String title) {
        final int fullLength = title.length();
        AtomicInteger index = new AtomicInteger(0);
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (index.get() < fullLength) {
                String currentTitle = title.substring(0, index.incrementAndGet());
                Title animatedTitle = Title.title(
                        Component.text(currentTitle)
                                .color(NamedTextColor.RED)
//                                .decorate(TextDecoration.BOLD)
                        , // 直接显示当前的整个标题字符串
                        Component.text(""), // 副标题
                        Title.Times.times(
                                Duration.ofMillis(0), // 进场时间
                                Duration.ofMillis(1500), // 显示持续时间
                                Duration.ofMillis(500) // 场退时间
                        )
                );
                player.showTitle(animatedTitle);
            } else {
                task.cancel();
            }
        }, 0L, 5L); // 每5个tick（250ms）更新一次
    }

    private static class Region {
        private final String id;
        private final Location pos1;
        private final Location pos2;
        private String title;

        public Region(String id, Location pos1, Location pos2, String title) {
            this.id = id;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public Location getPos1() {
            return pos1;
        }

        public Location getPos2() {
            return pos2;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public boolean isInRegion(Location location) {
            double x1 = Math.min(pos1.getX(), pos2.getX());
            double x2 = Math.max(pos1.getX(), pos2.getX());
            double y1 = Math.min(pos1.getY(), pos2.getY());
            double y2 = Math.max(pos1.getY(), pos2.getY());
            double z1 = Math.min(pos1.getZ(), pos2.getZ());
            double z2 = Math.max(pos1.getZ(), pos2.getZ());

            return location.getX() >= x1 && location.getX() <= x2 &&
                    location.getY() >= y1 && location.getY() <= y2 &&
                    location.getZ() >= z1 && location.getZ() <= z2;
        }

        public Location getCenter() {
            double centerX = (pos1.getX() + pos2.getX()) / 2;
            double centerY = (pos1.getY() + pos2.getY()) / 2;
            double centerZ = (pos1.getZ() + pos2.getZ()) / 2;
            return new Location(pos1.getWorld(), centerX, centerY, centerZ);
        }

        public String locationToString(Location location) {
            return String.format("%s,%.2f,%.2f,%.2f",
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ());
        }
    }
}
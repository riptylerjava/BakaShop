package dev.tyler.shop;

import com.earth2me.essentials.api.Economy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class BakaShopPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String BUNDLED_ESSENTIALS_RESOURCE = "bundled/EssentialsX-2.22.0.jar";
    private static final String BUNDLED_ESSENTIALS_FILE = "EssentialsX-2.22.0.jar";
    private static final String BUNDLED_ESSENTIALS_VERSION = "2.22.0";
    private static final Pattern ITEM_PATH = Pattern.compile("(?i)^page(\\d+)\\.items\\.(\\d+)$");
    private static final int SELL_CONFIRM_SLOT = 53;
    private static final int SPAWNER_XP_SLOT = 11;
    private static final int SPAWNER_COLLECT_SLOT = 15;
    private static final int PURCHASE_SET_64_SLOT = 4;
    private static final int PURCHASE_BUY_SLOT = 13;
    private static final int PURCHASE_MINUS_10_SLOT = 18;
    private static final int PURCHASE_MINUS_1_SLOT = 19;
    private static final int PURCHASE_PREVIEW_SLOT = 22;
    private static final int PURCHASE_PLUS_1_SLOT = 25;
    private static final int PURCHASE_PLUS_10_SLOT = 26;
    private static final int LATEST_CONFIG_VERSION = 4;
    private static final List<Integer> CATEGORY_TRADE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    private static final Map<String, Integer> DEFAULT_CATEGORY_SLOTS = Map.of(
            "blocks", 10,
            "woods", 11,
            "materials", 12,
            "gear", 13,
            "food", 14,
            "farm", 15,
            "redstone", 16,
            "spawner", 22
    );
    private static NamespacedKey customItemKey;
    private static NamespacedKey spawnerTypeKey;

    private final Map<String, ShopCategory> categoriesById = new LinkedHashMap<>();
    private final Map<Material, ShopItem> sellItemsByMaterial = new LinkedHashMap<>();
    private final Map<String, VirtualSpawner> virtualSpawners = new LinkedHashMap<>();
    private final Map<UUID, PickMode> shellPickModes = new HashMap<>();
    private final Set<UUID> suppressCategoryReturn = new HashSet<>();
    private final Set<UUID> suppressPurchaseReturn = new HashSet<>();
    private final Set<String> pluginBreakingBlocks = new HashSet<>();
    private String mainTitle;
    private int mainSize;
    private int categorySize;
    private boolean internalSmartSpawnersEnabled = true;
    private File spawnerDataFile;
    private FileConfiguration spawnerDataConfig;

    @Override
    public void onLoad() {
        installBundledEssentials();
    }

    @Override
    public void onEnable() {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled()) {
            getLogger().severe("EssentialsX is not enabled. If BakaShop just installed the bundled copy, restart the server.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        customItemKey = new NamespacedKey(this, "custom_item");
        spawnerTypeKey = new NamespacedKey(this, "spawner_type");
        saveDefaultConfig();
        resetOldConfig();
        ensureShellsDefault();
        fixDefaultCategorySlots();
        internalSmartSpawnersEnabled = shouldEnableInternalSmartSpawners();
        if (internalSmartSpawnersEnabled) {
            loadSpawnerData();
        } else {
            virtualSpawners.clear();
        }
        loadShop();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("bakashop")).setExecutor(this);
        Objects.requireNonNull(getCommand("bakashop")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("editshop")).setExecutor(this);
        Objects.requireNonNull(getCommand("editshop")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("sell")).setExecutor(this);
        Objects.requireNonNull(getCommand("sell")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("amethystpick")).setExecutor(this);
        Objects.requireNonNull(getCommand("amethystpick")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("enableshells")).setExecutor(this);
        Objects.requireNonNull(getCommand("enableshells")).setTabCompleter(this);
        if (internalSmartSpawnersEnabled) {
            Bukkit.getScheduler().runTask(this, this::scanLoadedSpawners);
            Bukkit.getScheduler().runTaskTimer(this, this::tickVirtualSpawners, 600L, 600L);
        }
    }

    @Override
    public void onDisable() {
        if (internalSmartSpawnersEnabled) {
            saveSpawnerData();
        }
    }

    private boolean shouldEnableInternalSmartSpawners() {
        if (!getConfig().getBoolean("smart-spawners.internal-enabled", true)) {
            getLogger().info("Internal smart spawners are disabled in config.yml.");
            return false;
        }
        if (!getConfig().getBoolean("smart-spawners.disable-when-external-installed", true)) {
            return true;
        }

        List<String> externalNames = getConfig().getStringList("smart-spawners.external-plugin-names");
        if (externalNames.isEmpty()) {
            externalNames = List.of("SmartSpawner", "SmartSpawners", "SmartSpawnerSystem", "SmartSpawner-System");
        }

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            String installed = normalizePluginName(plugin.getName());
            for (String externalName : externalNames) {
                if (installed.equals(normalizePluginName(externalName))) {
                    getLogger().info("Detected external smart spawner plugin '" + plugin.getName() + "'. Internal smart spawners are disabled.");
                    return false;
                }
            }
        }
        return true;
    }

    private String normalizePluginName(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("editshop")) {
            return handleEditShop(sender, args);
        }
        if (command.getName().equalsIgnoreCase("enableshells")) {
            return handleEnableShells(sender);
        }
        if (command.getName().equalsIgnoreCase("sell")) {
            return handleSellCommand(sender);
        }
        if (command.getName().equalsIgnoreCase("amethystpick")) {
            return handleAmethystPick(sender, args);
        }
        return handleShop(sender, args);
    }

    private void ensureShellsDefault() {
        if (!getConfig().contains("shells.enabled")) {
            getConfig().set("shells.enabled", false);
            saveConfig();
        }
    }

    private void resetOldConfig() {
        int configVersion = getConfig().getInt("config-version", 0);
        if (configVersion >= LATEST_CONFIG_VERSION) {
            return;
        }

        File configFile = new File(getDataFolder(), "config.yml");
        File backupFile = new File(getDataFolder(), "config.old-v" + configVersion + "-" + System.currentTimeMillis() + ".yml");
        try {
            if (configFile.exists()) {
                Files.move(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().warning("Old config.yml detected. Backed it up as " + backupFile.getName());
            }
            saveResource("config.yml", true);
            reloadConfig();
            getLogger().warning("Reset config.yml to version " + LATEST_CONFIG_VERSION + ".");
        } catch (IOException exception) {
            getLogger().severe("Failed to reset old config.yml: " + exception.getMessage());
        }
    }

    private void installBundledEssentials() {
        File pluginsFolder = getDataFolder().getParentFile();
        if (pluginsFolder == null) {
            getLogger().warning("Could not find the plugins folder, so bundled EssentialsX was not checked.");
            return;
        }

        InstalledPlugin installed = findInstalledPlugin(pluginsFolder, "Essentials");
        if (installed == null) {
            extractBundledEssentials(pluginsFolder);
            getLogger().warning("Bundled EssentialsX " + BUNDLED_ESSENTIALS_VERSION + " was installed. Restart the server so Paper can load it.");
            return;
        }

        int versionCompare = compareVersions(installed.version(), BUNDLED_ESSENTIALS_VERSION);
        if (versionCompare >= 0) {
            getLogger().info("Found EssentialsX " + installed.version() + ". Bundled EssentialsX is disabled.");
            return;
        }

        File disabledFile = new File(installed.jar().getParentFile(), installed.jar().getName() + ".old-disabled-by-BakaShop");
        try {
            Files.move(installed.jar().toPath(), disabledFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            extractBundledEssentials(pluginsFolder);
            getLogger().warning("Found older EssentialsX " + installed.version() + ". Moved it to " + disabledFile.getName() + " and installed bundled EssentialsX " + BUNDLED_ESSENTIALS_VERSION + ".");
            getLogger().warning("Restart the server so Paper stops using the old EssentialsX jar.");
        } catch (IOException exception) {
            getLogger().severe("Failed to replace old EssentialsX: " + exception.getMessage());
        }
    }

    private InstalledPlugin findInstalledPlugin(File pluginsFolder, String pluginName) {
        File[] jars = pluginsFolder.listFiles((folder, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
        if (jars == null) {
            return null;
        }

        for (File jar : jars) {
            try (JarFile jarFile = new JarFile(jar)) {
                JarEntry pluginFile = jarFile.getJarEntry("plugin.yml");
                if (pluginFile == null) {
                    continue;
                }
                try (InputStream inputStream = jarFile.getInputStream(pluginFile);
                     InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    FileConfiguration pluginConfig = YamlConfiguration.loadConfiguration(reader);
                    String name = pluginConfig.getString("name", "");
                    String version = pluginConfig.getString("version", "0");
                    if (normalizePluginName(name).equals(normalizePluginName(pluginName))) {
                        return new InstalledPlugin(jar, version);
                    }
                }
            } catch (IOException exception) {
                getLogger().warning("Could not read " + jar.getName() + " while checking EssentialsX: " + exception.getMessage());
            }
        }
        return null;
    }

    private void extractBundledEssentials(File pluginsFolder) {
        File target = new File(pluginsFolder, BUNDLED_ESSENTIALS_FILE);
        try (InputStream inputStream = getResource(BUNDLED_ESSENTIALS_RESOURCE)) {
            if (inputStream == null) {
                getLogger().severe("Bundled EssentialsX jar is missing from BakaShop.");
                return;
            }
            Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            getLogger().severe("Failed to install bundled EssentialsX: " + exception.getMessage());
        }
    }

    private int compareVersions(String first, String second) {
        List<Integer> firstParts = versionParts(first);
        List<Integer> secondParts = versionParts(second);
        int max = Math.max(firstParts.size(), secondParts.size());
        for (int index = 0; index < max; index++) {
            int firstPart = index < firstParts.size() ? firstParts.get(index) : 0;
            int secondPart = index < secondParts.size() ? secondParts.get(index) : 0;
            if (firstPart != secondPart) {
                return Integer.compare(firstPart, secondPart);
            }
        }
        return 0;
    }

    private List<Integer> versionParts(String version) {
        List<Integer> parts = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(version == null ? "" : version);
        while (matcher.find()) {
            parts.add(parsePositiveInt(matcher.group(), 0));
        }
        return parts;
    }

    private void fixDefaultCategorySlots() {
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : DEFAULT_CATEGORY_SLOTS.entrySet()) {
            String path = "categories." + entry.getKey() + ".slot";
            if (getConfig().contains(path) && getConfig().getInt(path) != entry.getValue()) {
                getConfig().set(path, entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            saveConfig();
        }
    }

    private boolean handleEnableShells(CommandSender sender) {
        if (!sender.hasPermission("bakashop.enableshells")) {
            send(sender, "no-permission");
            return true;
        }
        if (shellsEnabled()) {
            sender.sendMessage(color("&aShell shops are already enabled."));
            return true;
        }

        getConfig().set("shells.enabled", true);
        saveConfig();
        reloadConfig();
        loadShop();
        sender.sendMessage(color("&aShell shops are now enabled."));
        return true;
    }

    private boolean handleAmethystPick(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "player-only");
            return true;
        }
        if (args.length != 1 || (!args[0].equalsIgnoreCase("wall") && !args[0].equalsIgnoreCase("floor"))) {
            player.sendMessage(color("&cUsage: /amethystpick <wall|floor>"));
            return true;
        }

        PickMode mode = args[0].equalsIgnoreCase("wall") ? PickMode.WALL : PickMode.FLOOR;
        shellPickModes.put(player.getUniqueId(), mode);
        player.sendMessage(color("&aAmethyst pickaxe mode set to &e" + mode.name().toLowerCase(Locale.ROOT) + "&a."));
        return true;
    }

    private boolean handleSellCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("bakashop.sell")) {
            send(player, "no-permission");
            return true;
        }

        openSellMenu(player);
        return true;
    }

    private boolean handleShop(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("bakashop.reload")) {
                send(sender, "no-permission");
                return true;
            }
            reloadConfig();
            resetOldConfig();
            fixDefaultCategorySlots();
            loadShop();
            send(sender, "reloaded");
            return true;
        }

        if (!(sender instanceof Player player)) {
            send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("bakashop.use")) {
            send(player, "no-permission");
            return true;
        }
        if (categoriesById.isEmpty()) {
            send(player, "invalid-config");
            return true;
        }

        openMainMenu(player);
        return true;
    }

    private boolean handleEditShop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bakashop.edit")) {
            send(sender, "no-permission");
            return true;
        }

        if (args.length >= 5 && args[0].equalsIgnoreCase("additem")) {
            return addItem(sender, args);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("removeitem")) {
            return removeItem(sender, args);
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("edititem")) {
            return editItem(sender, args);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("removecoins")) {
            return removeCoins(sender, args);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addcoins")) {
            return addCoins(sender, args);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("addshells")) {
            return addShells(sender, args);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("removeshells")) {
            return removeShells(sender, args);
        }

        sender.sendMessage(color("&cUsage: /editshop additem <category> <material> <buy> <sell>"));
        sender.sendMessage(color("&cUsage: /editshop removeitem <category> page<page>.items.<slot>"));
        sender.sendMessage(color("&cUsage: /editshop edititem <category> page<page>.items.<slot> <buy|sell> set <price>"));
        sender.sendMessage(color("&cUsage: /editshop removecoins <player> <amount>"));
        sender.sendMessage(color("&cUsage: /editshop addcoins <player> <amount>"));
        sender.sendMessage(color("&cUsage: /editshop addshells <player> <count>"));
        sender.sendMessage(color("&cUsage: /editshop removeshells <player> <count>"));
        return true;
    }

    private boolean addItem(CommandSender sender, String[] args) {
        String categoryId = findCategoryId(args[1]);
        if (categoryId == null) {
            sender.sendMessage(color("&cUnknown category: &e" + args[1]));
            return true;
        }

        Material material = findMaterial(args[2]);
        if (material == null || material.isAir()) {
            sender.sendMessage(color("&cUnknown material: &e" + args[2]));
            return true;
        }

        BigDecimal buy = parsePrice(sender, args[3]);
        BigDecimal sell = parsePrice(sender, args[4]);
        if (buy == null || sell == null) {
            return true;
        }

        migrateLegacyItems(categoryId);
        PageSlot target = firstEmptySlot(categoryId);
        String basePath = "categories." + categoryId + ".pages." + target.page() + ".items." + target.slot();
        getConfig().set(basePath + ".material", material.name());
        getConfig().set(basePath + ".amount", 1);
        getConfig().set(basePath + ".buy", buy.doubleValue());
        getConfig().set(basePath + ".sell", sell.doubleValue());
        getConfig().set(basePath + ".name", "&f" + prettify(material));
        getConfig().set(basePath + ".lore", List.of(
                "&7Left click: buy for &e%buy%",
                "&7Right click: sell for &e%sell%",
                "&7Shift click trades 64x."
        ));
        saveAndReload();
        sender.sendMessage(color("&aAdded &e" + prettify(material) + " &ato &e" + categoryId + " page" + target.page() + ".items." + target.slot() + "&a."));
        return true;
    }

    private boolean removeItem(CommandSender sender, String[] args) {
        String categoryId = findCategoryId(args[1]);
        PageSlot target = parseItemPath(args[2]);
        if (categoryId == null || target == null) {
            sender.sendMessage(color("&cUsage: /editshop removeitem <category> page<page>.items.<slot>"));
            return true;
        }

        migrateLegacyItems(categoryId);
        String basePath = configItemPath(categoryId, target);
        if (!getConfig().isConfigurationSection(basePath)) {
            sender.sendMessage(color("&cNo item exists at &e" + args[2] + "&c."));
            return true;
        }

        getConfig().set(basePath, null);
        saveAndReload();
        sender.sendMessage(color("&aRemoved item at &e" + categoryId + " " + args[2] + "&a."));
        return true;
    }

    private boolean editItem(CommandSender sender, String[] args) {
        String categoryId = findCategoryId(args[1]);
        PageSlot target = parseItemPath(args[2]);
        String field = args[3].toLowerCase(Locale.ROOT);
        if (categoryId == null || target == null || (!field.equals("buy") && !field.equals("sell")) || !args[4].equalsIgnoreCase("set")) {
            sender.sendMessage(color("&cUsage: /editshop edititem <category> page<page>.items.<slot> <buy|sell> set <price>"));
            return true;
        }

        BigDecimal price = parsePrice(sender, args[5]);
        if (price == null) {
            return true;
        }

        migrateLegacyItems(categoryId);
        String basePath = configItemPath(categoryId, target);
        if (!getConfig().isConfigurationSection(basePath)) {
            sender.sendMessage(color("&cNo item exists at &e" + args[2] + "&c."));
            return true;
        }

        getConfig().set(basePath + "." + field, price.doubleValue());
        saveAndReload();
        sender.sendMessage(color("&aSet &e" + field + " &ato &e" + price + " &afor &e" + categoryId + " " + args[2] + "&a."));
        return true;
    }

    private boolean removeCoins(CommandSender sender, String[] args) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        BigDecimal amount = parsePrice(sender, args[2]);
        if (amount == null) {
            return true;
        }

        try {
            if (!Economy.hasEnough(target.getUniqueId(), amount)) {
                sender.sendMessage(color("&cThat player does not have &e" + Economy.format(amount) + "&c."));
                return true;
            }
            Economy.subtract(target.getUniqueId(), amount);
            sender.sendMessage(color("&aRemoved &e" + Economy.format(amount) + " &afrom &e" + target.getName() + "&a."));
        } catch (Exception exception) {
            getLogger().warning("Failed to remove coins from " + args[1] + ": " + exception.getMessage());
            sender.sendMessage(color("&cEssentialsX economy rejected that transaction."));
        }
        return true;
    }

    private boolean addCoins(CommandSender sender, String[] args) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        BigDecimal amount = parsePrice(sender, args[2]);
        if (amount == null) {
            return true;
        }

        try {
            Economy.add(target.getUniqueId(), amount);
            sender.sendMessage(color("&aAdded &e" + Economy.format(amount) + " &ato &e" + target.getName() + "&a."));
        } catch (Exception exception) {
            getLogger().warning("Failed to add coins to " + args[1] + ": " + exception.getMessage());
            sender.sendMessage(color("&cEssentialsX economy rejected that transaction."));
        }
        return true;
    }

    private boolean addShells(CommandSender sender, String[] args) {
        int amount = parsePositiveInt(args[2], -1);
        if (amount <= 0) {
            sender.sendMessage(color("&cShell count must be a positive whole number."));
            return true;
        }

        Currency shellCurrency = findPlaceholderCurrency();
        if (shellCurrency == null) {
            sender.sendMessage(color("&cNo shell currency is configured."));
            return true;
        }

        try {
            dispatchCurrencyCommand(args[1], "", shellCurrency.giveCommand(), amount);
            sender.sendMessage(color("&aAdded &e" + amount + " &ashells to &e" + args[1] + "&a."));
        } catch (Exception exception) {
            getLogger().warning("Failed to add shells to " + args[1] + ": " + exception.getMessage());
            sender.sendMessage(color("&cShell command failed. Check currency-give-command in config.yml."));
        }
        return true;
    }

    private boolean removeShells(CommandSender sender, String[] args) {
        int amount = parsePositiveInt(args[2], -1);
        if (amount <= 0) {
            sender.sendMessage(color("&cShell count must be a positive whole number."));
            return true;
        }

        Currency shellCurrency = findPlaceholderCurrency();
        if (shellCurrency == null) {
            sender.sendMessage(color("&cNo shell currency is configured."));
            return true;
        }

        try {
            dispatchCurrencyCommand(args[1], "", shellCurrency.takeCommand(), amount);
            sender.sendMessage(color("&aRemoved &e" + amount + " &ashells from &e" + args[1] + "&a."));
        } catch (Exception exception) {
            getLogger().warning("Failed to remove shells from " + args[1] + ": " + exception.getMessage());
            sender.sendMessage(color("&cShell command failed. Check currency-take-command in config.yml."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("bakashop")) {
            if (args.length == 1 && sender.hasPermission("bakashop.reload")) {
                return Collections.singletonList("reload");
            }
            return Collections.emptyList();
        }
        if (command.getName().equalsIgnoreCase("amethystpick")) {
            return args.length == 1 ? List.of("wall", "floor") : Collections.emptyList();
        }
        if (command.getName().equalsIgnoreCase("enableshells")) {
            return Collections.emptyList();
        }

        if (!sender.hasPermission("bakashop.edit")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return List.of("additem", "removeitem", "edititem", "removecoins", "addcoins", "addshells", "removeshells");
        }
        if (args.length == 2 && List.of("additem", "removeitem", "edititem").contains(args[0].toLowerCase(Locale.ROOT))) {
            return new ArrayList<>(categoriesById.keySet());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("edititem")) {
            return List.of("buy", "sell");
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("edititem")) {
            return Collections.singletonList("set");
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().equalsIgnoreCase("/sell")) {
            event.setCancelled(true);
            handleSellCommand(event.getPlayer());
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (internalSmartSpawnersEnabled && !shouldEnableInternalSmartSpawners()) {
            internalSmartSpawnersEnabled = false;
            virtualSpawners.clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (handleSpawnerBreak(event)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        String custom = customItem(tool);
        if (custom == null) {
            return;
        }

        String blockKey = blockKey(event.getBlock());
        if (pluginBreakingBlocks.contains(blockKey)) {
            return;
        }

        if (custom.equals("amethyst_pickaxe") || custom.equals("shell_pickaxe")) {
            breakShellPickArea(player, event.getBlock(), tool);
        } else if (custom.equals("amethyst_axe") || custom.equals("shell_axe")) {
            breakShellAxeVein(player, event.getBlock(), tool);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!internalSmartSpawnersEnabled) {
            return;
        }

        ItemStack item = event.getItemInHand();
        String type = spawnerType(item);
        if (event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }

        Block block = event.getBlockPlaced();
        EntityType entityType = parseEntityType(type);
        if (block.getState() instanceof CreatureSpawner spawner) {
            if (entityType == null) {
                entityType = spawner.getSpawnedType();
            } else {
                spawner.setSpawnedType(entityType);
            }
            spawner.setDelay(Integer.MAX_VALUE);
            if (entityType != null) {
                spawner.getPersistentDataContainer().set(spawnerTypeKey, PersistentDataType.STRING, entityType.name());
            }
            spawner.update(true);
        }

        if (entityType == null) {
            return;
        }

        virtualSpawners.put(blockKey(block), new VirtualSpawner(entityType, 0, new LinkedHashMap<>()));
        saveSpawnerData();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!internalSmartSpawnersEnabled) {
            return;
        }
        registerSpawners(event.getChunk());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!internalSmartSpawnersEnabled) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.SPAWNER) {
            return;
        }
        String key = blockKey(event.getClickedBlock());
        VirtualSpawner spawner = virtualSpawners.get(key);
        if (spawner == null) {
            spawner = createSmartSpawner(event.getClickedBlock());
            if (spawner == null) {
                return;
            }
            virtualSpawners.put(key, spawner);
            saveSpawnerData();
        }

        event.setCancelled(true);
        openSpawnerMenu(event.getPlayer(), key, spawner);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!internalSmartSpawnersEnabled) {
            return;
        }

        Block block = event.getSpawner().getBlock();
        String key = blockKey(block);
        if (!virtualSpawners.containsKey(key)) {
            VirtualSpawner spawner = createSmartSpawner(block);
            if (spawner != null) {
                virtualSpawners.put(key, spawner);
                saveSpawnerData();
            }
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        int rawSlot = event.getRawSlot();
        boolean clickedTopInventory = rawSlot >= 0 && rawSlot < topInventory.getSize();
        if (holder instanceof BakaSellHolder) {
            if (rawSlot == SELL_CONFIRM_SLOT) {
                event.setCancelled(true);
                processSellInventory(player, topInventory);
                player.closeInventory();
            }
            return;
        }

        if (holder instanceof BakaSpawnerHolder spawnerHolder) {
            event.setCancelled(true);
            if (rawSlot == SPAWNER_XP_SLOT) {
                collectSpawnerXp(player, spawnerHolder.locationKey());
            } else if (rawSlot == SPAWNER_COLLECT_SLOT) {
                collectSpawnerDrops(player, spawnerHolder.locationKey());
            }
            return;
        }

        if (holder instanceof BakaPurchaseHolder purchaseHolder) {
            event.setCancelled(true);
            if (clickedTopInventory) {
                handlePurchaseMenuClick(player, purchaseHolder, rawSlot);
            }
            return;
        }

        if (holder instanceof BakaMainMenuHolder) {
            event.setCancelled(true);
            if (clickedTopInventory) {
                openClickedCategory(player, rawSlot);
            }
            return;
        }

        if (!(holder instanceof BakaCategoryHolder categoryHolder)) {
            return;
        }

        event.setCancelled(true);
        ShopCategory category = categoriesById.get(categoryHolder.categoryId());
        if (category == null) {
            return;
        }

        if (!clickedTopInventory) {
            return;
        }

        if (rawSlot == categoryBackSlot()) {
            suppressCategoryReturn.add(player.getUniqueId());
            openMainMenu(player);
            return;
        }
        if (rawSlot == categoryPreviousSlot() && categoryHolder.page() > 1) {
            suppressCategoryReturn.add(player.getUniqueId());
            openCategory(player, category, categoryHolder.page() - 1);
            return;
        }
        if (rawSlot == categoryNextSlot() && category.hasPage(categoryHolder.page() + 1)) {
            suppressCategoryReturn.add(player.getUniqueId());
            openCategory(player, category, categoryHolder.page() + 1);
            return;
        }

        ShopItem shopItem = category.itemAt(categoryHolder.page(), rawSlot);
        if (shopItem == null) {
            return;
        }

        ClickType click = event.getClick();
        if (click.isLeftClick()) {
            suppressCategoryReturn.add(player.getUniqueId());
            openPurchaseMenu(player, categoryHolder.categoryId(), categoryHolder.page(), rawSlot, 1);
        } else if (click.isRightClick() && shopItem.currency().isMoney()) {
            sell(player, shopItem, click.isShiftClick() ? 64 : 1);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BakaSellHolder) {
            processSellInventory(player, event.getInventory());
            return;
        }

        if (holder instanceof BakaPurchaseHolder purchaseHolder) {
            if (suppressPurchaseReturn.remove(player.getUniqueId())) {
                return;
            }

            ShopCategory category = categoriesById.get(purchaseHolder.categoryId());
            if (category == null || !category.hasPage(purchaseHolder.page())) {
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline()) {
                    suppressCategoryReturn.add(player.getUniqueId());
                    openCategory(player, category, purchaseHolder.page());
                }
            });
            return;
        }

        if (holder instanceof BakaCategoryHolder) {
            if (suppressCategoryReturn.remove(player.getUniqueId())) {
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline() && !categoriesById.isEmpty()) {
                    openMainMenu(player);
                }
            });
        }
    }

    private void breakShellPickArea(Player player, Block origin, ItemStack tool) {
        PickMode mode = shellPickModes.getOrDefault(player.getUniqueId(), PickMode.FLOOR);
        List<Block> blocks = new ArrayList<>();
        if (mode == PickMode.FLOOR) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    blocks.add(origin.getRelative(x, 0, z));
                }
            }
        } else {
            boolean xWall = Math.abs(player.getLocation().getDirection().getX()) > Math.abs(player.getLocation().getDirection().getZ());
            for (int a = -1; a <= 1; a++) {
                for (int y = -1; y <= 1; y++) {
                    blocks.add(xWall ? origin.getRelative(0, y, a) : origin.getRelative(a, y, 0));
                }
            }
        }

        breakExtraBlocks(player, blocks, tool, 24);
    }

    private void breakShellAxeVein(Player player, Block origin, ItemStack tool) {
        if (!isLog(origin.getType())) {
            return;
        }

        List<Block> blocks = new ArrayList<>();
        Queue<Block> queue = new LinkedList<>();
        Set<String> seen = new HashSet<>();
        queue.add(origin);
        seen.add(blockKey(origin));

        while (!queue.isEmpty() && blocks.size() < 160) {
            Block current = queue.poll();
            blocks.add(current);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        Block next = current.getRelative(x, y, z);
                        String key = blockKey(next);
                        if (!seen.contains(key) && isLog(next.getType())) {
                            seen.add(key);
                            queue.add(next);
                        }
                    }
                }
            }
        }

        breakExtraBlocks(player, blocks, tool, 160);
    }

    private void breakExtraBlocks(Player player, List<Block> blocks, ItemStack tool, int maxBlocks) {
        int broken = 0;
        for (Block block : blocks) {
            if (broken >= maxBlocks || block.getType().isAir() || block.getType() == Material.BEDROCK || block.getType() == Material.BARRIER) {
                continue;
            }
            String key = blockKey(block);
            if (!pluginBreakingBlocks.add(key)) {
                continue;
            }
            try {
                block.breakNaturally(tool, true);
                broken++;
            } finally {
                pluginBreakingBlocks.remove(key);
            }
        }
    }

    private boolean handleSpawnerBreak(BlockBreakEvent event) {
        if (!internalSmartSpawnersEnabled) {
            return false;
        }

        if (event.getBlock().getType() != Material.SPAWNER) {
            return false;
        }

        String key = blockKey(event.getBlock());
        VirtualSpawner removed = virtualSpawners.remove(key);
        EntityType entityType = removed == null ? null : removed.entityType();

        if (entityType == null && event.getBlock().getState() instanceof CreatureSpawner spawner) {
            entityType = spawner.getSpawnedType();
        }

        if (!event.getPlayer().getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            if (removed != null) {
                saveSpawnerData();
            }
            return false;
        }

        event.setCancelled(true);
        event.setDropItems(false);
        event.getBlock().setType(Material.AIR);

        if (entityType != null) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), spawnerItem(entityType));
        }

        if (removed != null) {
            saveSpawnerData();
        }
        return true;
    }

    private ItemStack spawnerItem(EntityType entityType) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(entityType);
            blockStateMeta.setBlockState(spawner);
            blockStateMeta.getPersistentDataContainer().set(spawnerTypeKey, PersistentDataType.STRING, entityType.name());
            item.setItemMeta(blockStateMeta);
        }
        return item;
    }

    private VirtualSpawner createSmartSpawner(Block block) {
        if (block.getType() != Material.SPAWNER || !(block.getState() instanceof CreatureSpawner spawner)) {
            return null;
        }

        EntityType entityType = spawner.getSpawnedType();
        if (entityType == null) {
            return null;
        }

        spawner.setDelay(Integer.MAX_VALUE);
        spawner.getPersistentDataContainer().set(spawnerTypeKey, PersistentDataType.STRING, entityType.name());
        spawner.update(true);
        return new VirtualSpawner(entityType, 0, new LinkedHashMap<>());
    }

    private void scanLoadedSpawners() {
        if (!internalSmartSpawnersEnabled) {
            return;
        }

        int added = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                added += registerSpawners(chunk);
            }
        }
        if (added > 0) {
            saveSpawnerData();
            getLogger().info("Registered " + added + " existing smart spawners from loaded chunks.");
        }
    }

    private int registerSpawners(Chunk chunk) {
        if (!internalSmartSpawnersEnabled) {
            return 0;
        }

        int added = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof CreatureSpawner spawner)) {
                continue;
            }

            Block block = spawner.getBlock();
            String key = blockKey(block);
            if (virtualSpawners.containsKey(key)) {
                continue;
            }

            VirtualSpawner smartSpawner = createSmartSpawner(block);
            if (smartSpawner != null) {
                virtualSpawners.put(key, smartSpawner);
                added++;
            }
        }
        if (added > 0) {
            saveSpawnerData();
        }
        return added;
    }

    private void tickVirtualSpawners() {
        if (!internalSmartSpawnersEnabled) {
            return;
        }

        if (virtualSpawners.isEmpty()) {
            return;
        }

        for (VirtualSpawner spawner : virtualSpawners.values()) {
            spawner.xp(spawner.xp() + xpFor(spawner.entityType()));
            for (Map.Entry<Material, Integer> drop : dropsFor(spawner.entityType()).entrySet()) {
                spawner.items().merge(drop.getKey(), drop.getValue(), Integer::sum);
            }
        }
        saveSpawnerData();
    }

    private void openSpawnerMenu(Player player, String locationKey, VirtualSpawner spawner) {
        Inventory inventory = Bukkit.createInventory(new BakaSpawnerHolder(locationKey), 27, color("&5&lSmart Spawner"));
        ItemStack xpBottle = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta xpMeta = xpBottle.getItemMeta();
        if (xpMeta != null) {
            xpMeta.displayName(color("&aStored Experience"));
            xpMeta.lore(List.of(
                    color("&7Gathered XP: &e" + spawner.xp()),
                    color("&eClick to drop stored XP.")
            ));
            xpBottle.setItemMeta(xpMeta);
        }
        inventory.setItem(SPAWNER_XP_SLOT, xpBottle);

        ItemStack collect = new ItemStack(Material.MOSS_BLOCK);
        ItemMeta collectMeta = collect.getItemMeta();
        if (collectMeta != null) {
            collectMeta.displayName(color("&aCollect Drops"));
            collectMeta.lore(List.of(color("&7Click to collect stored drops.")));
            collect.setItemMeta(collectMeta);
        }
        inventory.setItem(SPAWNER_COLLECT_SLOT, collect);
        player.openInventory(inventory);
    }

    private void collectSpawnerXp(Player player, String locationKey) {
        VirtualSpawner spawner = virtualSpawners.get(locationKey);
        if (spawner == null) {
            player.closeInventory();
            return;
        }

        int xp = spawner.xp();
        if (xp <= 0) {
            player.sendMessage(color("&cThis smart spawner has no XP stored."));
            return;
        }

        player.getWorld().spawn(player.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(xp));
        spawner.xp(0);
        saveSpawnerData();
        player.sendMessage(color("&aDropped &e" + xp + " &astored XP."));
        player.closeInventory();
    }

    private void collectSpawnerDrops(Player player, String locationKey) {
        VirtualSpawner spawner = virtualSpawners.get(locationKey);
        if (spawner == null) {
            player.closeInventory();
            return;
        }

        for (Map.Entry<Material, Integer> item : spawner.items().entrySet()) {
            int remaining = item.getValue();
            while (remaining > 0) {
                int amount = Math.min(remaining, item.getKey().getMaxStackSize());
                returnToPlayer(player, new ItemStack(item.getKey(), amount));
                remaining -= amount;
            }
        }
        spawner.items().clear();
        saveSpawnerData();
        player.sendMessage(color("&aCollected the smart spawner drops."));
        player.closeInventory();
    }

    private Map<Material, Integer> dropsFor(EntityType entityType) {
        Map<Material, Integer> drops = new LinkedHashMap<>();
        switch (entityType) {
            case SKELETON -> {
                drops.put(Material.BONE, 5);
                drops.put(Material.ARROW, 5);
            }
            case CREEPER -> drops.put(Material.GUNPOWDER, 5);
            case ZOMBIE -> drops.put(Material.ROTTEN_FLESH, 5);
            case COW -> {
                drops.put(Material.LEATHER, 4);
                drops.put(Material.BEEF, 5);
            }
            case CHICKEN -> {
                drops.put(Material.FEATHER, 4);
                drops.put(Material.CHICKEN, 4);
            }
            case SPIDER -> drops.put(Material.STRING, 5);
            case IRON_GOLEM -> drops.put(Material.IRON_INGOT, 7);
            default -> drops.put(Material.ROTTEN_FLESH, 1);
        }
        return drops;
    }

    private int xpFor(EntityType entityType) {
        return entityType == EntityType.IRON_GOLEM ? 0 : 5;
    }

    private void loadSpawnerData() {
        spawnerDataFile = new File(getDataFolder(), "spawners.yml");
        spawnerDataConfig = YamlConfiguration.loadConfiguration(spawnerDataFile);
        virtualSpawners.clear();

        ConfigurationSection spawners = spawnerDataConfig.getConfigurationSection("spawners");
        if (spawners == null) {
            return;
        }

        for (String key : spawners.getKeys(false)) {
            EntityType entityType = parseEntityType(spawners.getString(key + ".type", ""));
            if (entityType == null) {
                continue;
            }
            Map<Material, Integer> items = new LinkedHashMap<>();
            ConfigurationSection drops = spawners.getConfigurationSection(key + ".items");
            if (drops != null) {
                for (String materialName : drops.getKeys(false)) {
                    Material material = Material.matchMaterial(materialName);
                    if (material != null) {
                        items.put(material, drops.getInt(materialName));
                    }
                }
            }
            virtualSpawners.put(key.replace(';', '.'), new VirtualSpawner(entityType, spawners.getInt(key + ".xp", 0), items));
        }
    }

    private void saveSpawnerData() {
        if (spawnerDataConfig == null || spawnerDataFile == null) {
            return;
        }

        spawnerDataConfig.set("spawners", null);
        for (Map.Entry<String, VirtualSpawner> entry : virtualSpawners.entrySet()) {
            String path = "spawners." + entry.getKey().replace('.', ';');
            VirtualSpawner spawner = entry.getValue();
            spawnerDataConfig.set(path + ".type", spawner.entityType().name());
            spawnerDataConfig.set(path + ".xp", spawner.xp());
            for (Map.Entry<Material, Integer> item : spawner.items().entrySet()) {
                spawnerDataConfig.set(path + ".items." + item.getKey().name(), item.getValue());
            }
        }

        try {
            spawnerDataConfig.save(spawnerDataFile);
        } catch (IOException exception) {
            getLogger().warning("Failed to save spawner data: " + exception.getMessage());
        }
    }

    private void loadShop() {
        loadShop(false);
    }

    private void loadShop(boolean alreadyReset) {
        mainTitle = getConfig().getString("shop.title", "&0SERVER SHOP");
        mainSize = normalizeSize(getConfig().getInt("shop.main-size", 27));
        categorySize = normalizeSize(getConfig().getInt("shop.category-size", 54));
        categoriesById.clear();
        sellItemsByMaterial.clear();

        ConfigurationSection categories = getConfig().getConfigurationSection("categories");
        if (categories == null) {
            resetInvalidConfig(alreadyReset, "Missing categories section");
            return;
        }

        for (String key : categories.getKeys(false)) {
            ConfigurationSection section = categories.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            Material icon = Material.matchMaterial(section.getString("icon", ""));
            int slot = section.getInt("slot", -1);
            if (icon == null || icon.isAir() || slot < 0 || slot >= mainSize) {
                getLogger().warning("Skipping invalid category: " + key);
                continue;
            }

            Currency currency = loadCurrency(section);
            if (currency.isPlaceholder() && !shellsEnabled()) {
                continue;
            }
            categoriesById.put(key, new ShopCategory(
                    key,
                    slot,
                    icon,
                    section.getString("title", prettify(icon)),
                    section.getString("menu-title", section.getString("title", prettify(icon))),
                    section.getStringList("lore"),
                    currency,
                    loadPages(key, section, currency)
            ));
        }

        if (categoriesById.isEmpty()) {
            resetInvalidConfig(alreadyReset, "No valid shop categories loaded");
            return;
        }

        boolean hasItems = categoriesById.values().stream()
                .flatMap(category -> category.pages().values().stream())
                .anyMatch(items -> !items.isEmpty());
        if (!hasItems) {
            resetInvalidConfig(alreadyReset, "No valid shop items loaded");
        }
    }

    private void resetInvalidConfig(boolean alreadyReset, String reason) {
        if (alreadyReset) {
            getLogger().severe("Default config also failed validation: " + reason);
            return;
        }

        getLogger().warning("Invalid config.yml detected: " + reason);
        File configFile = new File(getDataFolder(), "config.yml");
        File backupFile = new File(getDataFolder(), "config.invalid-" + System.currentTimeMillis() + ".yml");

        try {
            if (configFile.exists()) {
                Files.move(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().warning("Backed up invalid config to " + backupFile.getName());
            }
            saveResource("config.yml", true);
            reloadConfig();
            loadShop(true);
            getLogger().warning("Reset config.yml to the default bundled config.");
        } catch (IOException exception) {
            getLogger().severe("Failed to reset invalid config.yml: " + exception.getMessage());
        }
    }

    private Currency loadCurrency(ConfigurationSection categorySection) {
        String configured = categorySection.getString("currency", "money");
        if (configured == null || configured.equalsIgnoreCase("money") || configured.equalsIgnoreCase("essentials")) {
            return Currency.essentialsMoney();
        }

        if (configured.equalsIgnoreCase("placeholder") || configured.equalsIgnoreCase("shells")) {
            String placeholder = categorySection.getString("currency-placeholder", "%shells_short%");
            String singular = categorySection.getString("currency-singular", "shell");
            String plural = categorySection.getString("currency-plural", "shells");
            if (singular.contains("%")) {
                singular = "shell";
            }
            if (plural.contains("%")) {
                plural = "shells";
            }
            String takeCommand = categorySection.getString("currency-take-command", "");
            if (takeCommand.toLowerCase(Locale.ROOT).startsWith("shells remove ")) {
                takeCommand = "shells take " + takeCommand.substring("shells remove ".length());
            }
            String giveCommand = categorySection.getString("currency-give-command", "");
            return Currency.placeholder(placeholder, singular, plural, takeCommand, giveCommand);
        }

        Material material = findMaterial(configured);
        if (material == null || material.isAir()) {
            getLogger().warning("Invalid currency '" + configured + "', falling back to EssentialsX money.");
            return Currency.essentialsMoney();
        }

        String singular = categorySection.getString("currency-singular", prettify(material));
        String plural = categorySection.getString("currency-plural", singular + "s");
        return Currency.item(material, singular, plural);
    }

    private boolean shellsEnabled() {
        return getConfig().getBoolean("shells.enabled", false);
    }

    private Map<Integer, Map<Integer, ShopItem>> loadPages(String categoryId, ConfigurationSection categorySection, Currency currency) {
        Map<Integer, Map<Integer, ShopItem>> pages = new LinkedHashMap<>();
        ConfigurationSection pagesSection = categorySection.getConfigurationSection("pages");
        if (pagesSection != null) {
            for (String pageKey : pagesSection.getKeys(false)) {
                int page = parsePositiveInt(pageKey, -1);
                if (page > 0) {
                    pages.put(page, loadItems(categoryId, page, pagesSection.getConfigurationSection(pageKey + ".items"), currency));
                }
            }
        }

        if (pages.isEmpty()) {
            pages.put(1, loadItems(categoryId, 1, categorySection.getConfigurationSection("items"), currency));
        }
        return compactPages(pages);
    }

    private Map<Integer, Map<Integer, ShopItem>> compactPages(Map<Integer, Map<Integer, ShopItem>> pages) {
        List<ShopItem> allItems = new ArrayList<>();
        for (Map<Integer, ShopItem> pageItems : pages.values()) {
            allItems.addAll(pageItems.values());
        }

        Map<Integer, Map<Integer, ShopItem>> compacted = new LinkedHashMap<>();
        int page = 1;
        int slotIndex = 0;
        for (ShopItem item : allItems) {
            compacted.computeIfAbsent(page, ignored -> new LinkedHashMap<>())
                    .put(CATEGORY_TRADE_SLOTS.get(slotIndex), item);
            slotIndex++;
            if (slotIndex >= CATEGORY_TRADE_SLOTS.size()) {
                slotIndex = 0;
                page++;
            }
        }
        if (compacted.isEmpty()) {
            compacted.put(1, new LinkedHashMap<>());
        }
        return compacted;
    }

    private Map<Integer, ShopItem> loadItems(String categoryId, int page, ConfigurationSection itemsSection, Currency currency) {
        Map<Integer, ShopItem> items = new LinkedHashMap<>();
        if (itemsSection == null) {
            return items;
        }

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            Material material = Material.matchMaterial(section.getString("material", ""));
            int slot = section.getInt("slot", parsePositiveInt(key, -1));
            int amount = Math.max(1, Math.min(64, section.getInt("amount", 1)));
            BigDecimal buyPrice = BigDecimal.valueOf(section.getDouble("buy", -1));
            BigDecimal sellPrice = BigDecimal.valueOf(section.getDouble("sell", -1));

            if (material == null || material.isAir() || !isTradeSlot(slot) || buyPrice.signum() < 0 || sellPrice.signum() < 0) {
                getLogger().warning("Skipping invalid shop item: " + categoryId + ".page" + page + ".items." + key);
                continue;
            }

            ShopItem shopItem = new ShopItem(
                    key,
                    material,
                    amount,
                    buyPrice,
                    sellPrice,
                    section.getString("name", prettify(material)),
                    section.getStringList("lore"),
                    loadEnchantments(categoryId, page, key, section.getConfigurationSection("enchants")),
                    currency,
                    section.getString("custom", ""),
                    section.getString("spawner-type", "")
            );
            items.put(slot, shopItem);
            if (currency.isMoney()) {
                sellItemsByMaterial.putIfAbsent(material, shopItem);
            }
        }
        return items;
    }

    private Map<Enchantment, Integer> loadEnchantments(String categoryId, int page, String itemId, ConfigurationSection enchantsSection) {
        Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
        if (enchantsSection == null) {
            return enchantments;
        }

        for (String key : enchantsSection.getKeys(false)) {
            Enchantment enchantment = findEnchantment(key);
            int level = enchantsSection.getInt(key, 1);
            if (enchantment == null || level < 1) {
                getLogger().warning("Skipping invalid enchantment: " + categoryId + ".page" + page + ".items." + itemId + ".enchants." + key);
                continue;
            }
            enchantments.put(enchantment, level);
        }
        return enchantments;
    }

    private Enchantment findEnchantment(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(normalized));
        if (enchantment == null) {
            enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(normalized.replace("_", "-")));
        }
        if (enchantment == null) {
            enchantment = Enchantment.getByName(key.toUpperCase(Locale.ROOT));
        }
        return enchantment;
    }

    private void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new BakaMainMenuHolder(), mainSize, color(mainTitle));
        for (ShopCategory category : categoriesById.values()) {
            inventory.setItem(category.slot(), category.displayItem());
        }
        player.openInventory(inventory);
    }

    private void openClickedCategory(Player player, int rawSlot) {
        for (ShopCategory category : categoriesById.values()) {
            if (category.slot() == rawSlot) {
                openCategory(player, category, 1);
                return;
            }
        }
    }

    private void openCategory(Player player, ShopCategory category, int page) {
        Inventory inventory = Bukkit.createInventory(new BakaCategoryHolder(category.id(), page), categorySize, color(category.menuTitle()));
        addCategoryBorder(inventory);
        for (Map.Entry<Integer, ShopItem> entry : category.itemsForPage(page).entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue().displayItem());
        }
        if (page > 1) {
            inventory.setItem(categoryPreviousSlot(), navButton(Material.RED_STAINED_GLASS_PANE, "&cPrevious Page"));
        }
        inventory.setItem(categoryBackSlot(), navButton(Material.YELLOW_STAINED_GLASS_PANE, "&eBack"));
        if (category.hasPage(page + 1)) {
            inventory.setItem(categoryNextSlot(), navButton(Material.GREEN_STAINED_GLASS_PANE, "&aNext Page"));
        }
        player.openInventory(inventory);
    }

    private void addCategoryBorder(Inventory inventory) {
        ItemStack border = navButton(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!isTradeSlot(slot) && slot != categoryPreviousSlot() && slot != categoryBackSlot() && slot != categoryNextSlot()) {
                inventory.setItem(slot, border);
            }
        }
    }

    private void openPurchaseMenu(Player player, String categoryId, int page, int itemSlot, int quantity) {
        ShopCategory category = categoriesById.get(categoryId);
        if (category == null) {
            return;
        }

        ShopItem shopItem = category.itemAt(page, itemSlot);
        if (shopItem == null) {
            return;
        }

        int safeQuantity = Math.max(1, quantity);
        Inventory inventory = Bukkit.createInventory(new BakaPurchaseHolder(categoryId, page, itemSlot, safeQuantity), 27, color("&a&lChoose Amount"));
        inventory.setItem(PURCHASE_SET_64_SLOT, menuButton(Material.YELLOW_STAINED_GLASS_PANE, "&e&lSet to 64", List.of("&7Sets purchase amount to &e64&7.")));
        inventory.setItem(PURCHASE_BUY_SLOT, menuButton(Material.EMERALD_BLOCK, "&a&lBuy Now", List.of(
                "&7Amount: &ex" + safeQuantity,
                "&7Total items: &ex" + safeQuantity,
                "&7Cost: &e" + shopItem.currency().format(shopItem.buyPrice().multiply(BigDecimal.valueOf(safeQuantity)), RoundingMode.CEILING)
        )));
        inventory.setItem(PURCHASE_MINUS_10_SLOT, menuButton(Material.RED_STAINED_GLASS_PANE, "&c&l-10", List.of("&7Decrease amount by 10.")));
        inventory.setItem(PURCHASE_MINUS_1_SLOT, menuButton(Material.RED_STAINED_GLASS_PANE, "&c&l-1", List.of("&7Decrease amount by 1.")));
        inventory.setItem(PURCHASE_PREVIEW_SLOT, purchasePreview(shopItem, safeQuantity));
        inventory.setItem(PURCHASE_PLUS_1_SLOT, menuButton(Material.LIME_STAINED_GLASS_PANE, "&a&l+1", List.of("&7Increase amount by 1.")));
        inventory.setItem(PURCHASE_PLUS_10_SLOT, menuButton(Material.LIME_STAINED_GLASS_PANE, "&a&l+10", List.of("&7Increase amount by 10.")));
        player.openInventory(inventory);
    }

    private void handlePurchaseMenuClick(Player player, BakaPurchaseHolder holder, int rawSlot) {
        ShopCategory category = categoriesById.get(holder.categoryId());
        if (category == null) {
            player.closeInventory();
            return;
        }

        ShopItem shopItem = category.itemAt(holder.page(), holder.itemSlot());
        if (shopItem == null) {
            player.closeInventory();
            return;
        }

        int quantity = holder.quantity();
        if (rawSlot == PURCHASE_MINUS_10_SLOT) {
            suppressPurchaseReturn.add(player.getUniqueId());
            openPurchaseMenu(player, holder.categoryId(), holder.page(), holder.itemSlot(), Math.max(1, quantity - 10));
        } else if (rawSlot == PURCHASE_MINUS_1_SLOT) {
            suppressPurchaseReturn.add(player.getUniqueId());
            openPurchaseMenu(player, holder.categoryId(), holder.page(), holder.itemSlot(), Math.max(1, quantity - 1));
        } else if (rawSlot == PURCHASE_PLUS_1_SLOT) {
            suppressPurchaseReturn.add(player.getUniqueId());
            openPurchaseMenu(player, holder.categoryId(), holder.page(), holder.itemSlot(), quantity + 1);
        } else if (rawSlot == PURCHASE_PLUS_10_SLOT) {
            suppressPurchaseReturn.add(player.getUniqueId());
            openPurchaseMenu(player, holder.categoryId(), holder.page(), holder.itemSlot(), quantity + 10);
        } else if (rawSlot == PURCHASE_SET_64_SLOT) {
            suppressPurchaseReturn.add(player.getUniqueId());
            openPurchaseMenu(player, holder.categoryId(), holder.page(), holder.itemSlot(), 64);
        } else if (rawSlot == PURCHASE_BUY_SLOT || rawSlot == PURCHASE_PREVIEW_SLOT) {
            buy(player, shopItem, quantity);
        }
    }

    private ItemStack purchasePreview(ShopItem shopItem, int quantity) {
        ItemStack item = shopItem.createItem(Math.min(64, Math.max(1, quantity)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(color(""));
            lore.add(color("&dSelected amount: &ex" + quantity));
            lore.add(color("&dTotal items: &ex" + quantity));
            lore.add(color("&dTotal cost: &e" + shopItem.currency().format(shopItem.buyPrice().multiply(BigDecimal.valueOf(quantity)), RoundingMode.CEILING)));
            lore.add(color("&aClick here or the emerald block to buy."));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack menuButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(color(name));
            meta.lore(lore.stream().map(BakaShopPlugin::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openSellMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(new BakaSellHolder(), 54, color("&0Sell Items"));
        inventory.setItem(SELL_CONFIRM_SLOT, sellConfirmButton());
        player.openInventory(inventory);
    }

    private ItemStack sellConfirmButton() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(color("&a&lSell Items"));
            meta.lore(List.of(color("&7Click to sell all sellable items."), color("&7Closing this menu also sells them.")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack navButton(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void buy(Player player, ShopItem shopItem, int multiplier) {
        int itemAmount = multiplier;
        BigDecimal price = shopItem.buyPrice().multiply(BigDecimal.valueOf(multiplier));
        ItemStack stack = shopItem.purchaseItem(itemAmount);

        try {
            if (!canPay(player, shopItem.currency(), price)) {
                send(player, "not-enough-money", Map.of("%price%", shopItem.currency().format(price, RoundingMode.CEILING)));
                return;
            }
            if (!hasSpace(player.getInventory(), stack)) {
                send(player, "no-space");
                return;
            }

            takePayment(player, shopItem.currency(), price);
            player.getInventory().addItem(stack);
            send(player, "bought", Map.of("%amount%", String.valueOf(itemAmount), "%item%", shopItem.plainName(), "%price%", shopItem.currency().format(price, RoundingMode.CEILING)));
            getLogger().info(player.getName() + " bought x" + itemAmount + " " + shopItem.material().name() + " for " + shopItem.currency().format(price, RoundingMode.CEILING));
        } catch (Exception exception) {
            getLogger().warning("Failed to buy " + shopItem.id() + " for " + player.getName() + ": " + exception.getMessage());
            send(player, "economy-error");
        }
    }

    private void sell(Player player, ShopItem shopItem, int multiplier) {
        int itemAmount = shopItem.amount() * multiplier;
        BigDecimal price = shopItem.sellPrice().multiply(BigDecimal.valueOf(multiplier));
        ItemStack stack = new ItemStack(shopItem.material(), itemAmount);

        try {
            if (!player.getInventory().containsAtLeast(stack, itemAmount)) {
                send(player, "not-enough-items");
                return;
            }
            player.getInventory().removeItem(stack);
            giveCurrency(player, shopItem.currency(), price, RoundingMode.FLOOR);
            send(player, "sold", Map.of("%amount%", String.valueOf(itemAmount), "%item%", shopItem.plainName(), "%price%", shopItem.currency().format(price, RoundingMode.FLOOR)));
        } catch (Exception exception) {
            getLogger().warning("Failed to sell " + shopItem.id() + " for " + player.getName() + ": " + exception.getMessage());
            send(player, "economy-error");
        }
    }

    private boolean canPay(Player player, Currency currency, BigDecimal amount) throws Exception {
        if (currency.isMoney()) {
            return Economy.hasEnough(player.getUniqueId(), amount);
        }
        if (currency.isPlaceholder()) {
            return readPlaceholderBalance(player, currency).compareTo(amount) >= 0;
        }

        int itemAmount = currency.itemAmount(amount, RoundingMode.CEILING);
        return player.getInventory().containsAtLeast(new ItemStack(currency.material(), itemAmount), itemAmount);
    }

    private void takePayment(Player player, Currency currency, BigDecimal amount) throws Exception {
        if (currency.isMoney()) {
            Economy.subtract(player.getUniqueId(), amount);
            return;
        }
        if (currency.isPlaceholder()) {
            dispatchCurrencyCommand(player, currency.takeCommand(), currency.itemAmount(amount, RoundingMode.CEILING));
            return;
        }

        int itemAmount = currency.itemAmount(amount, RoundingMode.CEILING);
        player.getInventory().removeItem(new ItemStack(currency.material(), itemAmount));
    }

    private void giveCurrency(Player player, Currency currency, BigDecimal amount, RoundingMode roundingMode) throws Exception {
        if (currency.isMoney()) {
            Economy.add(player.getUniqueId(), amount);
            return;
        }
        if (currency.isPlaceholder()) {
            int itemAmount = currency.itemAmount(amount, roundingMode);
            if (itemAmount > 0) {
                dispatchCurrencyCommand(player, currency.giveCommand(), itemAmount);
            }
            return;
        }

        int itemAmount = currency.itemAmount(amount, roundingMode);
        if (itemAmount > 0) {
            returnToPlayer(player, new ItemStack(currency.material(), itemAmount));
        }
    }

    private BigDecimal readPlaceholderBalance(Player player, Currency currency) throws Exception {
        BigDecimal commandBalance = readShellBalanceCommand(player);
        if (commandBalance != null) {
            return commandBalance;
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            throw new IllegalStateException("Could not read shells balance from /shells bal, and PlaceholderAPI is not installed.");
        }

        Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
        List<String> placeholders = new ArrayList<>();
        placeholders.add(currency.placeholder());
        placeholders.add("%shells%");
        placeholders.add("%shells_short%");

        BigDecimal best = BigDecimal.ZERO;
        String bestRaw = "";
        for (String placeholder : placeholders.stream().filter(Objects::nonNull).distinct().toList()) {
            Object value = placeholderApi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                    .invoke(null, player, placeholder);
            String raw = String.valueOf(value);
            if (raw.equalsIgnoreCase(placeholder) || raw.contains("%")) {
                continue;
            }

            BigDecimal parsed = parseCompactNumber(raw);
            if (parsed.compareTo(best) > 0) {
                best = parsed;
                bestRaw = raw;
            }
        }

        if (best.signum() <= 0) {
            getLogger().warning("Could not read Shells balance for " + player.getName() + ". Last parsed value: '" + bestRaw + "'. Check PlaceholderAPI and the Shells placeholders.");
        }
        return best;
    }

    private BigDecimal readShellBalanceCommand(Player player) {
        CapturingCommandSender sender = new CapturingCommandSender();
        boolean handled = Bukkit.dispatchCommand(sender, "shells bal " + player.getName());
        if (!handled) {
            return null;
        }

        BigDecimal best = BigDecimal.ZERO;
        for (String message : sender.messages()) {
            BigDecimal parsed = parseCompactNumber(message);
            if (parsed.compareTo(best) > 0) {
                best = parsed;
            }
        }

        if (best.signum() > 0) {
            return best;
        }
        getLogger().warning("Could not parse /shells bal output for " + player.getName() + ": " + sender.messages());
        return null;
    }

    private void dispatchCurrencyCommand(Player player, String command, int amount) {
        dispatchCurrencyCommand(player.getName(), player.getUniqueId().toString(), command, amount);
    }

    private void dispatchCurrencyCommand(String playerName, String uuid, String command, int amount) {
        if (command == null || command.isBlank()) {
            throw new IllegalStateException("No shell currency command is configured.");
        }

        String rendered = command
                .replace("%player%", playerName)
                .replace("%uuid%", uuid == null ? "" : uuid)
                .replace("%amount%", String.valueOf(amount));
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered)) {
            throw new IllegalStateException("Shell currency command failed: " + rendered);
        }
    }

    private Currency findPlaceholderCurrency() {
        for (ShopCategory category : categoriesById.values()) {
            if (category.currency().isPlaceholder()) {
                return category.currency();
            }
        }
        return null;
    }

    private BigDecimal parseCompactNumber(String input) {
        Matcher shellsMessage = Pattern.compile("(?i)([0-9]+(?:\\.[0-9]+)?)\\s*([kmbt]?)\\s*shells?\\b").matcher(input);
        if (shellsMessage.find()) {
            BigDecimal number = new BigDecimal(shellsMessage.group(1));
            return switch (shellsMessage.group(2).toLowerCase(Locale.ROOT)) {
                case "k" -> number.multiply(BigDecimal.valueOf(1_000L));
                case "m" -> number.multiply(BigDecimal.valueOf(1_000_000L));
                case "b" -> number.multiply(BigDecimal.valueOf(1_000_000_000L));
                case "t" -> number.multiply(BigDecimal.valueOf(1_000_000_000_000L));
                default -> number;
            };
        }

        String normalized = input.toLowerCase(Locale.ROOT)
                .replace(",", "")
                .replace("Â§", "&")
                .replaceAll("\u00a7[0-9a-fk-or]", "")
                .replaceAll("&[0-9a-fk-or]", "")
                .replaceAll("[^0-9.kmbt]", "")
                .trim();
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([kmbt]?)").matcher(normalized);
        BigDecimal best = BigDecimal.ZERO;
        while (matcher.find()) {
            BigDecimal number = new BigDecimal(matcher.group(1));
            BigDecimal parsed = switch (matcher.group(2)) {
                case "k" -> number.multiply(BigDecimal.valueOf(1_000L));
                case "m" -> number.multiply(BigDecimal.valueOf(1_000_000L));
                case "b" -> number.multiply(BigDecimal.valueOf(1_000_000_000L));
                case "t" -> number.multiply(BigDecimal.valueOf(1_000_000_000_000L));
                default -> number;
            };
            if (parsed.compareTo(best) > 0) {
                best = parsed;
            }
        }
        return best;
    }

    private void processSellInventory(Player player, Inventory inventory) {
        Map<Currency, BigDecimal> totalsByCurrency = new LinkedHashMap<>();
        List<ItemStack> soldItems = new ArrayList<>();
        List<ItemStack> unsoldItems = new ArrayList<>();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == SELL_CONFIRM_SLOT) {
                inventory.setItem(slot, null);
                continue;
            }

            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            ShopItem sellItem = sellItemsByMaterial.get(item.getType());
            if (sellItem == null || sellItem.sellPrice().signum() <= 0) {
                unsoldItems.add(item.clone());
                inventory.setItem(slot, null);
                continue;
            }

            BigDecimal itemTotal = sellItem.sellPrice()
                    .multiply(BigDecimal.valueOf(item.getAmount()), MathContext.DECIMAL128)
                    .divide(BigDecimal.valueOf(sellItem.amount()), MathContext.DECIMAL128);
            if (!sellItem.currency().isMoney() && sellItem.currency().itemAmount(itemTotal, RoundingMode.FLOOR) <= 0) {
                unsoldItems.add(item.clone());
                inventory.setItem(slot, null);
                continue;
            }
            totalsByCurrency.merge(sellItem.currency(), itemTotal, (left, right) -> left.add(right, MathContext.DECIMAL128));
            soldItems.add(item.clone());
            inventory.setItem(slot, null);
        }

        for (ItemStack unsoldItem : unsoldItems) {
            returnToPlayer(player, unsoldItem);
        }

        if (totalsByCurrency.isEmpty()) {
            if (!unsoldItems.isEmpty()) {
                player.sendMessage(color("&cThose items cannot be sold here."));
            }
            return;
        }

        try {
            for (Map.Entry<Currency, BigDecimal> entry : totalsByCurrency.entrySet()) {
                if (entry.getKey().isMoney()) {
                    Economy.add(player.getUniqueId(), entry.getValue());
                }
            }

            List<String> paid = new ArrayList<>();
            for (Map.Entry<Currency, BigDecimal> entry : totalsByCurrency.entrySet()) {
                if (!entry.getKey().isMoney()) {
                    giveCurrency(player, entry.getKey(), entry.getValue(), RoundingMode.FLOOR);
                }
                paid.add(entry.getKey().format(entry.getValue(), RoundingMode.FLOOR));
            }
            player.sendMessage(color("&aSold items for &e" + String.join("&a, &e", paid) + "&a."));
        } catch (Exception exception) {
            getLogger().warning("Failed to sell GUI contents for " + player.getName() + ": " + exception.getMessage());
            player.sendMessage(color("&cEssentialsX economy rejected that transaction. Items were returned."));
            for (ItemStack item : soldItems) {
                returnToPlayer(player, item);
            }
        }
    }

    private void returnToPlayer(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private boolean hasSpace(PlayerInventory inventory, ItemStack stack) {
        int remaining = stack.getAmount();
        for (ItemStack content : inventory.getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                remaining -= stack.getMaxStackSize();
            } else if (content.isSimilar(stack)) {
                remaining -= Math.max(0, content.getMaxStackSize() - content.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private PageSlot firstEmptySlot(String categoryId) {
        int page = 1;
        while (true) {
            for (int slot = 0; slot < categorySize; slot++) {
                if (!isTradeSlot(slot)) {
                    continue;
                }
                String path = "categories." + categoryId + ".pages." + page + ".items." + slot;
                if (!getConfig().isConfigurationSection(path)) {
                    return new PageSlot(page, slot);
                }
            }
            page++;
        }
    }

    private void migrateLegacyItems(String categoryId) {
        String categoryPath = "categories." + categoryId;
        if (getConfig().isConfigurationSection(categoryPath + ".pages") || !getConfig().isConfigurationSection(categoryPath + ".items")) {
            return;
        }

        ConfigurationSection legacyItems = getConfig().getConfigurationSection(categoryPath + ".items");
        if (legacyItems == null) {
            return;
        }

        for (String key : legacyItems.getKeys(false)) {
            ConfigurationSection item = legacyItems.getConfigurationSection(key);
            if (item == null) {
                continue;
            }

            int slot = item.getInt("slot", parsePositiveInt(key, -1));
            if (!isTradeSlot(slot)) {
                continue;
            }

            Map<String, Object> values = new LinkedHashMap<>();
            for (String valueKey : item.getKeys(false)) {
                values.put(valueKey, item.get(valueKey));
            }
            values.put("slot", slot);
            getConfig().set(categoryPath + ".pages.1.items." + slot, values);
        }
        getConfig().set(categoryPath + ".items", null);
    }

    private String findCategoryId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        if (categoriesById.containsKey(normalized)) {
            return normalized;
        }
        for (ShopCategory category : categoriesById.values()) {
            String plainTitle = plain(category.title()).replace(" ", "").toLowerCase(Locale.ROOT);
            if (plainTitle.equals(normalized) || plainTitle.equals(normalized + "shop")) {
                return category.id();
            }
        }
        return null;
    }

    private Material findMaterial(String input) {
        String name = input.toUpperCase(Locale.ROOT).replace('-', '_');
        Material direct = Material.matchMaterial(name);
        if (direct != null) {
            return direct;
        }
        Material block = Material.matchMaterial(name + "_BLOCK");
        if (block != null) {
            return block;
        }
        return Material.matchMaterial(name + "_ITEM");
    }

    private String customItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(customItemKey, PersistentDataType.STRING);
    }

    private String spawnerType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(spawnerTypeKey, PersistentDataType.STRING);
    }

    private static EntityType parseEntityType(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return EntityType.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private boolean isLog(Material material) {
        return Tag.LOGS.isTagged(material) || material.name().endsWith("_STEM") || material.name().endsWith("_HYPHAE");
    }

    private PageSlot parseItemPath(String input) {
        Matcher matcher = ITEM_PATH.matcher(input);
        if (!matcher.matches()) {
            return null;
        }
        int page = parsePositiveInt(matcher.group(1), -1);
        int slot = parsePositiveInt(matcher.group(2), -1);
        if (page < 1 || !isTradeSlot(slot)) {
            return null;
        }
        return new PageSlot(page, slot);
    }

    private String configItemPath(String categoryId, PageSlot target) {
        String newPath = "categories." + categoryId + ".pages." + target.page() + ".items." + target.slot();
        if (getConfig().isConfigurationSection(newPath) || getConfig().isConfigurationSection("categories." + categoryId + ".pages")) {
            return newPath;
        }
        return "categories." + categoryId + ".items." + target.slot();
    }

    private BigDecimal parsePrice(CommandSender sender, String input) {
        try {
            BigDecimal price = new BigDecimal(input);
            if (price.signum() < 0) {
                sender.sendMessage(color("&cPrice cannot be negative."));
                return null;
            }
            return price;
        } catch (NumberFormatException exception) {
            sender.sendMessage(color("&cInvalid price: &e" + input));
            return null;
        }
    }

    private void saveAndReload() {
        saveConfig();
        reloadConfig();
        loadShop();
    }

    private void send(CommandSender sender, String messageKey) {
        send(sender, messageKey, Collections.emptyMap());
    }

    private void send(CommandSender sender, String messageKey, Map<String, String> placeholders) {
        String message = getConfig().getString("messages." + messageKey, messageKey);
        String prefix = getConfig().getString("messages.prefix", "");
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message = message.replace(placeholder.getKey(), placeholder.getValue());
        }
        sender.sendMessage(color(prefix + message));
    }

    private boolean isTradeSlot(int slot) {
        return CATEGORY_TRADE_SLOTS.contains(slot);
    }

    private int categoryPreviousSlot() {
        return categorySize - 6;
    }

    private int categoryBackSlot() {
        return categorySize - 5;
    }

    private int categoryNextSlot() {
        return categorySize - 4;
    }

    private static int normalizeSize(int size) {
        return Math.max(9, Math.min(54, ((size + 8) / 9) * 9));
    }

    private static int parsePositiveInt(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Component color(String text) {
        return LEGACY.deserialize(text == null ? "" : text).decoration(TextDecoration.ITALIC, false);
    }

    private static String plain(String text) {
        return LEGACY.serialize(LEGACY.deserialize(text == null ? "" : text)).replaceAll("&[0-9A-FK-ORa-fk-or]", "");
    }

    private static String prettify(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return String.join(" ", words);
    }

    private enum PickMode {
        WALL,
        FLOOR
    }

    private record PageSlot(int page, int slot) {
    }

    private record InstalledPlugin(File jar, String version) {
    }

    private static final class CapturingCommandSender implements ConsoleCommandSender, ServerOperator {
        private final List<String> messages = new ArrayList<>();
        private final PermissibleBase permissions = new PermissibleBase(this);

        private List<String> messages() {
            return messages;
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public void sendMessage(String... messages) {
            Collections.addAll(this.messages, messages);
        }

        @Override
        public void sendMessage(UUID sender, String message) {
            sendMessage(message);
        }

        @Override
        public void sendMessage(UUID sender, String... messages) {
            sendMessage(messages);
        }

        @Override
        public void sendMessage(net.kyori.adventure.identity.Identity source, Component message, net.kyori.adventure.audience.MessageType type) {
            messages.add(PLAIN.serialize(message));
        }

        @Override
        public boolean isConversing() {
            return false;
        }

        @Override
        public void acceptConversationInput(String input) {
        }

        @Override
        public boolean beginConversation(Conversation conversation) {
            return false;
        }

        @Override
        public void abandonConversation(Conversation conversation) {
        }

        @Override
        public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {
        }

        @Override
        public void sendRawMessage(String message) {
            messages.add(message);
        }

        @Override
        public void sendRawMessage(UUID sender, String message) {
            sendRawMessage(message);
        }

        @Override
        public Server getServer() {
            return Bukkit.getServer();
        }

        @Override
        public String getName() {
            return "BakaShopShellBalance";
        }

        @Override
        public Spigot spigot() {
            return Bukkit.getConsoleSender().spigot();
        }

        @Override
        public Component name() {
            return Component.text(getName());
        }

        @Override
        public boolean isPermissionSet(String name) {
            return permissions.isPermissionSet(name);
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return permissions.isPermissionSet(perm);
        }

        @Override
        public boolean hasPermission(String name) {
            return true;
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return true;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return permissions.addAttachment(plugin, name, value);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return permissions.addAttachment(plugin);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return permissions.addAttachment(plugin, name, value, ticks);
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return permissions.addAttachment(plugin, ticks);
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
            permissions.removeAttachment(attachment);
        }

        @Override
        public void recalculatePermissions() {
            permissions.recalculatePermissions();
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return permissions.getEffectivePermissions();
        }

        @Override
        public boolean isOp() {
            return true;
        }

        @Override
        public void setOp(boolean value) {
        }
    }

    private record Currency(boolean money, Material material, String singular, String plural,
                            String placeholder, String takeCommand, String giveCommand) {
        private static Currency essentialsMoney() {
            return new Currency(true, null, "money", "money", "", "", "");
        }

        private static Currency item(Material material, String singular, String plural) {
            return new Currency(false, material, singular, plural, "", "", "");
        }

        private static Currency placeholder(String placeholder, String singular, String plural, String takeCommand, String giveCommand) {
            return new Currency(false, null, singular, plural, placeholder, takeCommand, giveCommand);
        }

        private boolean isMoney() {
            return money;
        }

        private boolean isPlaceholder() {
            return !money && material == null;
        }

        private int itemAmount(BigDecimal amount, RoundingMode roundingMode) {
            return amount.setScale(0, roundingMode).intValue();
        }

        private String format(BigDecimal amount, RoundingMode roundingMode) {
            if (money) {
                return Economy.format(amount);
            }

            int itemAmount = itemAmount(amount, roundingMode);
            return itemAmount + " " + (itemAmount == 1 ? singular : plural);
        }
    }

    private record ShopCategory(String id, int slot, Material icon, String title, String menuTitle,
                                List<String> lore, Currency currency, Map<Integer, Map<Integer, ShopItem>> pages) {
        private ItemStack displayItem() {
            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(color(title));
                meta.lore(lore.stream().map(BakaShopPlugin::color).toList());
                item.setItemMeta(meta);
            }
            return item;
        }

        private Map<Integer, ShopItem> itemsForPage(int page) {
            return pages.getOrDefault(page, Collections.emptyMap());
        }

        private ShopItem itemAt(int page, int slot) {
            return itemsForPage(page).get(slot);
        }

        private boolean hasPage(int page) {
            return pages.containsKey(page) && !itemsForPage(page).isEmpty();
        }
    }

    private record ShopItem(String id, Material material, int amount, BigDecimal buyPrice, BigDecimal sellPrice,
                            String displayName, List<String> lore, Map<Enchantment, Integer> enchantments,
                            Currency currency, String custom, String spawnerType) {
        private ItemStack displayItem() {
            return createItem(1);
        }

        private ItemStack purchaseItem(int stackAmount) {
            if (custom != null && !custom.isBlank()) {
                return createItem(stackAmount);
            }

            ItemStack item = new ItemStack(material, stackAmount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                for (Map.Entry<Enchantment, Integer> enchantment : enchantments.entrySet()) {
                    meta.addEnchant(enchantment.getKey(), enchantment.getValue(), true);
                }
                if (spawnerType != null && !spawnerType.isBlank() && meta instanceof BlockStateMeta blockStateMeta
                        && blockStateMeta.getBlockState() instanceof CreatureSpawner spawner) {
                    EntityType entityType = parseEntityType(spawnerType);
                    if (entityType != null) {
                        spawner.setSpawnedType(entityType);
                        blockStateMeta.setBlockState(spawner);
                    }
                }
                item.setItemMeta(meta);
            }
            return item;
        }

        private ItemStack createItem(int stackAmount) {
            ItemStack item = new ItemStack(material, stackAmount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(color(displayName));
                List<Component> renderedLore = new ArrayList<>();
                for (String line : lore) {
                    renderedLore.add(color(line
                            .replace("%buy%", currency.format(buyPrice, RoundingMode.CEILING))
                            .replace("%sell%", currency.format(sellPrice, RoundingMode.FLOOR))));
                }
                meta.lore(renderedLore);
                for (Map.Entry<Enchantment, Integer> enchantment : enchantments.entrySet()) {
                    meta.addEnchant(enchantment.getKey(), enchantment.getValue(), true);
                }
                if (custom != null && !custom.isBlank()) {
                    meta.getPersistentDataContainer().set(customItemKey, PersistentDataType.STRING, custom);
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.setUnbreakable(true);
                }
                if (spawnerType != null && !spawnerType.isBlank()) {
                    meta.getPersistentDataContainer().set(spawnerTypeKey, PersistentDataType.STRING, spawnerType.toUpperCase(Locale.ROOT));
                }
                item.setItemMeta(meta);
            }
            return item;
        }

        private String plainName() {
            return plain(displayName);
        }
    }

    private static final class VirtualSpawner {
        private final EntityType entityType;
        private int xp;
        private final Map<Material, Integer> items;

        private VirtualSpawner(EntityType entityType, int xp, Map<Material, Integer> items) {
            this.entityType = entityType;
            this.xp = xp;
            this.items = items;
        }

        private EntityType entityType() {
            return entityType;
        }

        private int xp() {
            return xp;
        }

        private void xp(int xp) {
            this.xp = xp;
        }

        private Map<Material, Integer> items() {
            return items;
        }
    }

    private static final class BakaMainMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }

    private record BakaCategoryHolder(String categoryId, int page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }

    private record BakaPurchaseHolder(String categoryId, int page, int itemSlot, int quantity) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }

    private static final class BakaSellHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }

    private record BakaSpawnerHolder(String locationKey) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 9);
        }
    }
}

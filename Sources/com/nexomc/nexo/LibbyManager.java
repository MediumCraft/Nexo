package com.nexomc.nexo;

import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;
import net.byteflux.libby.relocation.Relocation;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

public class LibbyManager {

    public static boolean failedLibs = false;

    private static final ArrayList<Library> libs = new ArrayList<>();
    private static final String COMMAND_API_VERSION = "9.7.0";
    private static final String CREATIVE_VERSION = "1.7.3";
    private static final String IDOFRONT_VERSION = "0.25.17";

    public static void loadLibs(JavaPlugin plugin) {
        BukkitLibraryManager manager = new BukkitLibraryManager(plugin, "libs");
        Bukkit.getConsoleSender().sendMessage("[Nexo] Loading libraries...");

        manager.addMavenCentral();
        manager.addRepository("https://repo.nexomc.com/releases/");
        manager.addRepository("https://repo.nexomc.com/snapshots/");
        manager.addRepository("https://repo.mineinabyss.com/releases/");
        manager.addRepository("https://repo.mineinabyss.com/snapshots/");

        load();
        for (Library lib : libs) {
            try {
                manager.loadLibrary(lib);
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Nexo] Failed to load library: " + lib.getArtifactId());
                failedLibs = true;
            }
        }

    }

    private static void load() {
        libs.add(Library.builder().groupId("org{}jetbrains{}kotlinx").artifactId("kotlinx-coroutines-core").version("1.9.0-RC").relocate("kotlinx{}", "com{}nexomc{}libs{}kotlinx{}").build());
        libs.add(Library.builder().groupId("org{}jetbrains{}kotlin").artifactId("kotlin-stdlib").version("2.0.21").relocate("kotlin{}", "com{}nexomc{}libs{}kotlin{}").build());
        libs.add(getLib("dev{}jorel", (isPaperServer() && !isLegacyServer()) ? "commandapi-bukkit-shade-mojang-mapped" : "commandapi-bukkit-shade", COMMAND_API_VERSION, "dev{}jorel").build());
        libs.add(getLib("dev{}jorel", "commandapi-bukkit-kotlin", COMMAND_API_VERSION, "dev{}jorel").build());

        libs.add(getLib("com{}jeff-media", "custom-block-data", "2.2.2", "com{}jeff_media").build());
        libs.add(getLib("com{}jeff-media", "MorePersistentDataTypes", "2.4.0", "com{}jeff_media").build());
        libs.add(getLib("com{}jeff-media", "persistent-data-serializer", "1.0", "com{}jeff_media").build());

        libs.add(getLib("team{}unnamed", "creative-api", "1.7.6-SNAPSHOT", "team{}unnamed")
                .url("https://repo.nexomc.com/snapshots/team/unnamed/creative-api/1.7.6-SNAPSHOT/creative-api-1.7.6-SNAPSHOT.jar")
                .build());
        libs.add(getLib("team{}unnamed", "creative-server", CREATIVE_VERSION, "team{}unnamed").build());
        libs.add(getLib("team{}unnamed", "creative-serializer-minecraft", "1.7.6-SNAPSHOT", "team{}unnamed")
                .url("https://repo.nexomc.com/snapshots/team/unnamed/creative-serializer-minecraft/1.7.6-SNAPSHOT/creative-serializer-minecraft-1.7.6-SNAPSHOT.jar")
                .build());

        libs.add(getLib("io{}th0rgal", "protectionlib", "1.8.0", "io{}th0rgal").build());
        libs.add(getLib("com{}tcoded", "FoliaLib", "0.4.3", "com{}tcoded").build());

        libs.add(getLib("me{}gabytm{}util", "actions-spigot", "1.0.0", "me{}gabytm{}util")
                .url("https://repo.nexomc.com/releases/me/gabytm/util/actions-spigot/1.0.0/actions-spigot-1.0.0.jar")
                .build());
        libs.add(getLib("me{}gabytm{}util", "actions-core", "1.0.0", "me{}gabytm{}util")
                .url("https://repo.nexomc.com/releases/me/gabytm/util/actions-core/1.0.0/actions-core-1.0.0.jar")
                .build());

        libs.add(getLib("dev{}triumphteam", "triumph-gui", "3.1.12-SNAPSHOT", "dev{}triumphteam")
                .repository("https://repo.triumphteam.dev/snapshots").build());
        libs.add(getLib("com{}github{}stefvanschie{}inventoryframework", "IF", "0.10.19", "com{}github{}stefvanschie").build());

        libs.add(getLib("com{}mineinabyss", "idofront-util", IDOFRONT_VERSION, "com{}mineinabyss").build());
    }

    private static Library.Builder getLib(String groupId, String artifactId, String version, String relocationId) {
        return getLib(groupId, artifactId, version, new Relocation(relocationId, "com{}nexomc{}libs"));
    }

    private static Library.Builder getLib(String groupId, String artifactId, String version, Relocation relocation) {
        Library.Builder builder = Library.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .relocate(relocation)
                .isolatedLoad(false);

        return relocation != null ? builder.relocate(relocation) : builder;
    }

    private static boolean isLegacyServer() {
        return Bukkit.getServer().getMinecraftVersion().equals("1.20.4");
    }

    private static boolean isPaperServer() {
        Server server = Bukkit.getServer();
        Validate.notNull(server, "Server cannot be null");
        if (server.getName().equalsIgnoreCase("Paper")) return true;

        try {
            Class.forName("com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}

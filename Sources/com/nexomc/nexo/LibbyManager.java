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
    private static final String COMMAND_API_VERSION = "10.1.2";

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
                plugin.getComponentLogger().error("[Nexo] Failed to load library: " + lib.getArtifactId());
                failedLibs = true;
            }
        }

    }

    private static void load() {
        libs.add(Library.builder().groupId("org{}jetbrains{}kotlinx").artifactId("kotlinx-coroutines-core").version("1.10.1").relocate("kotlinx{}", "com{}nexomc{}libs{}kotlinx{}").build());
        libs.add(Library.builder().groupId("org{}jetbrains{}kotlinx").artifactId("kotlinx-coroutines-core-jvm").version("1.10.1").relocate("kotlinx{}", "com{}nexomc{}libs{}kotlinx{}").build());
        libs.add(Library.builder().groupId("org{}jetbrains{}kotlin").artifactId("kotlin-stdlib").version("2.2.0").relocate("kotlin{}", "com{}nexomc{}libs{}kotlin{}").build());
        libs.add(getLib("dev{}jorel", (isPaperServer() && !isLegacyServer()) ? "commandapi-bukkit-shade-mojang-mapped" : "commandapi-bukkit-shade", COMMAND_API_VERSION, "dev{}jorel").build());
        libs.add(getLib("dev{}jorel", "commandapi-bukkit-kotlin", COMMAND_API_VERSION, "dev{}jorel").build());

        libs.add(getLib("software{}amazon{}awssdk", "s3", "2.32.4", null, false).build());
    }

    private static Library.Builder getLib(String groupId, String artifactId, String version, String relocationId) {
        return getLib(groupId, artifactId, version, new Relocation(relocationId, "com{}nexomc{}libs"));
    }

    private static Library.Builder getLib(String groupId, String artifactId, String version, Relocation relocation) {
        return getLib(groupId, artifactId, version, relocation, false);
    }

    private static Library.Builder getLib(String groupId, String artifactId, String version, Relocation relocation, boolean isolated) {
        Library.Builder builder = Library.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .isolatedLoad(isolated);

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

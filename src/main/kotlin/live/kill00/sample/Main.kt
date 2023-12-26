package live.kill00.sample

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this).silentLogs(true).verboseOutput(false))
    }
    override fun onEnable() {
        logger.info("Working!")

        CommandAPICommand("sample")
            .withPermission("op")
            .executesPlayer(PlayerCommandExecutor { sender, _ ->
                sender.sendMessage("Hello, ${sender.name}! This is a sample command!")
            }).register()
    }
}
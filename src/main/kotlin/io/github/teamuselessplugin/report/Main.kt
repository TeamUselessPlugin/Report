package io.github.teamuselessplugin.report

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import io.github.kill00.configapi.cfg
import io.github.teamuselessplugin.report.commands.Report
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this).silentLogs(true).verboseOutput(false))
    }
    override fun onEnable() {
        cfg.register(this)
        cfg.makeData("config.yml")
        cfg.makeData("messages.yml")

        if (Bukkit.getPluginManager().getPlugin("PunishmentHelper") != null) {
            logger.info("PunishmentHelper 플러그인을 찾았습니다.")
        }

        Report().register()
        server.pluginManager.registerEvents(Report(), this)
    }
}
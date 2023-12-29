package io.github.teamuselessplugin.report

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import io.github.kill00.configapi.cfg
import io.github.teamuselessplugin.report.commands.Report
import org.bukkit.plugin.java.JavaPlugin

class Main : JavaPlugin() {
    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this).silentLogs(true).verboseOutput(false))
    }
    override fun onEnable() {
        cfg.register(this)
        cfg.makeData("config.yml")
        cfg.makeData("messages.yml")

        Report().register()
        server.pluginManager.registerEvents(Report(), this)
    }
}
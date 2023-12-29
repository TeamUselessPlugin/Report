package io.github.teamuselessplugin.report.commands

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.BooleanArgument
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import io.github.kill00.configapi.cfg
import io.github.monun.heartbeat.coroutines.HeartbeatScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.util.EventListener
import java.util.UUID

class Report : Listener {
    private var commandCooldown: HashMap<UUID, Long>? = HashMap()
    private var reportConfirm: HashMap<UUID, Boolean>? = HashMap()
    private var lastCommand: HashMap<UUID, String>? = HashMap()
    private var lastReporter: UUID? = null

    // 모두 종료후 해당 플레이어를 제거해야함
    private var reporting: HashMap<UUID, Boolean>? = HashMap()
    private var reportedPlayer: HashMap<UUID, UUID>? = HashMap()
    private var reportedReason: HashMap<UUID, String>? = HashMap()
    private var acceptedReport: HashMap<UUID, HashMap<UUID, Boolean>>? = HashMap()
    private var elapsedTime: HashMap<UUID, Long>? = HashMap()

    private val msg = cfg.get("messages.yml")
    private val config = cfg.get("config.yml")

    private val minimumPlayers = config.getInt("minimumPlayers")
    private val minimumAcceptPlayersRatio = config.getDouble("minimumAcceptPlayersRatio")
    private val maxReportCount = config.getInt("maxReportCount")
    private val punishmentLevel = config.getInt("punishmentLevel")
    private val reportAgreementTime = 1000L * 60 * config.getInt("reportAgreementTime")
    private val tenMinutes = 1000L * 60 * 10
    fun register() {
        CommandAPICommand("report")
            .withPermission("report.use")
            .withArguments(PlayerArgument("player"))
            .withArguments(GreedyStringArgument("reason").setOptional(true))
            .withAliases("신고")
            .executesPlayer(PlayerCommandExecutor { sender, args ->
                val rPlayer = args[0] as Player
                val userCooldown = commandCooldown?.get(sender.uniqueId) ?: 0L
                val userReportConfirm = reportConfirm?.get(sender.uniqueId) ?: false
                val userLastCommand = lastCommand?.get(sender.uniqueId) ?: ""
                val userReporting = reporting?.get(sender.uniqueId) ?: false

                val min = msg.getString("minute")
                val sec = msg.getString("second")

                lastCommand!![sender.uniqueId] = "/report ${args[0]}"

                if (!rPlayer.hasPermission("report.bypass")
                    &&
                    reportedPlayer?.size!! <= maxReportCount
                    &&
                    args[0] != sender
                    &&
                    Bukkit.getOnlinePlayers().size >= minimumPlayers
                    && !userReporting && userCooldown <= System.currentTimeMillis()) {

                    reportConfirm!![sender.uniqueId] = true

                    // 플레이어가 동의를 하지 않았을 경우 동의를 받음
                    if (!userReportConfirm) {
                        msg.getStringList("report_confirm").forEach {
                            sendMessageWithPrefix(sender, it)
                        }
                        reportConfirm!![sender.uniqueId] = true

                    } else if (userLastCommand == "/report ${args[0]}") {
                        reportConfirm?.remove(sender.uniqueId)
                        commandCooldown!![sender.uniqueId] = System.currentTimeMillis() + tenMinutes
                        lastReporter = sender.uniqueId

                        msg.getStringList("report_confirm_success").forEach { string ->
                            val tmp : Component

                            if (string.contains("%report-cancel-command%")) {
                                val s = string.split("%report-cancel-command%")
                                tmp = Component.text(s[0])
                                    .append(Component.text("§e/report-cancel")
                                        .clickEvent(ClickEvent.runCommand("/report-cancel"))
                                        .hoverEvent(Component.text("${msg.getString("report_cancel_hover")
                                            ?.replace("%reporter%", sender.name)}")))
                                    .append(Component.text(s[1]))
                            } else {
                                tmp = Component.text(string
                                    .replace("%punishmentLevel%", "$punishmentLevel")
                                    .replace("%reportAgreementTime%", "${reportAgreementTime / 1000L / 60}")
                                    .replace("%reason%", "${args[1] ?: "${msg.getString("NonReason")}"}"))
                            }

                            sendMessageWithPrefix(sender, tmp)
                        }

                        // 변수 세팅
                        reporting!![sender.uniqueId] = true
                        reportedPlayer!![sender.uniqueId] = rPlayer.uniqueId
                        reportedReason!![sender.uniqueId] = (args[1] ?: "${msg.getString("NonReason")}").toString()
                        // 본인은 자동 동의
                        acceptedReport!![sender.uniqueId] = HashMap()
                        acceptedReport!![sender.uniqueId]!![sender.uniqueId] = true

                        // 신고 접수를 위한 투표 시작
                        Bukkit.getOnlinePlayers().forEach {
                            if (it != sender) {
                                it.playSound(it.location, "minecraft:block.note_block.pling", 1f, 1f)
                                msg.getStringList("report_confirm_success_broadcast").forEach { string ->
                                    val tmp : Component

                                    if (string.contains("%report-accept-command%")) {
                                        val s = string.split("%report-accept-command%")
                                        tmp = Component.text(s[0])
                                            .append(Component.text("§e/report-accept ${sender.name}")
                                                .clickEvent(ClickEvent.runCommand("/report-accept ${sender.name}"))
                                                .hoverEvent(Component.text("${msg.getString("report_accept_hover")
                                                    ?.replace("%reporter%", sender.name)}")))
                                            .append(Component.text(s[1]))
                                    } else {
                                        tmp = Component.text(string
                                            .replace("%reporter%", sender.name)
                                            .replace("%reportee%", rPlayer.name)
                                            .replace("%reportAgreementTime%", "${reportAgreementTime / 1000L / 60}")
                                            .replace("%reason%", "${args[1] ?: "${msg.getString("NonReason")}"}"))
                                    }

                                    sendMessageWithPrefix(it, tmp)
                                }
                            }
                        }

                        HeartbeatScope().launch {
                            var ticks = 0L
                            var enabled = true

                            while (enabled) {
                                delay(50L)
                                ticks += 50L
                                elapsedTime!![sender.uniqueId] = ticks

                                val userAcceptedReport = acceptedReport?.get(sender.uniqueId)?.size ?: 0
                                enabled = reporting?.get(sender.uniqueId) ?: false

                                if (sender.isOnline) {
                                    if (ticks >= reportAgreementTime && userAcceptedReport < Math.round(Bukkit.getOnlinePlayers().size * minimumAcceptPlayersRatio)) {
                                        Bukkit.getOnlinePlayers().forEach {
                                            if (it != sender) {
                                                sendMessageWithPrefix(it, "${msg.getString("report_fail_timeout_broadcast")
                                                    ?.replace("%reporter%", sender.name)
                                                    ?.replace("%reportee%", rPlayer.name)
                                                    ?.replace("%reportAgreementTime%", "${reportAgreementTime / 1000L / 60}${min}")}")
                                            }
                                        }
                                        resetValue(sender)

                                        break
                                    } else if (userAcceptedReport >= Math.round(Bukkit.getOnlinePlayers().size * minimumAcceptPlayersRatio)) {
                                        Bukkit.getOnlinePlayers().forEach {
                                            sendMessageWithPrefix(it, "${msg.getString("report_vote_success_broadcast")
                                                ?.replace("%reporter%", sender.name)}")
                                            resetValue(sender)
                                        }

                                        when (punishmentLevel) {
                                            0 -> {
                                                val lastID = cfg.get("reports.yml").getInt("reports.${rPlayer.uniqueId}.lastID")
                                                val aliveID = cfg.get("reports.yml").getIntegerList("reports.${rPlayer.uniqueId}.aliveID")

                                                cfg.get("reports.yml").set("reports.${rPlayer.uniqueId}.lastID", lastID + 1).also { cfg.save("reports.yml", false) }

                                                cfg.get("reports.yml").set("reports.${rPlayer.uniqueId}.aliveID", aliveID.apply {
                                                    this.add(lastID + 1)
                                                }).also { cfg.save("reports.yml", false) }

                                                cfg.get("reports.yml").set("reports.${rPlayer.uniqueId}.data.${lastID + 1}",
                                                    HashMap<String, String>().apply {
                                                    this["reporter"] = sender.uniqueId.toString()
                                                    this["reason"] = "${args[1] ?: "${msg.getString("NonReason")}"}"
                                                    this["time"] = "${System.currentTimeMillis()}"
//                                                    this["checked"] = false.toString()
                                                }).also { cfg.save("reports.yml", false) }
                                            }

                                            1 -> {
                                                val tmp: Component = Component.empty()
                                                msg.getStringList("kick_message").forEach { string ->
                                                    tmp.append(Component.text(string
                                                        .replace("%reporter%", sender.name)
                                                        .replace("%reportee%", rPlayer.name)
                                                        .replace("%reason%", "${args[1] ?: "${msg.getString("NonReason")}"}")))
                                                    rPlayer.kick(tmp)
                                                }
                                            }

                                            // TODO: LiteBan
//                                            2 -> {
//                                                val tmp: Component = Component.empty()
//                                                msg.getStringList("ban_message").forEach { string ->
//                                                    tmp.append(Component.text(string
//                                                        .replace("%reporter%", sender.name)
//                                                        .replace("%reportee%", rPlayer.name)
//                                                        .replace("%reason%", "${args[1] ?: "${msg.getString("NonReason")}"}")))
//                                                    rPlayer.banPlayer()
//                                                }
                                            else -> {
                                                Bukkit.getLogger().warning("${msg.getString("error_wrong_punishment_level")
                                                    ?.replace("%punishmentLevel%", "$punishmentLevel")}")

                                                Bukkit.getOnlinePlayers().forEach {
                                                    sendMessageWithPrefix(it, "${msg.getString("error_wrong_punishment_level")
                                                        ?.replace("%punishmentLevel%", "$punishmentLevel")}")
                                                }
                                            }
                                        }

                                        break
                                    }
                                } else {
                                    resetValue(sender)
                                    Bukkit.getOnlinePlayers().forEach {
                                        sendMessageWithPrefix(it, "${msg.getString("report_fail_offline")
                                            ?.replace("%reporter%", sender.name)
                                            ?.replace("%reportee%", rPlayer.name)}")
                                    }

                                    break
                                }
                            }
                        }
                    }

                } else if (rPlayer.hasPermission("report.bypass")) {
                    // bypass 권한을 가진 플레이어는 신고를 할 수 없음
                    sendMessageWithPrefix(sender, "${msg.getString("report_fail_bypass")}")

                } else if (reportedPlayer?.size!! > maxReportCount) {
                    // 동시에 신고할 수 있는 최대 횟수를 초과했을 경우
                    sendMessageWithPrefix(sender, "${msg.getString("report_fail_max_report_count")}")

                } else if (args[0] == sender) {
                    // 본인을 신고할 수 없음
                    sendMessageWithPrefix(sender, "${msg.getString("report_fail_self")}")

                } else if (Bukkit.getOnlinePlayers().size < minimumPlayers) {
                    // 최소 플레이어 수를 충족하지 못했을 경우
                    sendMessageWithPrefix(sender, "${msg.getString("report_fail_low_players")
                        ?.replace("%minimumPlayers%", "$minimumPlayers")}")

                } else if (userReporting) {
                    // 이미 신고를 진행중일 경우
                    sendMessageWithPrefix(sender, "${msg.getString("report_fail_already_reporting")}")

                } else if (userCooldown > System.currentTimeMillis()) {
                    // 신고 쿨타임이 적용중일 경우
                    val time = (userCooldown - System.currentTimeMillis()) / 1000L
                    val text = msg.getString("report_fail_cooldown")!!

                    if (time > 60) {
                        sendMessageWithPrefix(sender, text.replace("%time%", "${time / 60}${min} ${time % 60}${sec}"))
                    } else {
                        sendMessageWithPrefix(sender, text.replace("%time%", "${time}${sec}"))
                    }
                }

                // 신고 동의중 다른 명령어를 입력할 경우 동의를 취소함
                if (userLastCommand != "/report ${args[0]}" && userReportConfirm) {
                    reportConfirm?.remove(sender.uniqueId)
                }
            }).register()

        // 신고 동의
        CommandAPICommand("report-accept")
            .withAliases("신고동의")
            .withArguments(PlayerArgument("player"))
            .executesPlayer(PlayerCommandExecutor { sender, args ->
                val reportPlayer = args[0] as? Player
                val reportPlayerUUID = reportedPlayer?.get(reportPlayer?.uniqueId)
                val reportedPlayer =  if (reportPlayerUUID != null) Bukkit.getPlayer(reportPlayerUUID) else null
                val userReporting = reporting?.get(reportPlayer?.uniqueId) ?: false
                val userAcceptedReport = acceptedReport?.get(reportPlayer?.uniqueId)?.get(sender.uniqueId) ?: false

                if (userReporting && reportedPlayer != null && reportedPlayer != sender && !userAcceptedReport) {
                    acceptedReport!![reportPlayer?.uniqueId]!![sender.uniqueId] = true
                    sender.playSound(sender.location, "minecraft:block.note_block.pling", 1f, 1f)
                    sendMessageWithPrefix(sender, "${msg.getString("report_accept_success")
                        ?.replace("%reporter%", "${reportPlayer?.name}")}")
                } else if (!userReporting || reportedPlayer == null) {
                    // 신고를 진행중이지 않을 경우
                    sendMessageWithPrefix(sender, "${msg.getString("report_accept_fail_not_reporting")}")
                } else if (reportedPlayer == sender) {
                    // 본인의 피신고자인 경우 신고를 동의 할 수 없음
                    sendMessageWithPrefix(sender, "${msg.getString("report_accept_fail_self")}")
                } else {
                    // 이미 동의를 했을 경우
                    sendMessageWithPrefix(sender, "${msg.getString("report_accept_fail_already_accepted")}")
                }
            }).register()

        // 신고 취소
        CommandAPICommand("report-cancel")
            .withAliases("신고취소")
            .executesPlayer(PlayerCommandExecutor { sender, _ ->
                val userReporting = reporting?.get(sender.uniqueId) ?: false
                val reportedPlayer = reportedPlayer?.get(sender.uniqueId) ?: ""

                val reporter = sender.name

                if (userReporting) {
                    reporting?.remove(sender.uniqueId)
                    sender.playSound(sender.location, "minecraft:entity.player.levelup", 1f, 1f)
                    sendMessageWithPrefix(sender, "${msg.getString("report_cancel_success")}")
                    resetValue(sender)

                    Bukkit.getOnlinePlayers().forEach {
                        if (it == sender) {
                            sendMessageWithPrefix(it, "${msg.getString("report_cancel_success_broadcast")
                                ?.replace("%reporter%", reporter)
                                ?.replace("%reportee%", Bukkit.getPlayer(reportedPlayer as UUID)?.name ?: "null")}")
                        }
                    }
                } else {
                    // 신고를 진행중이지 않을 경우
                    sendMessageWithPrefix(sender, "${msg.getString("report_cancel_fail_not_reporting")}")
                }
            }).register()

        // 신고 확인
        CommandAPICommand("report-check")
            .withAliases("신고확인")
            .withArguments(PlayerArgument("player").setOptional(true))
            .executesPlayer(PlayerCommandExecutor { sender, args ->
                if (args[0] == null) {
                    val usersText = msg.getStringList("report_check_info")
                    sendMessageWithPrefix(sender, usersText[0]
                        ?.replace("%reporterCount%", "${reporting?.size ?: 0}")!!)

                    val userTextS = usersText[1]?.split("%reporters%") ?: listOf("")
                    var usersTextComponent = Component.text(userTextS[0])

                    reporting?.let {
                        var i = 0
                        it.forEach { (uuid, _) ->
                            i++
                            val playerName = Bukkit.getPlayer(uuid)?.name
                            val tmp = Component.text("$playerName")
                                .clickEvent(ClickEvent.runCommand("/report-check $playerName"))
                                .hoverEvent(Component.text("${msg.getString("report_check_info_player_hover")
                                    ?.replace("%reporter%", sender.name)}"))
                            usersTextComponent = usersTextComponent.append(tmp)
                            if (it.size != i) {
                                usersTextComponent = usersTextComponent.append(Component.text(", "))
                            }
                        }
                    }

                    usersTextComponent.append(Component.text(userTextS[1]))
                    sendMessageWithPrefix(sender, usersTextComponent)
                } else {
                    // 신고 확인
                    val reportPlayer = args[0] as? Player
                    val reportedPlayerUUID = reportedPlayer?.get(reportPlayer?.uniqueId) ?: ""
                    val userReporting = reporting?.get(reportPlayer?.uniqueId) ?: false
                    val reportedReason = reportedReason?.get(reportPlayer?.uniqueId) ?: "${msg.getString("NonReason")}"
                    val elapsedTime = elapsedTime?.get(reportPlayer?.uniqueId) ?: 0L

                    if (userReporting) {
                        msg.getStringList("report_check_player").forEach {
                            val tmp: Component
                            if (it.contains("%acceptButton")) {
                                val s = it.split("%acceptButton%")
                                tmp = Component.text(s[0]
                                    .replace("%nowAcceptedPlayer%", "${acceptedReport?.get(reportPlayer?.uniqueId)?.size ?: 0}")
                                    .replace("%maxAcceptedPlayer%", "${Math.round(Bukkit.getOnlinePlayers().size * minimumAcceptPlayersRatio)}"))

                                    .append(Component.text("${msg.getString("report_check_player_accept_button")}")
                                        .clickEvent(ClickEvent.runCommand("/report-accept ${reportPlayer?.name}"))
                                        .hoverEvent(Component.text("${msg.getString("report_check_player_accept_hover")?.replace("%reporter%", sender.name)}")))
                                    .append(Component.text(s[1]))
                            } else {
                                tmp = Component.text(it
                                    .replace("%reporter%", "${reportPlayer?.name}")
                                    .replace("%reportee%", Bukkit.getPlayer(reportedPlayerUUID as UUID)?.name ?: "null")
                                    .replace("%reason%", reportedReason)
                                    .replace("%time%", "${(reportAgreementTime - elapsedTime) / 1000L / 60}${msg.getString("minute")} ${(reportAgreementTime- elapsedTime) / 1000L % 60}${msg.getString("second")}"))
                            }
                            sendMessageWithPrefix(sender, tmp)
                        }
                    } else {
                        // 신고를 진행중이지 않을 경우
                        sendMessageWithPrefix(sender, "${msg.getString("report_check_fail_not_reporting")}")
                    }
                }
            }).register()

        // 신고 디버그
        CommandAPICommand("report-debug")
            .withPermission("report.debug")
            .withArguments(PlayerArgument("player").setOptional(true))
            .executesPlayer(PlayerCommandExecutor { sender, args ->
                val player = args[0] as? Player
                val userCooldown = commandCooldown?.get(player?.uniqueId) ?: 0L

                if (args[0] != null) {
                    sender.sendMessage("§c[!] §f-- 유저 정보 --")
                    if (userCooldown != 0L) {
                        sender.sendMessage("§c[!] §f쿨타임: §e${(userCooldown - System.currentTimeMillis()) / 1000L / 60}분 ${((userCooldown - System.currentTimeMillis()) / 1000L) % 60}초")
                    } else {
                        sender.sendMessage("§c[!] §f쿨타임: §e0초")
                    }
                    sender.sendMessage("§c[!] §f신고 동의: §e${reportConfirm?.get(player?.uniqueId) ?: false}")
                    sender.sendMessage("§c[!] §f신고 활성화: §e${reporting?.get(player?.uniqueId) ?: false}")
                    sender.sendMessage("§c[!] §f최근 명령어: §e${lastCommand?.get(player?.uniqueId) ?: "null"}")
                } else {
                    sender.sendMessage("§c[!] §f-- 신고 시스템 디버그 --")
                    sender.sendMessage("§c[!] §f최소 플레이어 수: §e${minimumPlayers}명")
                    sender.sendMessage("§c[!] §f신고를 승인하기 위한 최소 신고 동의자 비율: §e${Math.round(Bukkit.getOnlinePlayers().size * minimumAcceptPlayersRatio)}명")
                    sender.sendMessage("§c[!] §f최소 신고 동의자 비율: §e${minimumAcceptPlayersRatio * 100}%")
                    sender.sendMessage("§c[!] §f최대 동시 신고자 수: §e${maxReportCount}회")
                    sender.sendMessage("§c[!] §f처벌 레벨: §e${punishmentLevel}")
                    sender.sendMessage("§c[!] §f킥 동의 시간: §e${reportAgreementTime / 1000L / 60}분")
                    sender.sendMessage("§c[!] §f쿨타임: §e${tenMinutes / 1000L / 60}분")
                    sender.sendMessage("§c[!] §f최근 신고자: §e${lastReporter?.let { Bukkit.getPlayer(it)?.name }}")
                }
            }).register()

        // 신고 시스템 초기화
        CommandAPICommand("report-reset")
            .withArguments(BooleanArgument("RESET-reports.yml-FILE").setOptional(true))
            .withPermission("report.reset")
            .executesPlayer(PlayerCommandExecutor { sender, args ->
                commandCooldown?.clear()
                reportConfirm?.clear()
                lastCommand?.clear()
                reporting?.clear()
                reportedPlayer?.clear()
                reportedReason?.clear()
                acceptedReport?.clear()

                lastReporter = null

                if (args[0] == true) {
                    cfg.save("reports.yml", true)
                }

                sender.sendMessage("§c[!] §f신고 시스템이 초기화되었습니다.")
            }).register()
    }

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        val player = e.player

        if (player.hasPermission("report.notice")) {
            val reports = cfg.get("reports.yml")
            var reportCount = 0

            reports.getConfigurationSection("reports")?.getKeys(false)?.forEach {reporteeUUID ->
                reports.getIntegerList("reports.${reporteeUUID}.aliveID").forEach { id ->
                    if (!reports.getStringList("reports.${reporteeUUID}.data.${id}.checked").contains(player.uniqueId.toString())) {
                        reportCount++
                    }
                }
            }

            if (reportCount != 0) {
                msg.getStringList("notice_unread_reports").forEach {
                    // TODO : 신고 확인 명령어 추가
                    val tmp : Component
                    if (it.contains("%reports-check-command%")) {
                        tmp = Component.text(it.replace("%reports-check-command%", "§e/something"))
                            .clickEvent(ClickEvent.runCommand("/something"))
                            .hoverEvent(Component.text("${msg.getString("notice_unread_reports_hover")}"))
                    } else {
                        tmp = Component.text(it.replace("%reportCount%", "$reportCount"))
                    }

                    sendMessageWithPrefix(player, tmp)
                }
            }
        }
    }

    private fun resetValue(player: Player) {
        reporting?.remove(player.uniqueId)
        reportedPlayer?.remove(player.uniqueId)
        reportedReason?.remove(player.uniqueId)
        acceptedReport?.remove(player.uniqueId)
        elapsedTime?.remove(player.uniqueId)
    }

    private fun sendMessageWithPrefix(player: Player, message: String) {
        player.sendMessage("${config.getString("prefix")} §r${message}")
    }
    private fun sendMessageWithPrefix(player: Player, message: Component) {
        player.sendMessage(Component.text("${config.getString("prefix")} §r").append(message))
    }
}
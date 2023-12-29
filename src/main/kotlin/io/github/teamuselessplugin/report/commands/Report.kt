package io.github.teamuselessplugin.report.commands

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import io.github.monun.heartbeat.coroutines.HeartbeatScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.EventListener
import java.util.UUID

class Report : EventListener {
    private var commandCooldown: HashMap<UUID, Long>? = HashMap()
    private var reportConfirm: HashMap<UUID, Boolean>? = HashMap()
    private var lastCommand: HashMap<UUID, String>? = HashMap()

    // 모두 종료후 해당 플레이어를 제거해야함
    private var reporting: HashMap<UUID, Boolean>? = HashMap()
    private var reportedPlayer: HashMap<UUID, UUID>? = HashMap()
    private var reportedReason: HashMap<UUID, String>? = HashMap()
    private var acceptedReport: HashMap<UUID, HashMap<UUID, Boolean>>? = HashMap()
    private var elapsedTime: HashMap<UUID, Long>? = HashMap()

    private var lastReporter: UUID? = null

//    private val anonymous = false
    private val minimumPlayers = 2
    private val minimumAcceptPlayersRatio = 0.5
    private val maxReportCount = 5
    private val punishmentLevel = 1 // 0: report.notice 펄미션이 있는 플레이어들에게 알림 1: 킥 2: 밴
    private val reportAgreementTime = 1000L * 60 * 5
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
                        sender.sendMessage("§c[!] §f신고를 하기 전에 동의를 해주세요.")
                        sender.sendMessage("§c[!] §f동의하시려면 명령어를 §e한 번더§f 입력해주세요.")
                        reportConfirm!![sender.uniqueId] = true

                    } else if (userLastCommand == "/report ${args[0]}") {
                        reportConfirm?.remove(sender.uniqueId)
                        commandCooldown!![sender.uniqueId] = System.currentTimeMillis() + tenMinutes
                        lastReporter = sender.uniqueId

                        sender.sendMessage("§c[!] §f--------------------------")
                        sender.sendMessage("§c[!] §f신고가 접수되었습니다. | 서버 처벌 레벨: §e${punishmentLevel}")
                        sender.sendMessage("§c[!] §f사유 : §e${args[1] ?: "없음"}")
                        sender.sendMessage("§c[!] §f신고 투표는 §e${reportAgreementTime / 1000L / 60}분§f동안 진행되며 신고 투표중에 나갈경우 신고가 취소됩니다.")
                        sender.sendMessage(
                            Component.text("§c[!] §f신고를 취소하시려면 ")
                                .append(
                                    Component.text("§e'/report-cancel'")
                                        .clickEvent(ClickEvent.runCommand("/report-cancel"))
                                        .hoverEvent(Component.text("클릭하여 신고를 취소합니다."))
                                )
                                .append(Component.text("§f를 입력해주세요."))
                        )
                        sender.sendMessage("§c[!] §f--------------------------")

                        // 변수 세팅
                        reporting!![sender.uniqueId] = true
                        reportedPlayer!![sender.uniqueId] = rPlayer.uniqueId
                        reportedReason!![sender.uniqueId] = (args[1] ?: "없음").toString()
                        // 본인은 자동 동의
                        acceptedReport!![sender.uniqueId] = HashMap()
                        acceptedReport!![sender.uniqueId]!![sender.uniqueId] = true

                        // 신고 접수를 위한 투표 시작
                        Bukkit.getOnlinePlayers().forEach {
                            if (it != sender) {
                                it.playSound(it.location, "minecraft:block.note_block.pling", 1f, 1f)
                                it.sendMessage(Component.text("§c[!] §f--------------------------"))
                                it.sendMessage(Component.text("§c[!] §e'${sender.name}'§f님이 §e'${rPlayer.name}'§f님을 신고하셨습니다."))
                                it.sendMessage(Component.text("§c[!] §f신고 사유: §e${args[1] ?: "없음"}"))
                                it.sendMessage(
                                    Component.text("§c[!] §f신고 내용이 합당하다고 판단하시면 ")
                                        .append(
                                            Component.text("§e'/report-accept'")
                                                .clickEvent(ClickEvent.runCommand("/report-accept ${sender.name}"))
                                                .hoverEvent(Component.text("클릭하여 플레이어의 신고를 승인합니다."))
                                        )
                                        .append(Component.text("§f를 입력해주세요."))
                                )
                                it.sendMessage(Component.text("§c[!] §f--------------------------"))
                            }
                        }

                        HeartbeatScope().launch {
                            var ticks = 0L

                            while (true) {
                                delay(50L)
                                ticks += 50L
                                elapsedTime!![sender.uniqueId] = ticks

                                if (sender.isOnline) {
                                    if (ticks >= reportAgreementTime && acceptedReport?.get(sender.uniqueId)?.size!! < Math.round(Bukkit.getOnlinePlayers().size * minimumAcceptPlayersRatio)) {
                                        Bukkit.getOnlinePlayers().forEach {
                                            if (it != sender) {
                                                it.sendMessage("§c[!] §e'${sender.name}'§f님이 §e'${rPlayer.name}'§f님을 신고 후 §e${reportAgreementTime / 1000L / 60}분§f 동안 투표자 수를 채우지 못하여 신고가 취소되었습니다.")
                                            }
                                        }
                                        resetValue(sender)

                                        break
                                    } else if (acceptedReport?.get(sender.uniqueId)?.size!! >= Math.round(Bukkit.getOnlinePlayers().size * minimumAcceptPlayersRatio)) {
                                        Bukkit.getOnlinePlayers().forEach {
                                            it.sendMessage("§c[!] §e'${sender.name}'§f님의 신고가 투표자 수를 채워 승인되었습니다.")
                                            resetValue(sender)

                                            when (punishmentLevel) {
                                                0 -> {
                                                    // TODO: report.notice 펄미션을 가진 플레이어들에게 알림
                                                }

                                                1 -> {
                                                    rPlayer.kick(
                                                        Component.text(
                                                            "§c[!] §f${sender.name}님의 신고가 승인되어 킥되었습니다.\n" +
                                                                    "신고 사유: ${args[1] ?: "없음"}\n\n" +
                                                                    "자세한 사항은 관리자에게 문의해주세요."
                                                        )
                                                    )
                                                }

                                                2 -> {
                                                    rPlayer.banPlayer(
                                                        "§c[!] §f${sender.name}님의 신고가 승인되어 밴되었습니다.\n" +
                                                                "신고 사유: ${args[1] ?: "없음"}\n\n" +
                                                                "자세한 사항은 관리자에게 문의해주세요."
                                                    )
                                                }
                                            }
                                        }

                                        break
                                    }
                                } else {
                                    resetValue(sender)
                                    Bukkit.getOnlinePlayers().forEach {
                                        it.sendMessage("§c[!] §e'${sender.name}'§f님이 로그아웃 하여 ${rPlayer.name}님에 대한 신고가 취소되었습니다.")
                                    }

                                    break
                                }
                            }
                        }
                    }

                } else if (rPlayer.hasPermission("report.bypass")) {
                    sender.sendMessage("§c[!] §f해당 플레이어는 신고할 수 없습니다.")

                } else if (reportedPlayer?.size!! > maxReportCount) {
                    sender.sendMessage("§c[!] §f신고자가 너무 많습니다. 신고를 진행할 수 없습니다.")

                } else if (args[0] == sender) {
                    sender.sendMessage("§c[!] §f자기 자신을 신고할 수 없습니다.")

                } else if (Bukkit.getOnlinePlayers().size < minimumPlayers) {
                    sender.sendMessage("§c[!] §f신고를 하기 위해서는 최소 §e${minimumPlayers}명§f 이상의 플레이어가 접속해 있어야 합니다.")

                } else if (userReporting) {
                    sender.sendMessage("§c[!] §f당신은 이미 신고를 진행 하고 있습니다.")

                } else if (userCooldown > System.currentTimeMillis()) {
                    val time = (userCooldown - System.currentTimeMillis()) / 1000L
                    val text = "§c[!] §f당신은 아직 신고를 할 수 없습니다. §e**ReWrite**§f 후에 다시 시도해주세요."

                    if (time > 60) {
                        sender.sendMessage(text.replace("**ReWrite**", "${time / 60}분 ${time % 60}초"))
                    } else {
                        sender.sendMessage(text.replace("**ReWrite**", "${time}초"))
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
                val reportedPlayer = Bukkit.getPlayer(reportedPlayer?.get(reportPlayer?.uniqueId) as UUID)
                val userReporting = reporting?.get(reportPlayer?.uniqueId) ?: false
                val userAcceptedReport = acceptedReport?.get(reportPlayer?.uniqueId)?.get(sender.uniqueId) ?: false

                if (userReporting && reportedPlayer != sender && !userAcceptedReport) {
                    acceptedReport!![reportPlayer?.uniqueId]!![sender.uniqueId] = true
                    sender.playSound(sender.location, "minecraft:block.note_block.pling", 1f, 1f)
                    sender.sendMessage("§c[!] §f${reportPlayer?.name}님의 신고를 승인하셨습니다.")
                } else if (!userReporting) {
                    sender.sendMessage("§c[!] §f신고를 진행중이지 않은 플레이어입니다.")
                } else if (reportedPlayer == sender) {
                    sender.sendMessage("§c[!] §f자기 자신에 대한 신고를 승인할 수 없습니다.")
                } else {
                    sender.sendMessage("§c[!] §f이미 신고를 승인하셨습니다.")
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
                    sender.sendMessage("§c[!] §f신고가 취소되었습니다.")

                    resetValue(sender)

                    Bukkit.getOnlinePlayers().forEach {
                        if (it == sender) {
                            it.sendMessage(Component.text("§c[!] §e'${reporter}'§f님이 §e'${Bukkit.getPlayer(reportedPlayer as UUID)?.name}'§f님에 대한 신고를 취소하셨습니다."))
                        }
                    }
                } else {
                    sender.sendMessage("§c[!] §f당신은 신고를 진행하고 있지 않습니다.")
                }
            }).register()

        // 신고 확인
        CommandAPICommand("report-check")
            .withAliases("신고확인")
            .withArguments(PlayerArgument("player").setOptional(true))
            .executesPlayer(PlayerCommandExecutor { sender, args ->
                if (args[0] == null) {
                    sender.sendMessage("§c[!] §f현재 신고를 진행중인 플레이어: §e${reporting?.size ?: 0}명")

                    var usersText = Component.text("§c[!] §f신고를 진행중인 플레이어 목록: ")
                    reporting?.let {
                        var i = 0
                        it.forEach { (uuid, _) ->
                            i++
                            val playerName = Bukkit.getPlayer(uuid)?.name
                            val tmp = Component.text("$playerName").clickEvent(ClickEvent.runCommand("/report-check $playerName")).hoverEvent(Component.text("클릭하여 ${playerName}님의 신고 정보를 확인합니다."))
                            usersText = usersText.append(tmp)

                            if (it.size != i) {
                                usersText = usersText.append(Component.text(", "))
                            }
                        }
                    }

                    sender.sendMessage(usersText)
                } else {
                    val reportPlayer = args[0] as? Player
                    val reportedPlayerUUID = reportedPlayer?.get(reportPlayer?.uniqueId) ?: ""
                    val userReporting = reporting?.get(reportPlayer?.uniqueId) ?: false
                    val reportedReason = reportedReason?.get(reportPlayer?.uniqueId) ?: "없음"
                    val elapsedTime = elapsedTime?.get(reportPlayer?.uniqueId) ?: 0L

                    if (userReporting) {
                        sender.sendMessage("§c[!] §f--------------------------")
                        sender.sendMessage("§c[!] §e'${reportPlayer?.name}'§f님이 §e'${Bukkit.getPlayer(reportedPlayerUUID as UUID)?.name}'§f님을 신고하셨습니다.")
                        sender.sendMessage("§c[!] §f신고 사유: §e${reportedReason}")
                        sender.sendMessage(
                            Component.text("§c[!] §f신고 동의자 수: §e${acceptedReport?.get(reportPlayer?.uniqueId)?.size ?: 0}/${Math.round(Bukkit.getOnlinePlayers().size * minimumAcceptPlayersRatio)}명 ").append(Component.text("§c[동의]").clickEvent(ClickEvent.runCommand("/report-accept ${reportPlayer?.name}")).hoverEvent(Component.text("클릭하여 ${reportPlayer?.name}님의 신고를 승인합니다."))))
                        sender.sendMessage("§c[!] §f남은 시간: §e${(reportAgreementTime - elapsedTime) / 1000L / 60}분 ${(reportAgreementTime- elapsedTime) / 1000L % 60}초")
                        sender.sendMessage("§c[!] §f--------------------------")
                    } else {
                        sender.sendMessage("§c[!] §f신고를 진행중이지 않은 플레이어입니다.")
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
            .withPermission("report.reset")
            .executesPlayer(PlayerCommandExecutor { sender, _ ->
                commandCooldown?.clear()
                reportConfirm?.clear()
                lastCommand?.clear()
                reporting?.clear()
                reportedPlayer?.clear()
                reportedReason?.clear()
                acceptedReport?.clear()

                lastReporter = null

                sender.sendMessage("§c[!] §f신고 시스템이 초기화되었습니다.")
            }).register()
    }

    private fun resetValue(player: Player) {
        reporting?.remove(player.uniqueId)
        reportedPlayer?.remove(player.uniqueId)
        reportedReason?.remove(player.uniqueId)
        acceptedReport?.remove(player.uniqueId)
        elapsedTime?.remove(player.uniqueId)
    }
}
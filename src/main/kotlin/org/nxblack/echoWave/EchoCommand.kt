package org.nxblack.echoWave

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class EchoCommand(private val plugin: EchoWave) : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (plugin.isVoiceBanned(sender as Player)) {
            sender.sendMessage("§c[EchoWave] You are banned from voice chat.")
            return true
        }

        if (sender !is Player)
            return true

        if (args.isEmpty()) {
            sender.sendMessage("§bEchoWave")
            sender.sendMessage("§7/echo mute")
            sender.sendMessage("§7/echo invite <player>")
            sender.sendMessage("§7/echo join <code>")
            sender.sendMessage("§7/echo leave")
            sender.sendMessage("§7/echo members")
            return true
        }

        when (args[0].lowercase()) {

            "mute" -> {
                val settings = plugin.getPlayerSettings(sender)
                settings.muted = !settings.muted
                sender.sendMessage(
                    if(settings.muted)"§cVoice chat muted."
                    else "§aVoice chat unmuted."
                )

            }

            "invite" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /echo invite <player>")
                    return true
                }
                plugin.invitePlayer(sender, args[1])

            }

            "join" -> {

                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /echo join <code>")
                    return true
                }
                plugin.joinGroup(sender, args[1].uppercase())
            }

            "leave" -> {
                plugin.leaveGroup(sender)
            }

            "members" -> {
                plugin.showMembers(sender)
            }

            "kick" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /echo kick <player>")
                    return true
                }

                plugin.kickPlayer(sender, args[1])
            }

            "transfer" -> {
                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /echo transfer <player>")
                    return true
                }

                plugin.transferOwnership(sender, args[1])
            }
            "voiceban" -> {

                if (!sender.hasPermission("echowave.admin")) {
                    sender.sendMessage("§cNo permission.")
                    return true
                }

                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /echo voiceban <player>")
                    return true
                }

                val target = Bukkit.getPlayerExact(args[1])

                if (target == null) {
                    sender.sendMessage("§cPlayer not found.")
                    return true
                }

                plugin.setVoiceBan(target, true)

                Bukkit.broadcastMessage(
                    "§c[EchoWave] ${target.name} has been banned from voice chat."
                )
            }

            else -> {
                sender.sendMessage("§cUnknown subcommand.")
            }
        }

        return true
    }
}
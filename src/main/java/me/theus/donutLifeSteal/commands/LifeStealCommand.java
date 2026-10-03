package me.theus.donutLifeSteal.commands;

import me.theus.donutLifeSteal.DonutLifeSteal;
import me.theus.donutLifeSteal.models.LifeStealUser;
import me.theus.donutLifeSteal.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.*;

public class LifeStealCommand implements CommandExecutor, TabCompleter {

    private final DonutLifeSteal plugin;

    public LifeStealCommand(DonutLifeSteal plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload") || sub.equals("rl")) {
            if (!sender.hasPermission("donutlifesteal.admin")) {
                sender.sendMessage(plugin.getMessage("NO-PERMISSION", "&cVocê não possui permissão para executar este comando."));
                return true;
            }
            plugin.reloadPlugin();
            sender.sendMessage(plugin.getMessage("RELOAD-SUCCESS", "&aConfigurações e banco de dados do DonutLifeSteal recarregados com sucesso!"));
            return true;
        }

        if (sub.equals("give") || sub.equals("add")) {
            if (!sender.hasPermission("donutlifesteal.admin")) {
                sender.sendMessage(plugin.getMessage("NO-PERMISSION", "&cVocê não possui permissão para executar este comando."));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Utils.color("&cUso correto: /" + label + " give <jogador> <quantidade>"));
                return true;
            }

            String targetName = args[1];
            int amount = parsePositiveInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage(plugin.getMessage("INVALID-NUMBER", "&cA quantidade informada é inválida."));
                return true;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null && target.isOnline()) {
                LifeStealUser user = plugin.getManager().getUser(target.getUniqueId());
                if (user == null) user = plugin.getManager().loadUser(target);

                int maxHearts = plugin.getConfig().getInt("MAX-HEARTS", 20);
                int newHearts = Math.min(maxHearts, user.getHearts() + amount);
                int added = newHearts - user.getHearts();

                user.setHearts(newHearts);
                plugin.getManager().applyPlayerHearts(target, user.getHearts());
                target.setHealth(Math.min(Utils.getPlayerMaxHealth(target), target.getHealth() + (added * 2.0)));
                plugin.getDatabase().saveUser(user);

                target.sendMessage(plugin.getMessage("HEART-RECEIVED", "&fVocê recebeu &#FF0000+{amount} Coração").replace("{amount}", String.valueOf(amount)));
                sender.sendMessage(plugin.getMessage("HEART-GIVEN-SENDER", "&aVocê adicionou &#FF0000+{amount} Coração(ões) &apara &#FFF000{player}&a.")
                        .replace("{amount}", String.valueOf(amount))
                        .replace("{player}", target.getName()));
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
                if (offline == null || offline.getUniqueId() == null) {
                    sender.sendMessage(plugin.getMessage("PLAYER-NOT-FOUND", "&cJogador não encontrado ou offline."));
                    return true;
                }
                int defaultHearts = plugin.getConfig().getInt("HEART-DEFAULT.HEART-PER-PLAYER", 4);
                LifeStealUser user = plugin.getDatabase().loadUser(offline.getUniqueId(), offline.getName(), defaultHearts);
                int maxHearts = plugin.getConfig().getInt("MAX-HEARTS", 20);
                user.setHearts(Math.min(maxHearts, user.getHearts() + amount));
                plugin.getDatabase().saveUser(user);

                sender.sendMessage(plugin.getMessage("HEART-GIVEN-SENDER", "&aVocê adicionou &#FF0000+{amount} Coração(ões) &apara &#FFF000{player}&a.")
                        .replace("{amount}", String.valueOf(amount))
                        .replace("{player}", offline.getName() != null ? offline.getName() : targetName));
            }
            return true;
        }

        if (sub.equals("remove") || sub.equals("take")) {
            if (!sender.hasPermission("donutlifesteal.admin")) {
                sender.sendMessage(plugin.getMessage("NO-PERMISSION", "&cVocê não possui permissão para executar este comando."));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Utils.color("&cUso correto: /" + label + " remove <jogador> <quantidade>"));
                return true;
            }

            String targetName = args[1];
            int amount = parsePositiveInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage(plugin.getMessage("INVALID-NUMBER", "&cA quantidade informada é inválida."));
                return true;
            }

            int minHearts = plugin.getConfig().getInt("MIN-HEARTS", plugin.getConfig().getInt("HEART-DEFAULT.HEART-PER-PLAYER", 4));

            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null && target.isOnline()) {
                LifeStealUser user = plugin.getManager().getUser(target.getUniqueId());
                if (user == null) user = plugin.getManager().loadUser(target);

                if (user.getHearts() <= minHearts) {
                    sender.sendMessage(plugin.getMessage("MIN-HEARTS-REACHED", "&cEste jogador já está no limite mínimo base de corações!"));
                    return true;
                }

                int newHearts = Math.max(minHearts, user.getHearts() - amount);
                int removed = user.getHearts() - newHearts;

                user.setHearts(newHearts);
                plugin.getManager().applyPlayerHearts(target, user.getHearts());
                target.setHealth(Math.min(Utils.getPlayerMaxHealth(target), target.getHealth()));
                plugin.getDatabase().saveUser(user);

                target.sendMessage(plugin.getMessage("HEART-REMOVED", "&fRemovido &#FF0000+{amount} Coração").replace("{amount}", String.valueOf(removed)));
                sender.sendMessage(plugin.getMessage("HEART-REMOVED-SENDER", "&cVocê removeu &#FF0000-{amount} Coração(ões) &cde &#FFF000{player}&c.")
                        .replace("{amount}", String.valueOf(removed))
                        .replace("{player}", target.getName()));
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
                if (offline == null || offline.getUniqueId() == null) {
                    sender.sendMessage(plugin.getMessage("PLAYER-NOT-FOUND", "&cJogador não encontrado ou offline."));
                    return true;
                }
                int defaultHearts = plugin.getConfig().getInt("HEART-DEFAULT.HEART-PER-PLAYER", 4);
                LifeStealUser user = plugin.getDatabase().loadUser(offline.getUniqueId(), offline.getName(), defaultHearts);
                user.setHearts(Math.max(minHearts, user.getHearts() - amount));
                plugin.getDatabase().saveUser(user);

                sender.sendMessage(plugin.getMessage("HEART-REMOVED-SENDER", "&cVocê removeu &#FF0000-{amount} Coração(ões) &cde &#FFF000{player}&c.")
                        .replace("{amount}", String.valueOf(amount))
                        .replace("{player}", offline.getName() != null ? offline.getName() : targetName));
            }
            return true;
        }

        if (sub.equals("set")) {
            if (!sender.hasPermission("donutlifesteal.admin")) {
                sender.sendMessage(plugin.getMessage("NO-PERMISSION", "&cVocê não possui permissão para executar este comando."));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Utils.color("&cUso correto: /" + label + " set <jogador> <quantidade>"));
                return true;
            }

            String targetName = args[1];
            int amount = parsePositiveInt(args[2]);
            if (amount <= 0) {
                sender.sendMessage(plugin.getMessage("INVALID-NUMBER", "&cA quantidade informada é inválida."));
                return true;
            }

            int minHearts = plugin.getConfig().getInt("MIN-HEARTS", 4);
            int maxHearts = plugin.getConfig().getInt("MAX-HEARTS", 20);
            int bounded = Math.max(minHearts, Math.min(maxHearts, amount));

            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null && target.isOnline()) {
                LifeStealUser user = plugin.getManager().getUser(target.getUniqueId());
                if (user == null) user = plugin.getManager().loadUser(target);

                user.setHearts(bounded);
                plugin.getManager().applyPlayerHearts(target, bounded);
                target.setHealth(Math.min(Utils.getPlayerMaxHealth(target), target.getHealth()));
                plugin.getDatabase().saveUser(user);

                sender.sendMessage(plugin.getMessage("HEART-SET-SENDER", "&eVocê definiu a vida de &#FFF000{player} &epara &#FF0000{amount} Coração(ões)&e.")
                        .replace("{amount}", String.valueOf(bounded))
                        .replace("{player}", target.getName()));
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
                if (offline == null || offline.getUniqueId() == null) {
                    sender.sendMessage(plugin.getMessage("PLAYER-NOT-FOUND", "&cJogador não encontrado ou offline."));
                    return true;
                }
                int defaultHearts = plugin.getConfig().getInt("HEART-DEFAULT.HEART-PER-PLAYER", 4);
                LifeStealUser user = plugin.getDatabase().loadUser(offline.getUniqueId(), offline.getName(), defaultHearts);
                user.setHearts(bounded);
                plugin.getDatabase().saveUser(user);

                sender.sendMessage(plugin.getMessage("HEART-SET-SENDER", "&eVocê definiu a vida de &#FFF000{player} &epara &#FF0000{amount} Coração(ões)&e.")
                        .replace("{amount}", String.valueOf(bounded))
                        .replace("{player}", offline.getName() != null ? offline.getName() : targetName));
            }
            return true;
        }

        if (sub.equals("giveitem") || sub.equals("heart")) {
            if (!sender.hasPermission("donutlifesteal.admin")) {
                sender.sendMessage(plugin.getMessage("NO-PERMISSION", "&cVocê não possui permissão para executar este comando."));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Utils.color("&cUso correto: /" + label + " giveitem <jogador> [quantidade]"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(plugin.getMessage("PLAYER-NOT-FOUND", "&cJogador não encontrado ou offline."));
                return true;
            }

            int amount = 1;
            if (args.length >= 3) {
                amount = parsePositiveInt(args[2]);
                if (amount <= 0) amount = 1;
            }

            for (int i = 0; i < amount; i++) {
                UUID creator = (sender instanceof Player) ? ((Player) sender).getUniqueId() : null;
                ItemStack heart = plugin.getManager().createHeartItem(creator);
                target.getInventory().addItem(heart);
            }

            sender.sendMessage(plugin.getMessage("HEART-ITEM-GIVEN", "&aVocê entregou &#FF0000{amount} item(ns) de coração &apara &#FFF000{player}&a.")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{player}", target.getName()));
            return true;
        }

        sendHelp(sender, label);
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Utils.color("&#FFF000&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        sender.sendMessage(Utils.color("               &#FF0000&lDONUT LIFESTEAL &7- Ajuda"));
        sender.sendMessage(Utils.color(" &7/" + label + " give <jogador> <quantidade> &f- Adiciona corações"));
        sender.sendMessage(Utils.color(" &7/" + label + " remove <jogador> <quantidade> &f- Remove corações"));
        sender.sendMessage(Utils.color(" &7/" + label + " set <jogador> <quantidade> &f- Define os corações"));
        sender.sendMessage(Utils.color(" &7/" + label + " giveitem <jogador> [quantidade] &f- Entrega o item"));
        sender.sendMessage(Utils.color(" &7/" + label + " reload &f- Recarrega as configurações"));
        sender.sendMessage(Utils.color("&#FFF000&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
    }

    private int parsePositiveInt(String str) {
        try {
            int val = Integer.parseInt(str);
            return val > 0 ? val : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("donutlifesteal.admin")) {
            return completions;
        }

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("give", "remove", "set", "giveitem", "reload");
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("remove") || sub.equals("set") || sub.equals("giveitem")) {
                List<String> playerNames = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
                StringUtil.copyPartialMatches(args[1], playerNames, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("remove") || sub.equals("set") || sub.equals("giveitem")) {
                List<String> numbers = Arrays.asList("1", "2", "3", "4", "5", "10");
                StringUtil.copyPartialMatches(args[2], numbers, completions);
                Collections.sort(completions);
                return completions;
            }
        }

        return completions;
    }
}

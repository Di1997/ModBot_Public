package core;

import classes.modules.BotModule;
import classes.Tools;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import java.util.*;
import java.util.stream.Collectors;

import static classes.Tools.SendMessage;

/* TODO:
Implement list of currently active modules on the server [Testing]
Make a nice help command
 */


public class Commands {
    private static final LinkedHashMap<String, Command> Commands = new LinkedHashMap<>();

    public static void registerCommand(String name, Executable function, Boolean isDev) {
        Commands.put(name.toLowerCase(), new Command(function, isDev));
    }

    public static void registerCommand(String name, Executable function) {
        registerCommand(name, function, false);
    }

    public static Command getCommand(String command) {
        command = command.split(" ")[0];
        return Commands.getOrDefault(command.toLowerCase(), null);
    }

    public static void initCommands() {
        registerCommand("loadmodule", x -> {
            if(x instanceof PrivateMessageReceivedEvent) {
                PrivateMessageReceivedEvent event = (PrivateMessageReceivedEvent) x;

                List<String> message = new LinkedList<>(Arrays.asList(event.getMessage().getContentRaw().split(" ")));
                message.remove(0);

                String moduleName = String.join(" ", message);

                if(Modules.LoadModuleByName(moduleName)) {
                    try {
                        Modules.GetModule(moduleName).coreReadyModule(event.getJDA());
                    } catch (Exception e) {
                        event.getChannel().sendMessage(String.format("Error while initializing module '%s'", moduleName)).queue();
                    }
                    event.getChannel().sendMessage("Module loaded successfully").queue();
                } else {
                    event.getChannel().sendMessage(String.format("Unable to find module '%s'", moduleName)).queue();
                }
            }
        }, true);

        registerCommand("removemodule", x -> {
            if(x instanceof PrivateMessageReceivedEvent) {
                PrivateMessageReceivedEvent event = (PrivateMessageReceivedEvent) x;

                List<String> message = new LinkedList<>(Arrays.asList(event.getMessage().getContentRaw().split(" ")));
                message.remove(0);

                String moduleName = String.join(" ", message);

                try {
                    if (Modules.RemoveModuleByName(moduleName)) {
                        event.getChannel().sendMessage("Module removed successfully").queue();
                    } else {
                        event.getChannel().sendMessage(String.format("Module '%s' hasn't been loaded yet", moduleName)).queue();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    event.getChannel().sendMessage("Couldn't remove module!").queue();
                }
            }
        }, true);

        registerCommand("disablemodule", x -> {
            if(x instanceof PrivateMessageReceivedEvent) {
                PrivateMessageReceivedEvent event = (PrivateMessageReceivedEvent) x;

                List<String> message = new LinkedList<>(Arrays.asList(event.getMessage().getContentRaw().split(" ")));
                message.remove(0);

                String moduleName = String.join(" ", message);

                try {
                    if (Modules.DisableModuleByName(moduleName)) {
                        event.getChannel().sendMessage("Module disabled successfully successfully").queue();
                    } else {
                        event.getChannel().sendMessage(String.format("Module '%s' hasn't been loaded yet", moduleName)).queue();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    event.getChannel().sendMessage("Couldn't disable module!").queue();
                }
            }
        }, true);

        registerCommand("stop", x -> {
            if(x instanceof PrivateMessageReceivedEvent) {
                System.exit(0);
            }
        }, true);

        registerCommand("reloadcommands", x -> {
            if(x instanceof PrivateMessageReceivedEvent) {
                Collection<Guild> guilds = x.getJDA().getGuilds();
                for(Guild guild : guilds) {
                    guild.updateCommands().queue();
                    for(String moduleName : Modules.GetGuildModules(guild.getId())) {
                        BotModule module = Modules.GetModule(moduleName);

                        if(module.SlashCommands.size() > 0) {
                            CommandListUpdateAction update = guild.updateCommands();
                            for(BotModule.CommandInfo command : module.SlashCommands.values()) {
                                update = update.addCommands(command.getCommandData());
                            }

                            update.queue();
                        }
                    }
                }

                ((PrivateMessageReceivedEvent) x).getChannel().sendMessage("Slash commands updated!").queue();
            }
        }, true);

        registerCommand("add", x -> {
            if(x instanceof GuildMessageReceivedEvent) {
                GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) x;

                if(!event.getAuthor().isBot()) {

                    if(!checkPermission(event, Permission.ADMINISTRATOR))
                        return;

                    List<String> message = new LinkedList<>(Arrays.asList(event.getMessage().getContentRaw().split(" ")));
                    message.remove(0);

                    String module = String.join(" ", message);

                    try {
                        if(Modules.RegisterModule(event.getGuild().getId(), module)) {
                            SendMessage(event, String.format("Module %s added", module), "Success");
                        } else {
                            SendMessage(event, String.format("Module %s not found", module), "Error");
                        }
                    } catch (Exception e) {
                        SendMessage(event, "Couldn't register this module", "Error");
                    }
                }
            }
        });

        registerCommand("remove", x -> {
            if(x instanceof GuildMessageReceivedEvent) {
                GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) x;

                if(!event.getAuthor().isBot()) {

                    if(!checkPermission(event, Permission.ADMINISTRATOR))
                        return;

                    List<String> message = new LinkedList<>(Arrays.asList(event.getMessage().getContentRaw().split(" ")));
                    message.remove(0);

                    String module = String.join(" ", message);

                    try {
                        if(Modules.RemoveModule(event.getGuild().getId(), module)) {
                            SendMessage(event, String.format("Module %s removed", module), "Success");
                        }
                    } catch (Exception e) {
                        SendMessage(event, "Couldn't remove this module", "Error");
                    }
                }
            }
        });

        registerCommand("modules", x -> {
            if(x instanceof GuildMessageReceivedEvent) {
                GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) x;

                if(!event.getAuthor().isBot()) {

                    if(!checkPermission(event, Permission.ADMINISTRATOR))
                        return;

                    String message = parsePrefix(event);
                    if(message == null) return;

                    StringJoiner builder = new StringJoiner("\n");

                    String guildId = event.getGuild().getId();
                    List<BotModule> modules = Modules.loaded_modules.values().stream()
                            .filter(bm -> !bm.Blacklist.contains(guildId) &&
                                    (bm.Whitelist.size() == 0 || bm.Whitelist.contains(guildId)))
                            .collect(Collectors.toList());

                    int index = 0;
                    try {
                        index = Integer.parseInt(message.split(" ")[1]) - 1;
                    } catch (Exception ignored) {}

                    Tools.Page<BotModule> page = new Tools.Page<>(modules, 10);
                    List<BotModule> data = page.GetPageByIndex(index);
                    Collection<String> guildModules = Modules.GetGuildModules(guildId);

                    if(data == null) return;

                    for(BotModule module : data) {
                        String info;
                        if(module.Description != null && module.Description.length() > 0) {
                            info = String.format("**%s**: %s", module.Name, module.Description);
                        } else {
                            info = String.format("**%s**", module.Name);
                        }

                        if(guildModules.contains(module.JarName)) {
                            info += " **[Active]**";
                        }

                        builder.add(info);
                    }

                    String list = builder.toString();

                    if (list.length() < 2000) {
                        EmbedBuilder embed = new EmbedBuilder().setTitle("Module list")
                                .setDescription(list)
                                .setFooter(String.format("Page %s out of %s (Use [module <page>] to select page)", index + 1, page.GetMaxIndex()+1))
                                .setColor(event.getGuild().getSelfMember().getColor());

                        event.getChannel().sendMessageEmbeds(embed.build()).queue();
                    }
                }
            }
        });

        registerCommand("prefix", x -> {
            if(x instanceof GuildMessageReceivedEvent) {
                GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) x;

                if(!event.getAuthor().isBot()) {

                    if(!checkPermission(event, Permission.ADMINISTRATOR))
                        return;

                    List<String> message = new LinkedList<>(Arrays.asList(event.getMessage().getContentRaw().split(" ")));
                    message.remove(0);

                    String prefix = String.join(" ", message);

                    if(Modules.SetGuildPrefix(event.getGuild().getId(), prefix)) {
                        SendMessage(event, String.format("Prefix successfully updated\n" +
                                "New prefix: %s[command]", prefix), "Success");
                    } else {
                        SendMessage(event, "Couldn't set prefix", "Error");
                    }
                }
            }
        });

        registerCommand("register", x -> {
            if(x instanceof GuildMessageReceivedEvent) {
                GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) x;

                if(!event.getAuthor().isBot()) {

                    if(!checkPermission(event, Permission.ADMINISTRATOR))
                        return;

                    if(Modules.InitGuild(event)) {
                        SendMessage(event, "Guild has successfully registered", "Success");
                    } else {
                        SendMessage(event, "Guild is already registered", "Error");
                    }
                }
            }
        });

        registerCommand("help", x -> {
            if(x instanceof GuildMessageReceivedEvent) {
                GuildMessageReceivedEvent event = (GuildMessageReceivedEvent) x;

                StringJoiner joiner = new StringJoiner("\n");

                for(String command : Commands.keySet()) {
                    String info = HelpMessages.GetHelpMessage(command);

                    if(info != null) {
                        joiner.add(String.format("`%s`: %s", command, info));
                    }
                }

                SendMessage(event, joiner.toString(), "Available commands");
            }
        });
    }

    public static String parsePrefix(GuildMessageReceivedEvent event) {
        String prefix = Modules.GetGuildPrefix(event.getGuild().getId());

        if(prefix == null) prefix = Modules.DEFAULT_PREFIX;

        if(event.getMessage().getContentRaw().startsWith(prefix)) {
            String message = event.getMessage().getContentRaw();
            message = message.replaceFirst(String.format("\\Q%s\\E", prefix), "");

            return message;
        }

        return "";
    }

    private static Boolean checkPermission(GuildMessageReceivedEvent event, Permission... permissions) {
        Member member = event.getMember();
        return member != null && (member.hasPermission(permissions) || BotMain.GetDevIDs().contains(member.getId()));
    }
}

class Command {
    private final Executable Function;
    Boolean IsDev;

    Command(Executable func, Boolean isDev){
        Function = func;
        IsDev = isDev;
    }

    public void Execute(GenericEvent event) {
        this.Function.Execute(event);
    }
}

class HelpMessages {
    private static final LinkedHashMap<String, String> messages = new LinkedHashMap<>();
    static {
        messages.put("add","Adds module");
        messages.put("remove","Removes module module");
        messages.put("modules","Lists all available modules");
        messages.put("prefix","Sets prefix for your server");
        messages.put("register","Registers your server to allow adding modules");
    }

    static String GetHelpMessage(String name) {
        return messages.get(name);
    }
}

@FunctionalInterface
interface Executable {
    void Execute(GenericEvent event);
}
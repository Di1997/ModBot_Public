package core;

import classes.modules.BotModule;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.message.guild.GenericGuildMessageEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.GenericPrivateMessageEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.stream.Collectors;

public class BotMain extends ListenerAdapter {

    private static Collection<String> DevIDs;
    public static Collection<String> GetDevIDs() { return DevIDs; }
    public static net.dv8tion.jda.api.JDA JDA;

    private static String Bot_ID = BotTokens.OLD_BOT;

    //TODO: Add caching system to improve work speed with database

    public static void main(String[] args) {
        try {
            String path = new File(BotMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getPath();
            String module_path = Paths.get(path, "modules").toString();

            File module_dir = new File(module_path);

            for(File file : Objects.requireNonNull(module_dir.listFiles())) {
                if(!file.isDirectory()) {
                    String extension = "";

                    int i = file.getName().lastIndexOf('.');
                    if (i > 0) {
                        extension = file.getName().substring(i+1);
                    }

                    if(extension.equals("jar")) {
                        Modules.LoadModule(file);
                    }
                }
            }

            Runtime.getRuntime().addShutdownHook(new Thread(Modules::ReleaseModules));
        } catch (Exception e) {
            System.out.println("Couldn't load modules!");
        }

        try {
            JDABuilder builder = JDABuilder.createDefault(Bot_ID,
                    EnumSet.allOf(GatewayIntent.class));
            builder.setChunkingFilter(ChunkingFilter.ALL);
            builder.addEventListeners(new BotMain());
            builder.build();
        } catch (Exception e) {
            System.out.println("Couldn't log in!");
        }

        Commands.initCommands();
    }

    @Override
    public void onGenericGuildMessage(@Nonnull GenericGuildMessageEvent event) {
        String guild = event.getGuild().getId();
        try {
            if(event instanceof GuildMessageReceivedEvent) {
                GuildMessageReceivedEvent guildEvent = (GuildMessageReceivedEvent) event;

                String pureMessage = Commands.parsePrefix(guildEvent);

                if(!pureMessage.equals("")) {
                    Command command = Commands.getCommand(pureMessage);

                    if (command != null) {
                        command.Execute(guildEvent);
                    }
                }
            }

            Collection<String> modules = Modules.GetGuildModules(guild);

            if (modules != null && modules.size() > 0) {
                for (String module : modules) {
                    new Thread(() -> {
                        BotModule botModule = Modules.GetModule(module);
                        if (botModule != null) {
                            botModule.coreProcess(event);
                        }
                    }).start();
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onGuildLeave(@Nonnull GuildLeaveEvent event) {
        Collection<String> registeredModules = Modules.GetGuildModules(event.getGuild().getId());

        for(String module : registeredModules) {
            new Thread(() -> Modules.RemoveModule(event.getGuild().getId(), module)).start();
        }
    }

    @Override
    public void onGuildJoin(@Nonnull GuildJoinEvent event) {
        Modules.InitGuild(event);
    }

    @Override
    public void onGenericEvent(@NotNull GenericEvent event) {
        new Thread(() -> {
            if(!(event instanceof GenericGuildMessageEvent) && !(event instanceof GenericPrivateMessageEvent)) {
                Collection<String> modules;

                if(event instanceof GenericGuildEvent) modules = Modules.GetGuildModules(((GenericGuildEvent) event).getGuild().getId());
                else modules = Modules.loaded_modules.keySet();

                for (String module:modules) {
                    new Thread(() -> {
                        try {
                            BotModule botModule = Modules.GetModule(module);

                            if (botModule != null && botModule.AllowedEvents.contains(event.getClass()))
                                botModule.coreProcess(event);
                        } catch (Exception e) {
                            System.out.println(module);
                        }
                    }).start();
                }
            }
        }).start();
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        new Thread(() -> {
            JDA = event.getJDA();
            RestAction.setDefaultFailure(BotModule::staticPrintException);

            event.getJDA().retrieveApplicationInfo().queue(applicationInfo -> {
                if (applicationInfo.getTeam() != null)
                    DevIDs = applicationInfo.getTeam().getMembers().stream().map(teamMember -> teamMember.getUser().getId()).collect(Collectors.toList());
            });

            for (BotModule module : Modules.loaded_modules.values()) {
                try {
                    module.coreReadyModule(JDA);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    public void onGenericPrivateMessage(@Nonnull GenericPrivateMessageEvent event) {
        if(event instanceof PrivateMessageReceivedEvent) {
            PrivateMessageReceivedEvent innerEvent = (PrivateMessageReceivedEvent) event;

            String message = innerEvent.getMessage().getContentRaw();
            Command command = Commands.getCommand(message);

            if(command != null) {
                if (command.IsDev) {
                    event.getJDA().retrieveApplicationInfo().queue(applicationInfo -> {
                        if(applicationInfo.getTeam() != null) {
                            DevIDs = applicationInfo.getTeam().getMembers().stream().map(teamMember -> teamMember.getUser().getId()).collect(Collectors.toList());

                            if(applicationInfo.getTeam().isMember(innerEvent.getAuthor()))
                                command.Execute(event);
                        }
                    });
                }
                else {
                    command.Execute(event);
                }
            } else {
                Collection<String> modules = Modules.loaded_modules.keySet();

                for (String module:modules) {
                    new Thread(() -> {
                        BotModule botModule = Modules.GetModule(module);

                        if(botModule.AllowedEvents.contains(event.getClass()))
                            botModule.coreProcess(event);
                    }).start();
                }
            }
        }
    }
}

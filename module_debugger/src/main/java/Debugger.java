import COTDBot.COTDBot;
import LevelDBReader.LevelDBReader;
import classes.modules.BotModule;
import core.BotMain;
import core.BotTokens;
import core.Modules;
import datetime.Datetime;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static core.Constants.Module.DESC_LIMIT;
import static core.Constants.Module.NAME_LIMIT;

public class Debugger extends ListenerAdapter {
    static Path DBPath;
    static LinkedHashMap<String, BotModule> loaded_modules = new LinkedHashMap<>();

    //Add modules here
    static void InitModules() throws Exception {
        //addModule(new Datetime());
        addModule(new COTDBot());
    }

    static {
        try {
            BotModule.DEBUG = true;

            String mainFolder = "ModularBot";
            String path = new File(Debugger.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
            String mainPath = path.substring(0, path.indexOf(mainFolder) + mainFolder.length());
            DBPath = Paths.get(mainPath, "ModuleDatabases");
            Class<Modules> c = Modules.class;
            Field field = c.getDeclaredField("loaded_modules");
            field.setAccessible(true);

            if (Files.notExists(DBPath) && !DBPath.toFile().mkdir()) {
                try {
                    Files.createDirectory(DBPath);

                    InitModules();

                    addModule(new LevelDBReader());
                    field.set(null, loaded_modules);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Couldn't create main folder");
                }
            } else {
                InitModules();

                addModule(new LevelDBReader());
                field.set(null, loaded_modules);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            JDABuilder builder = JDABuilder.createDefault(BotTokens.DEBUG_BOT,
                    EnumSet.allOf(GatewayIntent.class));
            builder.setChunkingFilter(ChunkingFilter.ALL);
            builder.addEventListeners(new Debugger());
            builder.build();

        } catch (Exception e) {
            System.out.println("Couldn't log in!");
        }
    }

    @Override
    public void onGenericEvent(@NotNull GenericEvent event) {
        for(BotModule module : loaded_modules.values()) {
            new Thread(() -> module.coreProcess(event)).start();
        }
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        BotMain.JDA = event.getJDA();
        RestAction.setDefaultFailure(Throwable::printStackTrace);

        for (BotModule module: loaded_modules.values()) {
            try {
                module.coreReadyModule(event.getJDA());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        loadCommands(event);
        for(int i = 0; i < 5; i++) {
            try {
                Datetime module = new Datetime();
                module.coreReadyModule(event.getJDA());
                addModule(module);
            } catch (Exception ignored) {}
        }
    }

    private void loadCommands(ReadyEvent event) {
        Collection<Guild> guilds = event.getJDA().getGuilds();
        for(Guild guild : guilds) {
            for(BotModule module : loaded_modules.values()) {
                if(module.SlashCommands.size() > 0) {
                    CommandListUpdateAction update = guild.updateCommands();
                    for(BotModule.CommandInfo command : module.SlashCommands.values()) {
                        update = update.addCommands(command.getCommandData());
                    }

                    update.queue();
                }
            }
        }
    }

    private static void addModule(BotModule module) {
        try {
            if(loaded_modules.containsKey(module.getClass().getName())) {
                BotModule oldModule = loaded_modules.get(module.getClass().getName());
                oldModule.coreDeInit();
            }

            module.init(DBPath.toString(), module.getClass().getName());

            String errors = ValidateModule(module);
            if(errors != null) {
                System.out.printf(errors, module.getClass().getName());
                return;
            }

            loaded_modules.put(module.getClass().getName(), module);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String ValidateModule(BotModule module) {
        if(module.Name == null || module.Name.length() == 0) {
            return "Module %1$s must contain a name!";
        } else if (module.Name.length() > NAME_LIMIT) {
            return String.format("Module %%s has the name exceeding limit of %d symbols", NAME_LIMIT);
        } else if (module.Description.length() > DESC_LIMIT) {
            return String.format("Module %%s has the description exceeding limit of %d symbols", NAME_LIMIT);
        }

        return null;
    }
}

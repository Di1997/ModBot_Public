import classes.modules.BotModule;
import core.BotTokens;
import core.Constants;
import core.Modules;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

import static core.Constants.Module.DESC_LIMIT;
import static core.Constants.Module.NAME_LIMIT;

public class Debugger extends ListenerAdapter {
    static List<BotModule> modules = new ArrayList<>();
    static Path DBPath;
    static LinkedHashMap<String, BotModule> loaded_modules = new LinkedHashMap<>();

    static {
        try {
            String mainFolder = "ModularBot";
            String path = new File(Debugger.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
            String mainPath = path.substring(0, path.indexOf(mainFolder) + mainFolder.length());
            DBPath = Paths.get(mainPath, "ModuleDatabases");
            Class<Modules> c = Modules.class;
            Field field = c.getDeclaredField("loaded_modules");
            field.setAccessible(true);

            if (Files.notExists(DBPath) && !DBPath.toFile().mkdir()) {
                System.out.println("Couldn't create main folder");
            } else {
                //Add modules here
                addModule(new LevelDBReader());
                addModule(new Monitoring());

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
        for(BotModule module : modules) {
            new Thread(() -> module.CoreProcess(event)).start();
        }
    }

    private static void addModule(BotModule module) {
        try {
            module.init(DBPath.toString(), module.getClass().getName());

            String errors = ValidateModule(module);
            if(errors != null) {
                System.out.printf(errors, module.getClass().getName());
            }

            loaded_modules.put(module.getClass().getName(), module);
            modules.add(module);
        } catch (Exception e) {
            System.out.println(e.toString());
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

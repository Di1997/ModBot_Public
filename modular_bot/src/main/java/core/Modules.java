package core;

import classes.Database;
import classes.modules.BotModule;
import classes.Tools;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static core.Constants.Module.DESC_LIMIT;
import static core.Constants.Module.NAME_LIMIT;

public class Modules {
    static LinkedHashMap<String, BotModule> loaded_modules = new LinkedHashMap<>();
    static LinkedHashMap<String, String> name_module_map = new LinkedHashMap<>();
    public static final String DEFAULT_PREFIX = ".";
    private static String modules_folder;
    private static Database db;

    static {
        try {
            String main_path = new File(BotMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getPath();
            modules_folder = Paths.get(main_path, "modules").toString();

            db = new Database(Paths.get(main_path, Constants.Module.DATABASE_FOLDER, "core", "guilds").toFile());
        } catch (Exception e) {
            System.out.println("Unable to initiate modules");
            System.exit(0);
        }
    }

    static void LoadModule(File jar) {
        try(JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> e = jarFile.entries();

            URL[] urls = {jar.toURI().toURL()};
            URLClassLoader cl = URLClassLoader.newInstance(urls);

            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (!je.isDirectory() && je.getName().endsWith(".class")) {
                    try {
                        String className = je.getName().replaceFirst("\\.class$", "").replace('/', '.');
                        Class<?> c = cl.loadClass(className);

                        if (BotModule.class.isAssignableFrom(c)) {
                            @SuppressWarnings("unchecked")
                            Class<? extends BotModule> cls = (Class<? extends BotModule>) cl.loadClass(className);
                            BotModule module = cls.getDeclaredConstructor().newInstance();
                            String path = new File(BotMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getPath();
                            String moduleName = jar.getName().substring(0, jar.getName().lastIndexOf('.'));
                            module.init(path, moduleName);

                            String error = VerifyModule(module);

                            if(error == null) {
                                loaded_modules.put(moduleName, module);
                                name_module_map.put(module.Name, moduleName);
                                System.out.printf("Loaded module: %s%n", module.Name);
                            } else {
                                System.out.printf((error) + "%n", jarFile.getName(), module.Name);
                            }
                        }
                    } catch (Throwable ex) {
                        System.out.printf("Error while loading module %s%n", jarFile.getName());
                    }
                }
            }

            cl.close();
        } catch (Exception e) {
            System.out.printf("Error opening file: %s%n", jar.getName());
        }
    }

    static boolean LoadModuleByName(String name) {
        name = String.format("%s.jar", name.toLowerCase());
        File module = Paths.get(modules_folder, name).toFile();

        if(module.exists()) {
            LoadModule(module);
            return true;
        } else return false;
    }

    static boolean DisableModuleByName(String name) throws IOException {
        BotModule module = loaded_modules.getOrDefault(name_module_map.get(name), null);

        if(module == null) {
            name = name.toLowerCase();
            module = loaded_modules.getOrDefault(name, null);
        }

        if(module == null) return false;

        module.releaseDatabases();

        loaded_modules.remove(name);

        return true;
    }

    static boolean RemoveModuleByName(String name) {
        BotModule module = loaded_modules.getOrDefault(name_module_map.get(name), null);

        if(module == null) {
            name = name.toLowerCase();
            module = loaded_modules.getOrDefault(name, null);
        }

        if(module == null) return false;

        String finalName = name;
        db.executeDBAction(database -> {
            database.forEach(entry -> {
                byte[] bytes = entry.getValue();
                if(bytes != null) {
                    Guild guildInfo = Tools.convertToClass(new String(bytes), new TypeToken<>(){});

                    if(guildInfo != null) {
                        guildInfo.modules.remove(finalName);
                        database.put(entry.getKey(), Tools.convertToJson(guildInfo).getBytes());
                    }
                }
            });
            return null;
        });

        loaded_modules.remove(name);

        return true;
    }

    static boolean RegisterModule(String guildID, String name) {
        Guild guildInfo = Tools.convertToClass(db.get(guildID), new TypeToken<>(){});

        if (guildInfo == null) {
            guildInfo = new Guild();
        }

        String moduleName = name_module_map.get(name);

        if(moduleName == null) return false;

        if(loaded_modules.containsKey(moduleName) && !guildInfo.modules.contains(moduleName)) {
            BotModule module = loaded_modules.get(moduleName);

            if(!module.Blacklist.contains(guildID) && (module.Whitelist.size() == 0 || module.Whitelist.contains(guildID))) {
                guildInfo.modules.add(moduleName);
                db.set(guildID, Tools.convertToJson(guildInfo));
            } else return false;
        } else return false;

        return true;
    }

    static boolean RemoveModule(String guildID, String name) {
        Guild guildInfo = Tools.convertToClass(db.get(guildID), new TypeToken<>(){});

        if (guildInfo == null) {
            return false;
        }

        String moduleName = name_module_map.get(name);

        if(moduleName == null) return false;

        if(guildInfo.modules.contains(moduleName)) {
            guildInfo.modules.remove(moduleName);
            GetModule(moduleName).removeGuildData(guildID);

            if(guildInfo.modules.size() > 0)
                db.set(guildID, Tools.convertToJson(guildInfo));

            else
                db.delete(guildID);
        } else return false;

        return true;
    }

    static Collection<String> GetGuildModules(String guildID) {
        Guild guild = Tools.convertToClass(db.get(guildID), new TypeToken<>(){});
        if(guild != null) {
            return guild.modules;
        } else return new LinkedList<>();
    }

    static BotModule GetModule(String name) {
        return loaded_modules.getOrDefault(name, null);
    }

    static String GetGuildPrefix(String guildID) {
        Guild guildInfo = Tools.convertToClass(db.get(guildID), new TypeToken<>(){});
        if (guildInfo == null) {
            return null;
        }
        return guildInfo.prefix;
    }

    static boolean SetGuildPrefix(String guildID, String prefix) {
        Guild guildInfo = Tools.convertToClass(db.get(guildID), new TypeToken<>(){});

        if (guildInfo == null) {
            return false;
        }

        guildInfo.prefix = prefix;

        db.set(guildID, Tools.convertToJson(guildInfo));

        return true;
    }

    static void InitGuild(GuildJoinEvent event) {
        Guild guildInfo = Tools.convertToClass(db.get(event.getGuild().getId()), new TypeToken<>(){});

        if (guildInfo != null) {
            return;
        }

        guildInfo = new Guild();

        db.set(event.getGuild().getId(), Tools.convertToJson(guildInfo));
    }

    static boolean InitGuild(GuildMessageReceivedEvent event) {
        Guild guildInfo = Tools.convertToClass(db.get(event.getGuild().getId()), new TypeToken<>(){});

        if (guildInfo != null) {
            return false;
        }

        guildInfo = new Guild();

        db.set(event.getGuild().getId(), Tools.convertToJson(guildInfo));

        return true;
    }

    static String VerifyModule(BotModule module) {
        if(module.Name == null || module.Name.length() == 0) {
            return "Module %1$s must contain a name!";
        } else if (module.Name.length() > NAME_LIMIT) {
            return String.format("Module %%2$s (%%1$s) has the name exceeding limit of %d symbols", NAME_LIMIT);
        } else if (module.Description.length() > DESC_LIMIT) {
            return String.format("Module %%2$s (%%1$s) has the description exceeding limit of %d symbols", NAME_LIMIT);
        }

        return null;
    }

    static void ReleaseModules() {
        for (BotModule module:loaded_modules.values()) {
            try {
                module.releaseDatabases();
            } catch (Exception e) {
                System.out.printf("Couldn't release module %s%n", module.Name);
            }
        }
    }

    private static class Guild {
        public Collection<String> modules;
        public String prefix;

        public Guild() {
            modules = new LinkedList<>();
            prefix = DEFAULT_PREFIX;
        }
    }
}

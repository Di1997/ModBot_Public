package classes.modules;

import classes.Database;
import classes.Tools;
import core.Commands;
import core.Constants;
import interfaces.IBotModule;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@SuppressWarnings("SameParameterValue")
public abstract class BotModule implements IBotModule {
    public String Name;
    public String Description;
    public String JarName;
    public List<String> Whitelist = new LinkedList<>();
    public List<String> Blacklist = new LinkedList<>();
    public Set<Class<? extends GenericEvent>> AllowedEvents = new HashSet<>();
    public Boolean NeedsDB = true;

    private final List<String> DatabaseFiles = new ArrayList<>();
    private final HashMap<String, Database> Databases = new HashMap<>();


    public void init(String mainLocation, String moduleName) {
        if(NeedsDB) {
            DatabaseFiles.add("default");

            Path moduleFolder = Paths.get(mainLocation, Constants.Module.DATABASE_FOLDER, moduleName);
            if (Files.notExists(moduleFolder)) {
                try {
                    Files.createDirectories(moduleFolder);
                } catch (Exception e) {
                    System.out.printf("Module %s: Couldn't create DB folder%n", moduleName);
                    e.printStackTrace();
                    return;
                }
            }

            for (String file : DatabaseFiles) {
                Path databasePath = Paths.get(moduleFolder.toString(), file);
                try {
                    Databases.put(file, new Database(new File(databasePath.toUri())));
                } catch (Exception e) {
                    System.out.println("Exception while initializing db");
                    e.printStackTrace();
                }
            }
        }

        JarName = moduleName;
        if(Name == null) Name = moduleName;
        if(Description == null) Description = "";
    }

    protected Database getDatabase() {
        return getDatabase("default");
    }

    protected Database getDatabase(String file) {
        return Databases.get(file);
    }

    protected void registerDatabase(String databaseName) {
        if(!DatabaseFiles.contains(databaseName)) {
            DatabaseFiles.add(databaseName);
        }
    }

    public void releaseDatabases() throws IOException {
        for (Database db: Databases.values()) {
            db.release();
        }
    }

    protected void whitelistServer(String serverId) {
        Whitelist.add(serverId);
    }

    protected void blacklistServer(String serverId) {
        Blacklist.add(serverId);
    }

    protected void addAllowedEvent(Class<? extends GenericEvent> event) {
        AllowedEvents.add(event);
    }

    protected void SendModuleMessage(GuildMessageReceivedEvent event, String message, String title) {
        Tools.SendMessage(event, message, title);
    }

    protected void SendModuleMessage(GuildMessageReceivedEvent event, String message) {
        SendModuleMessage(event, message, Name);
    }

    public void removeGuildData(String guildID) {
        for(Database DB : Databases.values()) {
            DB.delete(guildID);
        }
    }

    public void CoreProcess(GenericEvent event) {
        String command;
        if(event instanceof GuildMessageReceivedEvent) {
            command = Commands.ParsePrefix((GuildMessageReceivedEvent) event);
        } else {
            command = "";
        }

        try {
            this.Process(event, command);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected abstract void Process(GenericEvent event, String command) throws Exception;
}

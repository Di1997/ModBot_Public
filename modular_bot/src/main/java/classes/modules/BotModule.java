package classes.modules;

import classes.Database;
import classes.Database.InvalidDatabaseException;
import classes.Tools;
import core.BotMain;
import core.Commands;
import core.Constants;
import interfaces.IBotModule;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

@SuppressWarnings("SameParameterValue")
public abstract class BotModule implements IBotModule {
    public static Boolean DEBUG = false;

    public String Name;
    public String Description;
    public String JarName;
    public List<String> Whitelist = new LinkedList<>();
    public List<String> Blacklist = new LinkedList<>();
    public Set<Class<? extends GenericEvent>> AllowedEvents = new HashSet<>();
    public Boolean NeedsDB = true;
    public Map<String, CommandInfo> SlashCommands = new HashMap<>();
    public Map<String, ThrowingConsumer<ButtonClickEvent>> Buttons = new HashMap<>();
    public JDA JDA;

    private final List<String> DatabaseFiles = new ArrayList<>();
    private final HashMap<String, Database> Databases = new HashMap<>();

    public void init(String mainLocation, String moduleName) throws IOException {
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
                    throw e;
                }
            }
        }

        JarName = moduleName;
        if(Name == null) Name = moduleName;
        if(Description == null) Description = "";
    }

    protected Database getDatabase() {
        try {
            return getDatabase("default");
        } catch (InvalidDatabaseException e) {
            return null;
        }
    }

    protected Database getDatabase(String file) throws InvalidDatabaseException {
        if(Databases.get(file) == null) throw new InvalidDatabaseException();
        return Databases.get(file);
    }

    protected void registerDatabase(String databaseName) {
        if(!DatabaseFiles.contains(databaseName)) {
            DatabaseFiles.add(databaseName);
        }
    }

    public void coreDeInit() throws Exception {
        deInit();
        releaseDatabases();
    }

    public void releaseDatabases() throws IOException {
        for (Database db: Databases.values()) {
            db.release();
        }
    }

    protected void deInit(){}

    protected void whitelistServer(String serverId) {
        Whitelist.add(serverId);
    }

    protected void blacklistServer(String serverId) {
        Blacklist.add(serverId);
    }

    protected void addAllowedEvent(Class<? extends GenericEvent> event) {
        AllowedEvents.add(event);
    }

    protected void registerCommand(String name, String description, ThrowingConsumer<SlashCommandEvent> process, OptionData... options) {
        CommandData commandData = new CommandData(name, description);
        commandData.addOptions(options);

        SlashCommands.put(name, new CommandInfo(commandData, process));
    }

    protected void registerCommand(CommandData data, ThrowingConsumer<SlashCommandEvent> process) {
        SlashCommands.put(data.getName(), new CommandInfo(data, process));
    }

    protected void registerButton(String name, ThrowingConsumer<ButtonClickEvent> process) {
        Buttons.put(name, process);
    }

    protected void processSlashCommand(SlashCommandEvent event) {
        try {
            String id = event.getName();
            if (SlashCommands.containsKey(id))
                SlashCommands.get(id).getSlashCommandProcessor().accept(event);
        } catch (Exception e) {
            printExceptionInformation(e);
        }
    }

    protected void processButton(ButtonClickEvent event) {
        try {
            String id = event.getComponentId();
            if(Buttons.containsKey(id))
                Buttons.get(id).accept(event);
        } catch (Exception e) {
            printExceptionInformation(e);
        }
    }

    protected void sendModuleMessage(GuildMessageReceivedEvent event, String message, String title) {
        Tools.SendMessage(event, message, title);
    }

    protected void sendModuleMessage(GuildMessageReceivedEvent event, String message) {
        sendModuleMessage(event, message, Name);
    }

    public void removeGuildData(String guildID) {
        for(Database DB : Databases.values()) {
            DB.delete(guildID);
        }
    }

    public void coreProcess(GenericEvent event) {
        String command;
        if(event instanceof GuildMessageReceivedEvent) {
            command = Commands.parsePrefix((GuildMessageReceivedEvent) event);
        } else {
            command = "";
        }

        try {
            this.process(event, command);
        } catch (Exception e) {
            e.printStackTrace();
            printExceptionInformation(e);
        }
    }

    protected void printExceptionInformation(Exception e) {
        if(JDA == null) return;

        TextChannel channel = JDA.getTextChannelById(Constants.Module.REPORT_CHANNEL);

        if(channel != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            String exceptionStack = sw.toString();
            if(exceptionStack.length() > 2000)
                exceptionStack = exceptionStack.substring(0, 1990) + "...";

            String finalExceptionStack = exceptionStack;
            channel.sendMessage(String.format("Module %s received exception: %s", Name, e.getMessage())).queue(message ->
                    channel.sendMessage(finalExceptionStack).queue());
        }
    }

    public static void staticPrintException(Throwable e) {
        if(BotMain.JDA == null) return;

        TextChannel channel = BotMain.JDA.getTextChannelById(Constants.Module.REPORT_CHANNEL);

        if(channel != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);

            String exceptionStack = sw.toString();
            if(exceptionStack.length() > 2000)
                exceptionStack = exceptionStack.substring(0, 1990) + "...";

            String finalExceptionStack = exceptionStack;
            channel.sendMessage(String.format("Static Exception:\n%s", e.getMessage())).queue(message ->
                    channel.sendMessage(finalExceptionStack).queue());
        }
    }

    protected abstract void process(GenericEvent event, String command) throws Exception;

    public void coreReadyModule(JDA jda) throws Exception{
        if(jda != null)
            JDA = jda;
        else if(BotMain.JDA != null)
            JDA = BotMain.JDA;

        readyModule(JDA);
    }

    public void readyModule(JDA jda) throws Exception {}

    public static class CommandInfo {
        CommandData data;
        ThrowingConsumer<SlashCommandEvent> eventConsumer;

        public CommandInfo(CommandData data, ThrowingConsumer<SlashCommandEvent> eventConsumer) {
            this.data = data;
            this.eventConsumer = eventConsumer;
        }

        public CommandData getCommandData() {
            return data;
        }

        public ThrowingConsumer<SlashCommandEvent> getSlashCommandProcessor() {
            return eventConsumer;
        }
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        default void accept(final T elem) throws Exception {
            acceptThrows(elem);
        }
        void acceptThrows(T elem) throws Exception;
    }
}

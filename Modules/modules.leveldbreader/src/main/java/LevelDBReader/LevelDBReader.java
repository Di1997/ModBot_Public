package LevelDBReader;

import classes.Database;
import classes.Tools;
import classes.Tools.*;
import classes.modules.BotModule;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import core.Constants;
import core.Modules;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.iq80.leveldb.DBIterator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class LevelDBReader extends BotModule {

    Path DatabasePath;

    public LevelDBReader() {
        Name = "LevelDBReader";
        Description = "Only for developers";
        NeedsDB = false;

        whitelistServer("689089984686456832");
    }

    @Override
    public void init(String mainLocation, String moduleName) throws IOException {
        DatabasePath = Paths.get(mainLocation, Constants.Module.DATABASE_FOLDER);
        super.init(mainLocation, moduleName);
    }

    @Override
    protected void process(GenericEvent event, String command) throws Exception {
        if(event instanceof GuildMessageReceivedEvent) InnerProcess((GuildMessageReceivedEvent) event, command);
    }

    private void InnerProcess(GuildMessageReceivedEvent event, String command) throws Exception {
        Queue<String> args = new LinkedList<>(Arrays.asList(command.split(" ")));
        if(!event.getAuthor().isBot() && args.size() > 1) {
            String arg =  args.remove();
            if(arg.equalsIgnoreCase("leveldb")) {
                switch (Objects.requireNonNull(args.peek())) {
                    case "view" -> {
                        args.remove();
                        ViewData(event, args);
                    }
                    case "commit" -> {
                        if (EditingContext.Type.equals(EditingContext.OperationType.UPDATE) && EditingContext.Stage == 2)
                            EditingContext.UpdateData();
                        else if (EditingContext.Type.equals(EditingContext.OperationType.REMOVE) && EditingContext.Stage == 1)
                            EditingContext.RemoveData();

                        Tools.SendMessage(event, "Changes commited", "LevelDB viewer");
                    }
                    default -> {
                        if (EditingContext.Type.equals(EditingContext.OperationType.UPDATE) && EditingContext.Stage == 1) {
                            EditingContext.Update = String.join(" ", args);
                            EditingContext.Stage++;
                            Tools.SendMessage(event, "Type `.leveldb commit` to commit changes or `.leveldbcancel` to cancel", "LevelDB viewer");
                        }
                    }
                }
            } else if(arg.equalsIgnoreCase("leveldbcancel")) {
                EditingContext.Reset();
                Tools.SendMessage(event, "EditingContext was reset", "LevelDB viewer");
            }

            if(EditingContext.Type != EditingContext.OperationType.UPDATE && EditingContext.Type != EditingContext.OperationType.REMOVE)
                EditingContext.Reset();
        }
    }

    private void ViewData(GuildMessageReceivedEvent event, Queue<String> args) throws Exception {
        if(args.size() == 0 || (args.size() == 1 && isInt(args.peek()))) {
            List<String> directories = Arrays.asList(DatabasePath.toFile().list((file, name) -> file.isDirectory()));
            Page<String> directoryInfo = new Page<>(directories, 5);
            int index = 0;

            if(args.size() == 1)
                index = Integer.parseInt(args.peek()) - 1;

            directoryInfo.SetIndex(index);
            EmbedBuilder builder = new EmbedBuilder().setTitle("LevelDB viewer");
            Tools.SendMessage(event, directoryInfo, builder);

        } else {
            if(args.peek() == null) return;
            Queue<String> data = new LinkedList<>(Arrays.asList(Objects.requireNonNull(args.remove()).replace('/','\\').split("\\\\")));

            String module = data.remove();
            if(module != null) {
                Path ModulePath = Paths.get(DatabasePath.toString(), module);

                if(data.peek() != null) {
                    Path DBPath = Paths.get(ModulePath.toString(), data.remove());
                    ArrayList<Pair<String, String>> pairs = new ArrayList<>();
                    File DBfile = DBPath.toFile();
                    EditingContext.File = DBfile.toPath();

                    Queue<Exception> exception = new LinkedList<>();
                    DatabaseAccess.Execute(DBfile, db ->
                        db.executeDBAction(database -> {
                            try (DBIterator iterator = database.iterator()) {
                                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                                    String firstItem = new String(iterator.peekNext().getKey());
                                    String secondItem = new String(iterator.peekNext().getValue());
                                    pairs.add(new Pair<>(firstItem, secondItem));
                                }
                                iterator.close();
                                return null;
                            } catch (Exception e) {
                                exception.add(e);
                                return null;
                            }
                        })
                    );

                    if (exception.size() > 0) {
                        exception.remove().printStackTrace();
                        event.getChannel().sendMessage("Couldn't load module's database. Perhaps database is in use?").queue();
                        return;
                    }

                    ArrayList<Pair<String, String>> values = RecursiveView(data, pairs);

                    if (values == null) {
                        event.getChannel().sendMessage("Wrong format. Please, try again").queue();
                        return;
                    }

                    int index = 0;

                    if (args.size() == 1) {
                        if (args.peek().equalsIgnoreCase("edit") && EditingContext.Stage == 0) {
                            EditingContext.Type = EditingContext.OperationType.UPDATE;
                            EditingContext.Stage++;
                            Tools.SendMessage(event, "Type `.leveldb [new value]` to update this field or `.leveldbcancel` to cancel", "LevelDB viewer");
                            return;
                        } else if (args.peek().equalsIgnoreCase("delete") && EditingContext.Stage == 0) {
                            EditingContext.Type = EditingContext.OperationType.REMOVE;
                            EditingContext.Stage++;
                            Tools.SendMessage(event, "Type `.leveldb commit` to remove this field or `.leveldbcancel` to cancel", "LevelDB viewer");
                            return;
                        } else if (isInt(args.peek())) {
                            index = Integer.parseInt(args.remove()) - 1;
                        }
                    }

                    Page<Pair<String, String>> keyInfos = new Page<>(values, 5);

                    List<Pair<String, String>> page = keyInfos.GetPageByIndex(index);
                    keyInfos.SetIndex(index);
                    EmbedBuilder builder = Tools.GetEmbedTemplate(event, keyInfos).setTitle("LevelDB viewer");

                    if (page.size() > 0) {
                        page.forEach(pair -> {
                            String text = pair.getSecond();
                            if (text.length() > 1020)
                                text = text.substring(0, 1020) + "...";

                            builder.addField(pair.getFirst(), text, false);
                        });
                        event.getChannel().sendMessageEmbeds(builder.build()).queue();
                    }
                } else {
                    List<String> directories = Arrays.asList(ModulePath.toFile().list((file, name) -> file.isDirectory()));
                    Page<String> directoryInfo = new Page<>(directories, 5);
                    int index = 0;

                    if(args.size() == 1)
                        index = Integer.parseInt(args.peek()) - 1;

                    List<String> page = directoryInfo.GetPageByIndex(index);
                    EmbedBuilder builder = new EmbedBuilder().setTitle("LevelDB viewer");

                    if(page.size() > 0) {
                        builder.setDescription(String.join(System.lineSeparator(), page));
                        event.getChannel().sendMessageEmbeds(builder.build()).queue();
                    }
                }
            }
        }
    }

    private ArrayList<Pair<String, String>> RecursiveView(Queue<String> data, ArrayList<Pair<String, String>> pairs) {
        if(data.size() == 0) return pairs;
        String value = data.remove();
        Pair<String, String> pair = pairs.stream().filter(p -> p.getFirst().equals(value)).findFirst().orElse(null);
        if(pair != null) {
            EditingContext.Remember(pair.getFirst());
            Object object = new Gson().fromJson(pair.getSecond(), new TypeToken<Object>(){}.getType());
            return RecursiveView(data, object);
        }
        else return pairs;
    }

    private ArrayList<Pair<String, String>> RecursiveView(Queue<String> data, Object object) {
        try {
            if (object instanceof LinkedTreeMap) {
                LinkedHashMap<String, Object> map = ConvertToValidList((LinkedTreeMap<?, ?>) object);
                if (map.size() == 0) return null;
                if (data.size() == 0) {
                    ArrayList<Pair<String, String>> pairs = new ArrayList<>();
                    map.forEach((k, v) -> pairs.add(new Pair<>(k, new Gson().toJson(v, new TypeToken<Object>() {
                    }.getType()))));
                    return pairs;
                }
                String key = data.remove();
                EditingContext.Remember(key);
                return RecursiveView(data, map.get(key));
            } else if (object instanceof ArrayList) {
                ArrayList<Object> array = ConvertToValidList((ArrayList<?>) object);
                if (data.size() == 0) {
                    ArrayList<Pair<String, String>> pairs = new ArrayList<>();
                    for (int i = 0; i < array.size(); i++) {
                        String newJson = new Gson().toJson(array.get(i), new TypeToken<Object>() {
                        }.getType());
                        pairs.add(new Pair<>(Integer.toString(i), newJson));
                    }
                    return pairs;
                } else {
                    String arg = data.remove();
                    if (!isInt(arg)) return null;
                    EditingContext.Remember(arg);
                    return RecursiveView(data, array.get(Integer.parseInt(arg)));
                }
            } else {
                ArrayList<Pair<String, String>> pairs = new ArrayList<>();
                pairs.add(new Pair<>(object.getClass().getSimpleName(), object.toString()));
                return pairs;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Boolean isInt(String s) {
        try {
            int i = Integer.parseInt(s); return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static LinkedHashMap<String, Object> ConvertToValidList(LinkedTreeMap<?, ?> list) {
        LinkedHashMap<String, Object> validMap = new LinkedHashMap<>();
        list.forEach((k, v) -> {
            if(k instanceof String)
                validMap.put((String) k, v);
        });

        return validMap;
    }

    private static ArrayList<Object> ConvertToValidList(ArrayList<?> arrayList) {
        return new ArrayList<>(arrayList);
    }

    private static class EditingContext {
        enum OperationType {
            REMOVE,
            UPDATE
        }

        public static Path File;
        public static OperationType Type;
        public static int Stage = 0;
        public static String Update = "";

        static Queue<String> History = new LinkedList<>();

        public static void Reset() {
            History = new LinkedList<>();
            Path File = null;
            OperationType Type = null;
            Stage = 0;
            Update = "";
        }

        public static void Remember(String key) {
            History.add(key);
        }

        @SuppressWarnings("unchecked")
        private static void UpdateData() throws Exception {
            AtomicReference<String> data = new AtomicReference<>();
            AtomicReference<String> dbKey = new AtomicReference<>();

            DatabaseAccess.Execute(File.toFile(), db -> {
                dbKey.set(History.remove());
                data.set(db.get(dbKey.get()));
            });

            LinkedTreeMap<String, Object> originalMap = null;
            ArrayList<Object> originalList = null;

            Object object = new Gson().fromJson(data.get(), new TypeToken<Object>(){}.getType());
            boolean isMapOrList = true;

            if(object instanceof LinkedTreeMap) {
                originalMap = (LinkedTreeMap<String, Object>) object;
            } else if (object instanceof ArrayList) {
                originalList = (ArrayList<Object>) object;
            } else isMapOrList = false;


            if(isMapOrList) {
                Object currentElement;

                if (originalMap != null)
                    currentElement = originalMap;
                else
                    currentElement = originalList.stream().findFirst().orElse(null);

                if (currentElement == null) throw new Exception("JSONMap is empty");
                for (int i = History.size(); i > 1; i--) {
                    String key = History.remove();
                    if (currentElement instanceof LinkedTreeMap) {
                        LinkedHashMap<String, Object> listData = ConvertToValidList((LinkedTreeMap<?, ?>) currentElement);
                        currentElement = listData.get(key);
                    } else if (currentElement instanceof ArrayList) {
                        ArrayList<Object> arrayData = ConvertToValidList((ArrayList<?>) currentElement);
                        currentElement = arrayData.get(Integer.parseInt(key));
                    }
                }

                Object correctElement;
                if (currentElement instanceof LinkedTreeMap) {
                    LinkedTreeMap<String, Object> map = (LinkedTreeMap<String, Object>) currentElement;
                    String key = History.remove();
                    correctElement = ConvertToValue(Update, map.get(key).getClass());
                    map.put(key, correctElement);
                } else if (currentElement instanceof ArrayList) {
                    ArrayList<Object> arrayData = (ArrayList<Object>) currentElement;
                    int key = Integer.parseInt(History.remove());
                    correctElement = ConvertToValue(Update, arrayData.get(key).getClass());
                    arrayData.set(key, correctElement);
                }
            } else {
                object = ConvertToValue(Update, object.getClass());
            }

            Object updatedValue = object;

            DatabaseAccess.Execute(File.toFile(), db ->
                db.set(dbKey.get(), new Gson().toJson(updatedValue, new TypeToken<Object>(){}.getType()))
            );

            Reset();
        }

        @SuppressWarnings("unchecked")
        private static void RemoveData() throws Exception {
            AtomicReference<String> data = new AtomicReference<>();
            AtomicReference<String> dbKey = new AtomicReference<>();

            if(History.size() == 1) {
                DatabaseAccess.Execute(File.toFile(), db -> {
                    dbKey.set(History.remove());
                    data.set(db.get(dbKey.get()));
                });
                return;
            }

            DatabaseAccess.Execute(File.toFile(), db -> {
                dbKey.set(History.remove());
                data.set(db.get(dbKey.get()));
            });

            LinkedTreeMap<String, Object> originalMap = null;
            ArrayList<Object> originalList = null;
            boolean isMapOrList = true;

            Object object = new Gson().fromJson(data.get(), new TypeToken<Object>(){}.getType());
            if(object instanceof LinkedTreeMap) {
                originalMap = (LinkedTreeMap<String, Object>) object;
            } else if (object instanceof ArrayList) {
                originalList = (ArrayList<Object>) object;
            } else isMapOrList = false;

            if(isMapOrList) {
                Object currentElement;
                if (originalMap != null)
                    currentElement = originalMap;
                else
                    currentElement = originalList.stream().findFirst().orElse(null);

                if (currentElement == null) throw new Exception("JSONMap is empty");
                for (int i = History.size(); i > 1; i--) {
                    String key = History.remove();
                    if (currentElement instanceof LinkedTreeMap) {
                        LinkedHashMap<String, Object> listData = ConvertToValidList((LinkedTreeMap<?, ?>) currentElement);
                        currentElement = listData.get(key);
                    } else if (currentElement instanceof ArrayList) {
                        ArrayList<Object> arrayData = ConvertToValidList((ArrayList<?>) currentElement);
                        currentElement = arrayData.get(Integer.parseInt(key));
                    }
                }

                if (currentElement instanceof LinkedTreeMap) {
                    @SuppressWarnings("unchecked")
                    LinkedTreeMap<String, Object> map = (LinkedTreeMap<String, Object>) currentElement;
                    String key = History.remove();
                    map.remove(key);
                } else if (currentElement instanceof ArrayList) {
                    @SuppressWarnings("unchecked")
                    ArrayList<Object> arrayData = (ArrayList<Object>) currentElement;
                    int key = Integer.parseInt(History.remove());
                    arrayData.remove(key);
                }

                DatabaseAccess.Execute(File.toFile(), db ->
                        db.set(dbKey.get(), new Gson().toJson(object, new TypeToken<Object>(){}.getType()))
                );
            } else {
                DatabaseAccess.Execute(File.toFile(), db ->
                        db.delete(dbKey.get())
                );
            }

            Reset();
        }

        private static Object ConvertToValue(String data, Class<?> type) throws Exception {
            try {
                if (type.equals(Boolean.class)) {
                    return Boolean.valueOf(data);
                } else if (type.equals(Integer.class)) {
                    return Integer.parseInt(data);
                } else if (type.equals(String.class)) {
                    return data;
                } else return null;
            } catch (Exception e) {
                throw new Exception("Invalid data/type conversion operation");
            }
        }
    }

    private static class DatabaseAccess {

        static private Boolean destroy = false;

        static void Execute(File DatabasePath, DatabaseFunction function) throws Exception {
            Database db = GetDatabase(DatabasePath);
            if(db == null) throw new Exception("Coudln't load database");
            function.execute(db);
            if(destroy) {db.release(); destroy = false;}
        }

        @SuppressWarnings("unchecked")
        static private Database GetDatabase(File DatabasePath) throws Exception {
            Stack<String> path = new Stack<>();
            path.addAll(GetPathStrings(DatabasePath));

            String dbName = path.pop();
            String moduleName = path.pop();
            boolean fieldAccess;
            boolean methodAccess;

            Class<Modules> c = Modules.class;
            Field field = c.getDeclaredField("loaded_modules");
            fieldAccess = field.canAccess(null);
            field.setAccessible(true);

            LinkedHashMap<String, BotModule> modules = (LinkedHashMap<String, BotModule>) field.get(null);

            BotModule module;
            Database database = null;

            if(modules.size() > 0 && (module = modules.get(moduleName)) != null) {
                Method dbMethod = BotModule.class.getDeclaredMethod("getDatabase", String.class);

                methodAccess = dbMethod.canAccess(module);
                dbMethod.setAccessible(true);
                database = (Database) dbMethod.invoke(module, dbName);
                dbMethod.setAccessible(methodAccess);
                field.setAccessible(fieldAccess);
            } else if (moduleName.equals("core")) {
                Field coreDB = c.getDeclaredField("db");
                boolean coreAccess = field.canAccess(null);

                coreDB.setAccessible(true);
                database = (Database) coreDB.get(null);
                coreDB.setAccessible(coreAccess);
            }

            if(database != null) return database;

            destroy = true;
            field.setAccessible(fieldAccess);
            return new Database(DatabasePath, false);
        }

        static ArrayList<String> GetPathStrings(File file) {
            ArrayList<String> list = new ArrayList<>();

            File current = file;
            do {
                list.add(current.getName());
            } while ((current = current.getParentFile()) != null);

            Collections.reverse(list);
            return list;
        }

        protected interface DatabaseFunction {
            void execute(Database db);
        }
    }
}

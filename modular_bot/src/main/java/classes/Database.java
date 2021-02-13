package classes;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.IOException;

import static org.fusesource.leveldbjni.JniDBFactory.factory;

public class Database implements AutoCloseable {

    private final DB databaseFile;

    public Database(File path, Boolean createIfMissing) throws IOException {

        Options options = new Options();
        options.createIfMissing(createIfMissing);

        databaseFile = factory.open(path, options);
    }

    public Database(File path) throws IOException {
        this(path, true);
    }

    @FunctionalInterface
    public interface DBInteraction {
        byte[] action(DB database);
    }

    public String get(String key) {
        byte[] byteKey = key.getBytes();
        return executeDBAction(database -> database.get(byteKey));
    }

    public synchronized void set(String key, String value) {
        byte[] byteKey = key.getBytes();
        byte[] byteValue = value.getBytes();

        executeDBAction(database -> {
            database.put(byteKey, byteValue);
            return null;
        });
    }

    public synchronized String delete(String key) {
        byte[] byteKey = key.getBytes();
        return executeDBAction(database -> {
            database.delete(byteKey);
            return null;
        });
    }

    public synchronized String executeDBAction(DBInteraction func) {

        byte[] data;

        data = func.action(databaseFile);

        if(data == null) {
            return null;
        }

        return new String(data);
    }

    public void release() throws IOException {
        databaseFile.close();
    }

    @Override
    public void close() throws Exception {
        databaseFile.close();
    }
}

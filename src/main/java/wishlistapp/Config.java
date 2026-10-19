package wishlistapp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Внешние настройки; пароли не включаются в сообщения и toString(). */
public final class Config {
    public final String type, url, user, password, schema;
    private Config(Properties p) {
        type = p.getProperty("db.type", "").trim().toLowerCase(java.util.Locale.ROOT);
        url = p.getProperty("db.url", "").trim();
        user = p.getProperty("db.user", "").trim();
        password = p.getProperty("db.password", "");
        schema = p.getProperty("db.schema", "wishlistApp").trim();
        if (!type.equals("postgresql") && !type.equals("sqlserver"))
            throw new IllegalArgumentException("db.type: укажите postgresql или sqlserver");
        String prefix = type.equals("postgresql") ? "jdbc:postgresql:" : "jdbc:sqlserver:";
        if (!url.startsWith(prefix)) throw new IllegalArgumentException("db.url не соответствует db.type");
        if (user.isEmpty()) throw new IllegalArgumentException("Укажите db.user в файле настроек");
        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")) throw new IllegalArgumentException("Некорректное имя схемы");
    }
    public static Config load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Не найден " + path + ". Скопируйте пример в config/db.properties и заполните подключение.");
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(r); }
        return new Config(p);
    }
}

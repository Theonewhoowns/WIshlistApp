package wishlistapp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

// Чтение и проверка внешних настроек подключения к СУБД.
public final class Config {

    // Параметры подключения, значения которых задаются при создании объекта.
    public final String type, url, user, password, schema;

    private Config(Properties p) {
        // Получаем настройки. Для схемы по умолчанию используется wishlistApp.
        // У пароля сохраняем крайние пробелы: они могут быть его частью.
        type = p.getProperty("db.type", "").trim().toLowerCase(java.util.Locale.ROOT);
        url = p.getProperty("db.url", "").trim();
        user = p.getProperty("db.user", "").trim();
        password = p.getProperty("db.password", "");
        schema = p.getProperty("db.schema", "wishlistApp").trim();

        // Проверяем поддерживаемый тип СУБД и соответствие JDBC-адреса этому типу.
        if (!type.equals("postgresql") && !type.equals("sqlserver"))
            throw new IllegalArgumentException(
                    "db.type: укажите postgresql или sqlserver");

        String prefix = type.equals("postgresql")
                ? "jdbc:postgresql:" : "jdbc:sqlserver:";

        if (!url.startsWith(prefix))
            throw new IllegalArgumentException("db.url не соответствует db.type");

        // Проверяем наличие логина и допустимый формат имени схемы.
        if (user.isEmpty())
            throw new IllegalArgumentException("Укажите db.user в файле настроек");

        if (!schema.matches("[A-Za-z_][A-Za-z0-9_]{0,62}"))
            throw new IllegalArgumentException("Некорректное имя схемы");
    }

    // Загружает файл настроек и возвращает проверенный объект Config.
    public static Config load(Path path) throws IOException {
        // Проверяем существование файла по выбранному пути.
        if (!Files.isRegularFile(path))
            throw new IOException(
                    "Не найден " + path
                            + ". Скопируйте пример в config/db.properties"
                            + " и заполните подключение.");

        // Читаем свойства в UTF-8. Reader закрывается автоматически.
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(r);
        }

        // Конструктор извлекает значения и выполняет проверки.
        return new Config(p);
    }
}
package wishlistapp;

import java.sql.*;
import java.util.*;

/**
 * Работа с JDBC: загрузка драйвера, открытие соединений и проверка схемы.
 * Соединения и запросы закрывает вызывающий код через try-with-resources.
 */
public final class Database {

    // Настройки подключения и реализация SQL-синтаксиса выбранной СУБД.
    public final Config config;
    public final SqlDialect dialect;

    public Database(Config config) throws ClassNotFoundException {
        this.config = config;
        dialect = SqlDialect.create(config);

        // Загружаем JDBC-драйвер PostgreSQL или SQL Server.
        Class.forName(config.type.equals("postgresql")
                ? "org.postgresql.Driver"
                : "com.microsoft.sqlserver.jdbc.SQLServerDriver");

        // Общий тайм-аут ожидания подключения — 8 секунд.
        DriverManager.setLoginTimeout(8);
    }

    /** Открывает новое JDBC-соединение с параметрами из файла настроек. */
    public Connection open() throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", config.user);
        p.setProperty("password", config.password);

        // Ограничиваем время подключения и ожидания сетевого чтения.
        // PostgreSQL задаёт socketTimeout в секундах, SQL Server — в миллисекундах.
        if (config.type.equals("postgresql")) {
            p.setProperty("connectTimeout", "8");
            p.setProperty("socketTimeout", "30");
        } else {
            p.setProperty("loginTimeout", "8");
            p.setProperty("socketTimeout", "30000");
        }

        // Непосредственное подключение к СУБД через JDBC.
        return DriverManager.getConnection(config.url, p);
    }

    // Возвращает имя таблицы со схемой и кавычками нужного SQL-диалекта.
    public String t(String name) {
        return dialect.table(name);
    }

    /** Создаёт параметризованный запрос с тайм-аутом выполнения 20 секунд. */
    public PreparedStatement prepare(Connection c, String sql)
            throws SQLException {
        PreparedStatement p = c.prepareStatement(sql);
        p.setQueryTimeout(20);
        return p;
    }

    /** Проверяет доступность таблиц и наличие исправленного ключа получателя. */
    public void verify() throws SQLException {
        try (Connection c = open()) {

            // Проверяем доступность восьми таблиц.
            // WHERE 1=0 позволяет проверить запрос без получения строк данных.
            for (String name : Arrays.asList(
                    "app_user",
                    "recipient",
                    "wishlist",
                    "wish_item",
                    "gift_idea",
                    "interest",
                    "recipient_interest",
                    "gift_idea_interest")) {

                try (
                        PreparedStatement p = prepare(
                                c, "SELECT * FROM " + t(name) + " WHERE 1=0");
                        ResultSet ignored = p.executeQuery()
                ) {
                    // Достаточно успешного выполнения запроса.
                }
            }

            // Проверяем наличие столбцов составной ссылки на получателя
            // в таблице его интересов.
            try (
                    PreparedStatement p = prepare(
                            c,
                            "SELECT owner_id, recipient_id FROM "
                                    + t("recipient_interest") + " WHERE 1=0");
                    ResultSet ignored = p.executeQuery()
            ) {
                // Строки данных не нужны: проверяется структура.
            }

            // Получаем названия столбцов первичного ключа recipient
            // через метаданные JDBC. Порядок столбцов здесь не учитывается.
            Set<String> keys = new HashSet<String>();
            try (
                    ResultSet r = c.getMetaData().getPrimaryKeys(
                            c.getCatalog(), config.schema, "recipient")
            ) {
                while (r.next()) {
                    keys.add(
                            r.getString("COLUMN_NAME").toLowerCase(Locale.ROOT)
                    );
                }
            }

            // Ожидается ключ из двух столбцов: owner_id и recipient_id.
            // Это проверка версии структуры, а не полное доказательство НФБК.
            if (!keys.equals(new HashSet<String>(
                    Arrays.asList("owner_id", "recipient_id")))) {
                throw new SQLException(
                        "Нужна обновлённая схема с составным ключом получателя."
                                + " Выполните миграцию НФБК."
                );
            }
        }
    }
}
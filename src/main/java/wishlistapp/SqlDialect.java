package wishlistapp;

/**
 * Отличия SQL-синтаксиса PostgreSQL и SQL Server:
 * оформление имён таблиц и блокировка при выдаче локального номера получателя.
 */
public interface SqlDialect {

    // Полное имя таблицы: схема и название с нужными кавычками.
    String table(String name);

    // Запрос блокировки строки владельца на время транзакции.
    String lockUser();

    // Выбираем реализацию по типу СУБД из проверенных настроек.
    static SqlDialect create(Config c) {
        if (c.type.equals("postgresql")) {
            return new PostgresDialect(c.schema);
        }
        return new SqlServerDialect(c.schema);
    }
}

/** Реализация SQL-синтаксиса PostgreSQL. */
final class PostgresDialect implements SqlDialect {

    private final String schema;

    PostgresDialect(String schema) {
        this.schema = schema;
    }

    // Двойные кавычки сохраняют точное написание имён, включая регистр.
    // Пример: "wishlistApp"."app_user".
    public String table(String name) {
        return "\"" + schema + "\".\"" + name + "\"";
    }

    // FOR UPDATE блокирует строку владельца до завершения транзакции.
    // Это позволяет последовательно выдавать номера получателей одного владельца.
    public String lockUser() {
        return "SELECT user_id FROM " + table("app_user")
                + " WHERE user_id=? FOR UPDATE";
    }
}

/** Реализация SQL-синтаксиса Microsoft SQL Server. */
final class SqlServerDialect implements SqlDialect {

    private final String schema;

    SqlServerDialect(String schema) {
        this.schema = schema;
    }

    // Имена схемы и таблицы заключаются в квадратные скобки.
    // Пример: [wishlistApp].[app_user].
    public String table(String name) {
        return "[" + schema + "].[" + name + "]";
    }

    // UPDLOCK запрашивает блокировки обновления, HOLDLOCK задаёт
    // для этой таблицы поведение уровня SERIALIZABLE до завершения транзакции.
    // Используется для той же задачи выдачи локальных номеров, что и FOR UPDATE.
    public String lockUser() {
        return "SELECT user_id FROM " + table("app_user")
                + " WITH (UPDLOCK,HOLDLOCK) WHERE user_id=?";
    }
}
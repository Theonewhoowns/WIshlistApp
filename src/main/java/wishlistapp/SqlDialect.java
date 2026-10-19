package wishlistapp;

/** Различается только синтаксис имён и блокировки выдачи локального номера. */
public interface SqlDialect {
    String table(String name);
    String lockUser();
    static SqlDialect create(Config c) {
        if (c.type.equals("postgresql")) return new PostgresDialect(c.schema);
        return new SqlServerDialect(c.schema);
    }
}
final class PostgresDialect implements SqlDialect {
    private final String schema;
    PostgresDialect(String schema) { this.schema = schema; }
    public String table(String name) { return "\"" + schema + "\".\"" + name + "\""; }
    public String lockUser() { return "SELECT user_id FROM " + table("app_user") + " WHERE user_id=? FOR UPDATE"; }
}
final class SqlServerDialect implements SqlDialect {
    private final String schema;
    SqlServerDialect(String schema) { this.schema = schema; }
    public String table(String name) { return "[" + schema + "].[" + name + "]"; }
    public String lockUser() { return "SELECT user_id FROM " + table("app_user") + " WITH (UPDLOCK,HOLDLOCK) WHERE user_id=?"; }
}

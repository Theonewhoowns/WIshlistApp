package wishlistapp;

import java.sql.*;
import java.util.*;

/** Только JDBC: отдельное соединение на операцию, ресурсы закрывает вызывающий код. */
public final class Database {
    public final Config config;
    public final SqlDialect dialect;
    public Database(Config config) throws ClassNotFoundException {
        this.config = config; dialect = SqlDialect.create(config);
        Class.forName(config.type.equals("postgresql") ? "org.postgresql.Driver" : "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        DriverManager.setLoginTimeout(8);
    }
    public Connection open() throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", config.user); p.setProperty("password", config.password);
        if (config.type.equals("postgresql")) { p.setProperty("connectTimeout", "8"); p.setProperty("socketTimeout", "30"); }
        else { p.setProperty("loginTimeout", "8"); p.setProperty("socketTimeout", "30000"); }
        return DriverManager.getConnection(config.url,p);
    }
    public String t(String name) { return dialect.table(name); }
    public PreparedStatement prepare(Connection c, String sql) throws SQLException {
        PreparedStatement p = c.prepareStatement(sql); p.setQueryTimeout(20); return p;
    }
    public void verify() throws SQLException {
        try (Connection c=open()) {
            for (String name: Arrays.asList("app_user","recipient","wishlist","wish_item","gift_idea","interest","recipient_interest","gift_idea_interest"))
                try (PreparedStatement p=prepare(c,"SELECT * FROM "+t(name)+" WHERE 1=0"); ResultSet ignored=p.executeQuery()) { }
            try (PreparedStatement p=prepare(c,"SELECT owner_id,recipient_id FROM "+t("recipient_interest")+" WHERE 1=0"); ResultSet ignored=p.executeQuery()) { }
            Set<String> keys=new HashSet<String>();
            try (ResultSet r=c.getMetaData().getPrimaryKeys(c.getCatalog(),config.schema,"recipient")) {
                while(r.next()) keys.add(r.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
            if (!keys.equals(new HashSet<String>(Arrays.asList("owner_id","recipient_id"))))
                throw new SQLException("Нужна обновлённая схема с составным ключом получателя. Выполните миграцию НФБК.");
        }
    }
}

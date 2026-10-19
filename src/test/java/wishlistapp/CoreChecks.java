package wishlistapp;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Запускаемые проверки без БД и без тестовых фреймворков. */
public final class CoreChecks {
    private static int count;
    private static void check(boolean b,String message){if(!b)throw new AssertionError(message);count++;}
    private static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException expected){count++;return;}throw new AssertionError("Ожидался отказ валидации");}
    public static void main(String[] args)throws Exception{
        check(Validation.money(" 1250,50 ").equals(new BigDecimal("1250.50")),"Русская десятичная запятая");
        check(Validation.money("0").equals(new BigDecimal("0.00")),"Нулевая цена");
        check(Validation.money("9999999999.99").precision()==12,"Верхний предел DECIMAL");
        reject(()->Validation.money("-1"));reject(()->Validation.money("NaN"));reject(()->Validation.money("1.001"));reject(()->Validation.money("10000000000"));
        check(Validation.age("120")==120,"Верхняя граница возраста");reject(()->Validation.age("121"));reject(()->Validation.age("2.5"));
        reject(()->Validation.text("   ",100,"Имя"));check(Validation.text(" Мама ",100,"Имя").equals("Мама"),"Обрезка пробелов");
        // Независимый сохранённый в SQL вектор PBKDF2 (admin).
        check(PasswordHasher.verify("DemoPass123!".toCharArray(),"JIs7XyxrzGtBivnOzbjdU9CHTWYUKgHW+j12KZvnOVo=","3gtVR/h9nxSghgJsZcAlSQ==",600000),"Совместимость с SQL-хешами");
        check(!PasswordHasher.verify("wrong".toCharArray(),"JIs7XyxrzGtBivnOzbjdU9CHTWYUKgHW+j12KZvnOVo=","3gtVR/h9nxSghgJsZcAlSQ==",600000),"Отказ неверному паролю");
        PasswordHasher.Encoded a=PasswordHasher.create("test12345".toCharArray()),b=PasswordHasher.create("test12345".toCharArray());
        check(!a.salt.equals(b.salt)&&!a.hash.equals(b.hash),"Индивидуальная соль");
        Path file=Files.createTempFile("wishlist-config-",".properties");
        try{
            Files.write(file,("db.type=postgresql\ndb.url=jdbc:postgresql://localhost/postgres\ndb.user=test\ndb.schema=wishlistApp\n").getBytes(StandardCharsets.UTF_8));
            Config c=Config.load(file);check(SqlDialect.create(c).table("recipient").equals("\"wishlistApp\".\"recipient\""),"Регистр схемы PostgreSQL");
            Files.write(file,("db.type=sqlserver\ndb.url=jdbc:sqlserver://localhost;databaseName=Test\ndb.user=test\n").getBytes(StandardCharsets.UTF_8));
            c=Config.load(file);check(SqlDialect.create(c).lockUser().contains("UPDLOCK,HOLDLOCK"),"Блокировка выдачи локального номера SQL Server");
            Files.write(file,("db.type=postgresql\ndb.url=jdbc:postgresql://localhost/postgres\ndb.user=test\ndb.schema=bad;DROP\n").getBytes(StandardCharsets.UTF_8));
            boolean invalid=false;try{Config.load(file);}catch(IllegalArgumentException e){invalid=true;}check(invalid,"Имя схемы не допускает SQL-вставку");
        }finally{Files.deleteIfExists(file);}
        Class.forName("org.postgresql.Driver");Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        check(java.sql.DriverManager.getDriver("jdbc:postgresql://localhost/postgres")!=null,"Драйвер PostgreSQL загружается в Java 8");
        check(java.sql.DriverManager.getDriver("jdbc:sqlserver://localhost;databaseName=WishlistApp")!=null,"Драйвер SQL Server загружается в Java 8");
        System.out.println("PASS: "+count+" проверок на Java "+System.getProperty("java.version"));
    }
}

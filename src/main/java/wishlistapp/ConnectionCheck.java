package wishlistapp;

import java.nio.file.Paths;
import java.sql.*;

/** Проверяет подключение и версию схемы без изменения данных и без JavaFX. */
public final class ConnectionCheck {
    public static void main(String[] args){
        try{
            Config config=Config.load(Paths.get(args.length==0?"config/db.properties":args[0]));
            Database db=new Database(config);db.verify();
            try(Connection c=db.open()){
                System.out.println("Подключение успешно: "+c.getMetaData().getDatabaseProductName()+"; база "+c.getCatalog()+"; схема "+config.schema);
            }
        }catch(Exception e){System.err.println("Не удалось подключиться: "+e.getMessage());System.exit(1);}
    }
}

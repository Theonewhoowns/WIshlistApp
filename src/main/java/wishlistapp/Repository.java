package wishlistapp;

import java.sql.*;
import java.math.BigDecimal;
import java.security.GeneralSecurityException;
import java.util.*;
import wishlistapp.Models.*;

/** JDBC-операции. Пользовательские значения передаются только параметрами. */
public final class Repository {
    private final Database db;
    private final User user;
    public Repository(Database db,User user){this.db=db;this.user=user;}
    private String t(String name){return db.t(name);}
    private void admin(){if(!user.admin())throw new IllegalStateException("Операция доступна администратору");}
    private static Integer nullableInt(ResultSet r,String column)throws SQLException{int x=r.getInt(column);return r.wasNull()?null:x;}
    private static void nullable(PreparedStatement p,int i,Integer n)throws SQLException{if(n==null)p.setNull(i,Types.INTEGER);else p.setInt(i,n);}
    private static void changed(int count)throws SQLException{if(count!=1)throw new SQLException("Запись не найдена либо больше недоступна. Обновите данные.");}
    private static void rollback(Connection c,Exception original){try{c.rollback();}catch(SQLException e){original.addSuppressed(e);}}

    public static User login(Database db,String raw,char[] password)throws SQLException,GeneralSecurityException{
        String name=raw==null?"":raw.trim();
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"SELECT user_id,username,role,password_hash,password_salt,password_iterations FROM "+db.t("app_user")+" WHERE LOWER(username)=LOWER(?)")){
            p.setString(1,name);
            try(ResultSet r=p.executeQuery()){
                if(!r.next())return null;
                if(!PasswordHasher.verify(password,r.getString("password_hash"),r.getString("password_salt"),r.getInt("password_iterations")))return null;
                return new User(r.getInt("user_id"),r.getString("username"),r.getString("role"));
            }
        }
    }
    public static void register(Database db,String raw,char[] password)throws SQLException,GeneralSecurityException{
        String name=raw==null?"":raw.trim();
        if(!name.matches("[A-Za-z0-9_]{3,30}"))throw new IllegalArgumentException("Логин: 3–30 латинских букв, цифр или _");
        if(password.length<8||password.length>256)throw new IllegalArgumentException("Пароль: от 8 до 256 символов");
        PasswordHasher.Encoded h=PasswordHasher.create(password);
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"INSERT INTO "+db.t("app_user")+" (username,password_hash,password_salt,password_iterations,role) VALUES (?,?,?,?,?)")){
            p.setString(1,name);p.setString(2,h.hash);p.setString(3,h.salt);p.setInt(4,PasswordHasher.ITERATIONS);p.setString(5,"USER");p.executeUpdate();
        }
    }
    public List<Recipient> recipients()throws SQLException{
        List<Recipient> out=new ArrayList<Recipient>();
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"SELECT recipient_id,name,age FROM "+t("recipient")+" WHERE owner_id=? ORDER BY name,recipient_id")){
            p.setInt(1,user.id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new Recipient(r.getInt(1),r.getString(2),r.getInt(3)));}
        }return out;
    }
    public Set<Integer> recipientInterests(int id)throws SQLException{
        Set<Integer> out=new HashSet<Integer>();
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"SELECT interest_id FROM "+t("recipient_interest")+" WHERE owner_id=? AND recipient_id=?")){
            p.setInt(1,user.id);p.setInt(2,id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(r.getInt(1));}
        }return out;
    }
    public void saveRecipient(Integer id,String name,int age,Set<Integer> interests)throws SQLException{
        name=Validation.text(name,100,"Имя");if(age<0||age>120)throw new IllegalArgumentException("Возраст 0–120");
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try{
                // Один и тот же протокол для всех вставок: блокируем владельца до COMMIT.
                try(PreparedStatement p=db.prepare(c,db.dialect.lockUser())){p.setInt(1,user.id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("Пользователь не найден");}}
                if(id==null){
                    try(PreparedStatement p=db.prepare(c,"SELECT COALESCE(MAX(recipient_id),0) FROM "+t("recipient")+" WHERE owner_id=?")){
                        p.setInt(1,user.id);try(ResultSet r=p.executeQuery()){r.next();int max=r.getInt(1);if(max==Integer.MAX_VALUE)throw new SQLException("Исчерпан диапазон номеров");id=max+1;}
                    }
                    try(PreparedStatement p=db.prepare(c,"INSERT INTO "+t("recipient")+" (owner_id,recipient_id,name,age) VALUES (?,?,?,?)")){
                        p.setInt(1,user.id);p.setInt(2,id);p.setString(3,name);p.setInt(4,age);p.executeUpdate();
                    }
                }else{
                    try(PreparedStatement p=db.prepare(c,"UPDATE "+t("recipient")+" SET name=?,age=? WHERE owner_id=? AND recipient_id=?")){
                        p.setString(1,name);p.setInt(2,age);p.setInt(3,user.id);p.setInt(4,id);changed(p.executeUpdate());
                    }
                }
                try(PreparedStatement p=db.prepare(c,"DELETE FROM "+t("recipient_interest")+" WHERE owner_id=? AND recipient_id=?")){p.setInt(1,user.id);p.setInt(2,id);p.executeUpdate();}
                try(PreparedStatement p=db.prepare(c,"INSERT INTO "+t("recipient_interest")+" (owner_id,recipient_id,interest_id) VALUES (?,?,?)")){
                    for(int interest:interests){p.setInt(1,user.id);p.setInt(2,id);p.setInt(3,interest);p.addBatch();}p.executeBatch();
                }c.commit();
            }catch(SQLException|RuntimeException e){rollback(c,e);throw e;}
        }
    }
    public void deleteRecipient(int id)throws SQLException{
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"DELETE FROM "+t("recipient")+" WHERE owner_id=? AND recipient_id=?")){p.setInt(1,user.id);p.setInt(2,id);changed(p.executeUpdate());}
    }
    public List<WishList> lists()throws SQLException{
        List<WishList> out=new ArrayList<WishList>();
        String sql="SELECT w.wishlist_id,w.name,w.recipient_id,r.name AS recipient_name FROM "+t("wishlist")+" w LEFT JOIN "+t("recipient")+" r ON r.owner_id=w.owner_id AND r.recipient_id=w.recipient_id WHERE w.owner_id=? ORDER BY w.name,w.wishlist_id";
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,sql)){p.setInt(1,user.id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new WishList(r.getInt("wishlist_id"),r.getString("name"),nullableInt(r,"recipient_id"),r.getString("recipient_name")));}}return out;
    }
    public void saveList(Integer id,String name,Integer recipient)throws SQLException{
        name=Validation.text(name,100,"Название списка");
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,id==null?
            "INSERT INTO "+t("wishlist")+" (name,recipient_id,owner_id) VALUES (?,?,?)":
            "UPDATE "+t("wishlist")+" SET name=?,recipient_id=? WHERE owner_id=? AND wishlist_id=?")){
            p.setString(1,name);nullable(p,2,recipient);p.setInt(3,user.id);if(id!=null)p.setInt(4,id);changed(p.executeUpdate());
        }
    }
    public void deleteList(int id)throws SQLException{
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"DELETE FROM "+t("wishlist")+" WHERE wishlist_id=? AND owner_id=?")){p.setInt(1,id);p.setInt(2,user.id);changed(p.executeUpdate());}
    }
    public List<Wish> wishes(int listId)throws SQLException{
        List<Wish> out=new ArrayList<Wish>();
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"SELECT i.* FROM "+t("wish_item")+" i JOIN "+t("wishlist")+" w ON w.wishlist_id=i.wishlist_id WHERE w.owner_id=? AND w.wishlist_id=? ORDER BY i.wish_item_id")){
            p.setInt(1,user.id);p.setInt(2,listId);try(ResultSet r=p.executeQuery()){while(r.next())out.add(new Wish(r.getInt("wish_item_id"),nullableInt(r,"source_idea_id"),r.getString("title"),r.getString("description"),r.getBigDecimal("planned_price"),r.getString("image_path"),r.getBoolean("is_purchased")));}
        }return out;
    }
    public void saveWish(Integer id,int listId,Integer ideaId,String title,String desc,BigDecimal price,String image)throws SQLException{
        title=Validation.text(title,150,"Название");desc=Validation.optional(desc,2000,"Описание");image=Validation.optional(image,500,"Путь изображения");
        price=Validation.money(price.toPlainString());
        // INSERT SELECT одновременно проверяет, что список принадлежит текущему пользователю.
        String sql=id==null?"INSERT INTO "+t("wish_item")+" (title,description,planned_price,image_path,source_idea_id,wishlist_id) SELECT ?,?,?,?,?,wishlist_id FROM "+t("wishlist")+" WHERE wishlist_id=? AND owner_id=?":
            "UPDATE "+t("wish_item")+" SET title=?,description=?,planned_price=?,image_path=? WHERE wish_item_id=? AND wishlist_id IN (SELECT wishlist_id FROM "+t("wishlist")+" WHERE owner_id=?)";
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,sql)){
            p.setString(1,title);p.setString(2,desc);p.setBigDecimal(3,price);p.setString(4,image);
            if(id==null){nullable(p,5,ideaId);p.setInt(6,listId);p.setInt(7,user.id);}else{p.setInt(5,id);p.setInt(6,user.id);}changed(p.executeUpdate());
        }
    }
    public void setPurchased(int id,boolean value)throws SQLException{
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"UPDATE "+t("wish_item")+" SET is_purchased=? WHERE wish_item_id=? AND wishlist_id IN (SELECT wishlist_id FROM "+t("wishlist")+" WHERE owner_id=?)")){
            p.setBoolean(1,value);p.setInt(2,id);p.setInt(3,user.id);changed(p.executeUpdate());
        }
    }
    public void deleteWish(int id)throws SQLException{
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"DELETE FROM "+t("wish_item")+" WHERE wish_item_id=? AND wishlist_id IN (SELECT wishlist_id FROM "+t("wishlist")+" WHERE owner_id=?)")){
            p.setInt(1,id);p.setInt(2,user.id);changed(p.executeUpdate());
        }
    }
    public List<Interest> interests()throws SQLException{
        List<Interest> out=new ArrayList<Interest>();try(Connection c=db.open();PreparedStatement p=db.prepare(c,"SELECT interest_id,name FROM "+t("interest")+" ORDER BY name");ResultSet r=p.executeQuery()){while(r.next())out.add(new Interest(r.getInt(1),r.getString(2)));}return out;
    }
    public void saveInterest(Integer id,String name)throws SQLException{
        admin();name=Validation.text(name,100,"Интерес");
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,id==null?"INSERT INTO "+t("interest")+" (name) VALUES (?)":"UPDATE "+t("interest")+" SET name=? WHERE interest_id=?")){
            p.setString(1,name);if(id!=null)p.setInt(2,id);changed(p.executeUpdate());
        }
    }
    public void deleteInterest(int id)throws SQLException{
        admin();try(Connection c=db.open();PreparedStatement p=db.prepare(c,"DELETE FROM "+t("interest")+" WHERE interest_id=?")){p.setInt(1,id);changed(p.executeUpdate());}
    }
    private static Idea idea(ResultSet r,int matches)throws SQLException{
        return new Idea(r.getInt("gift_idea_id"),r.getString("title"),r.getString("description"),r.getBigDecimal("min_price"),r.getBigDecimal("max_price"),r.getInt("min_age"),r.getInt("max_age"),r.getString("occasion"),r.getString("image_path"),r.getBoolean("is_active"),matches);
    }
    public List<Idea> catalog()throws SQLException{
        List<Idea> out=new ArrayList<Idea>();
        try(Connection c=db.open();PreparedStatement p=db.prepare(c,"SELECT * FROM "+t("gift_idea")+" WHERE is_active=? OR ?=? ORDER BY title,gift_idea_id")){
            p.setBoolean(1,true);p.setInt(2,user.admin()?1:0);p.setInt(3,1);try(ResultSet r=p.executeQuery()){while(r.next())out.add(idea(r,0));}
        }return out;
    }
    public Set<Integer> ideaInterests(int id)throws SQLException{
        Set<Integer> out=new HashSet<Integer>();try(Connection c=db.open();PreparedStatement p=db.prepare(c,"SELECT interest_id FROM "+t("gift_idea_interest")+" WHERE gift_idea_id=?")){p.setInt(1,id);try(ResultSet r=p.executeQuery()){while(r.next())out.add(r.getInt(1));}}return out;
    }
    public List<Idea> recommend(int recipientId,BigDecimal budget,String occasion)throws SQLException{
        budget=Validation.money(budget.toPlainString());
        String matching=" FROM "+t("gift_idea_interest")+" gi JOIN "+t("recipient_interest")+" ri ON ri.interest_id=gi.interest_id WHERE gi.gift_idea_id=g.gift_idea_id AND ri.owner_id=r.owner_id AND ri.recipient_id=r.recipient_id";
        String sql="SELECT g.*,(SELECT COUNT(*)"+matching+") AS matched_count FROM "+t("gift_idea")+" g JOIN "+t("recipient")+" r ON r.owner_id=? AND r.recipient_id=? WHERE g.is_active=? AND r.age BETWEEN g.min_age AND g.max_age AND g.max_price<=? AND (g.occasion='ANY' OR g.occasion=?) AND (NOT EXISTS (SELECT 1 FROM "+t("recipient_interest")+" ri WHERE ri.owner_id=r.owner_id AND ri.recipient_id=r.recipient_id) OR EXISTS (SELECT 1"+matching+")) ORDER BY matched_count DESC,g.max_price,g.gift_idea_id";
        List<Idea> out=new ArrayList<Idea>();try(Connection c=db.open();PreparedStatement p=db.prepare(c,sql)){
            p.setInt(1,user.id);p.setInt(2,recipientId);p.setBoolean(3,true);p.setBigDecimal(4,budget);p.setString(5,occasion);
            try(ResultSet r=p.executeQuery()){while(r.next())out.add(idea(r,r.getInt("matched_count")));}
        }return out;
    }
    public void saveIdea(Integer id,String title,String desc,BigDecimal min,BigDecimal max,int minAge,int maxAge,String occasion,String image,boolean active,Set<Integer> interests)throws SQLException{
        admin();title=Validation.text(title,150,"Название");desc=Validation.optional(desc,2000,"Описание");image=Validation.optional(image,500,"Изображение");
        min=Validation.money(min.toPlainString());max=Validation.money(max.toPlainString());
        if(min.compareTo(max)>0)throw new IllegalArgumentException("Минимальная цена больше максимальной");
        if(minAge<0||maxAge>120||minAge>maxAge)throw new IllegalArgumentException("Некорректный диапазон возраста");
        try(Connection c=db.open()){
            c.setAutoCommit(false);
            try{
                String sql=id==null?"INSERT INTO "+t("gift_idea")+" (title,description,min_price,max_price,min_age,max_age,occasion,image_path,is_active) VALUES (?,?,?,?,?,?,?,?,?)":
                    "UPDATE "+t("gift_idea")+" SET title=?,description=?,min_price=?,max_price=?,min_age=?,max_age=?,occasion=?,image_path=?,is_active=? WHERE gift_idea_id=?";
                try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
                    p.setQueryTimeout(20);p.setString(1,title);p.setString(2,desc);p.setBigDecimal(3,min);p.setBigDecimal(4,max);p.setInt(5,minAge);p.setInt(6,maxAge);p.setString(7,occasion);p.setString(8,image);p.setBoolean(9,active);if(id!=null)p.setInt(10,id);changed(p.executeUpdate());
                    if(id==null)try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("Не получен идентификатор идеи");id=r.getInt(1);}
                }
                try(PreparedStatement p=db.prepare(c,"DELETE FROM "+t("gift_idea_interest")+" WHERE gift_idea_id=?")){p.setInt(1,id);p.executeUpdate();}
                try(PreparedStatement p=db.prepare(c,"INSERT INTO "+t("gift_idea_interest")+" (gift_idea_id,interest_id) VALUES (?,?)")){
                    for(int interest:interests){p.setInt(1,id);p.setInt(2,interest);p.addBatch();}p.executeBatch();
                }c.commit();
            }catch(SQLException|RuntimeException e){rollback(c,e);throw e;}
        }
    }
    public void setIdeaActive(int id,boolean active)throws SQLException{
        admin();try(Connection c=db.open();PreparedStatement p=db.prepare(c,"UPDATE "+t("gift_idea")+" SET is_active=? WHERE gift_idea_id=?")){p.setBoolean(1,active);p.setInt(2,id);changed(p.executeUpdate());}
    }
}

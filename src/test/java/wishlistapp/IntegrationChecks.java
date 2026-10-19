package wishlistapp;

import java.nio.file.Paths;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import wishlistapp.Models.*;

/** Реальный JDBC-тест: создаёт временные учётные записи и удаляет их в finally. */
public final class IntegrationChecks {
    private static int count;
    private static void check(boolean b,String name){if(!b)throw new AssertionError(name);count++;}
    public static void main(String[] args)throws Exception{
        Database db=new Database(Config.load(Paths.get(args.length==0?"config/db.properties":args[0])));db.verify();
        String prefix="it_"+Long.toString(System.currentTimeMillis(),36)+UUID.randomUUID().toString().substring(0,5);
        String a=prefix+"a",b=prefix+"b";char[] password="Integration123!".toCharArray();
        try{
            Repository.register(db,a,password);Repository.register(db,b,password);
            User ua=Repository.login(db,a,password),ub=Repository.login(db,b,password);
            check(ua!=null&&ub!=null,"Регистрация и вход");check(!ua.admin(),"Саморегистрация не выдаёт ADMIN");
            check(Repository.login(db,a,"wrong".toCharArray())==null,"Неверный пароль");
            Repository ra=new Repository(db,ua),rb=new Repository(db,ub);
            List<Interest> all=ra.interests();Set<Integer> tags=new HashSet<Integer>();if(!all.isEmpty())tags.add(all.get(0).id);
            ra.saveRecipient(null,"Получатель А",25,tags);ra.saveRecipient(null,"Получатель Б",40,Collections.<Integer>emptySet());rb.saveRecipient(null,"Получатель другого владельца",30,Collections.<Integer>emptySet());
            check(ra.recipients().size()==2&&rb.recipients().size()==1,"Разделение получателей владельцами");
            check(ra.recipients().get(0).id==1&&rb.recipients().get(0).id==1,"Одинаковые локальные номера допустимы");
            ra.saveList(null,"Тестовый список",1);rb.saveList(null,"Тестовый список",1);
            WishList la=ra.lists().get(0),lb=rb.lists().get(0);
            ra.saveWish(null,la.id,null,"Позиция",null,new BigDecimal("100.25"),null);
            Wish item=ra.wishes(la.id).get(0);check(item.price.equals(new BigDecimal("100.25")),"Точное денежное значение");
            check(rb.wishes(la.id).isEmpty(),"Чужие желания не читаются");
            boolean denied=false;try{rb.setPurchased(item.id,true);}catch(SQLException e){denied=true;}check(denied,"Чужое желание не изменяется");
            ra.setPurchased(item.id,true);check(ra.wishes(la.id).get(0).purchased,"Отметка покупки сохранена");
            ra.saveRecipient(1,"Переименованный",26,tags);check(ra.recipientInterests(1).equals(tags),"Интересы обновляются транзакционно");
            denied=false;try{ra.saveRecipient(1,"НЕ СОХРАНЯТЬ",27,new HashSet<Integer>(Arrays.asList(-1)));}catch(SQLException e){denied=true;}
            check(denied,"Несуществующий интерес запрещён");
            boolean correct=false;for(Recipient r:ra.recipients())if(r.id==1)correct=r.name.equals("Переименованный")&&r.age==26;
            check(correct&&ra.recipientInterests(1).equals(tags),"Ошибка назначения интереса откатывает весь профиль");
            denied=false;try{ra.deleteRecipient(1);}catch(SQLException e){denied=true;}check(denied,"Удаление получателя со списком запрещено");
            denied=false;try{ra.saveInterest(null,"Не должно создаться");}catch(IllegalStateException e){denied=true;}check(denied,"Сервис проверяет ADMIN");
            ra.deleteList(la.id);check(ra.wishes(la.id).isEmpty(),"Каскадное удаление желаний");
            check(!rb.lists().isEmpty()&&rb.lists().get(0).id==lb.id,"Удаление не затронуло другого владельца");
            System.out.println("PASS: "+count+" JDBC-проверок; "+db.config.type);
        }finally{
            // При очистке используем только точные имена двух созданных тестом пользователей.
            try(Connection c=db.open()){
                c.setAutoCommit(false);
                try{
                    for(String name:Arrays.asList(a,b)){
                        Integer id=null;try(PreparedStatement p=db.prepare(c,"SELECT user_id FROM "+db.t("app_user")+" WHERE username=?")){p.setString(1,name);try(ResultSet r=p.executeQuery()){if(r.next())id=r.getInt(1);}}
                        if(id==null)continue;
                        for(String table:Arrays.asList("wishlist","recipient"))try(PreparedStatement p=db.prepare(c,"DELETE FROM "+db.t(table)+" WHERE owner_id=?")){p.setInt(1,id);p.executeUpdate();}
                        try(PreparedStatement p=db.prepare(c,"DELETE FROM "+db.t("app_user")+" WHERE user_id=?")){p.setInt(1,id);p.executeUpdate();}
                    }c.commit();
                }catch(SQLException e){c.rollback();throw e;}
            }
            Arrays.fill(password,'\0');
        }
    }
}

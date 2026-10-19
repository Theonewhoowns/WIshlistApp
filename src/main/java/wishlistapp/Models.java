package wishlistapp;

import java.math.BigDecimal;

public final class Models {
    private Models() { }
    public static final class User {
        public final int id; public final String username,role;
        public User(int id,String username,String role){this.id=id;this.username=username;this.role=role;}
        public boolean admin(){return "ADMIN".equals(role);}
    }
    public static final class Recipient {
        public final int id,age; public final String name;
        public Recipient(int id,String name,int age){this.id=id;this.name=name;this.age=age;}
        public String toString(){return name+" ("+age+" лет, №"+id+")";}
    }
    public static final class WishList {
        public final int id; public final Integer recipientId; public final String name,recipientName;
        public WishList(int id,String name,Integer recipientId,String recipientName){this.id=id;this.name=name;this.recipientId=recipientId;this.recipientName=recipientName;}
        public String toString(){return name+(recipientName==null?" · для себя":" · "+recipientName);}
    }
    public static final class Wish {
        public final int id; public final Integer ideaId; public final String title,description,image;
        public final BigDecimal price; public final boolean purchased;
        public Wish(int id,Integer ideaId,String title,String description,BigDecimal price,String image,boolean purchased){this.id=id;this.ideaId=ideaId;this.title=title;this.description=description;this.price=price;this.image=image;this.purchased=purchased;}
    }
    public static final class Interest {
        public final int id; public final String name;
        public Interest(int id,String name){this.id=id;this.name=name;}
        public String toString(){return name;}
    }
    public static final class Idea {
        public final int id,minAge,maxAge,matches; public final String title,description,occasion,image;
        public final BigDecimal minPrice,maxPrice; public final boolean active;
        public Idea(int id,String title,String description,BigDecimal min,BigDecimal max,int minAge,int maxAge,String occasion,String image,boolean active,int matches){
            this.id=id;this.title=title;this.description=description;minPrice=min;maxPrice=max;this.minAge=minAge;this.maxAge=maxAge;this.occasion=occasion;this.image=image;this.active=active;this.matches=matches;
        }
        public String toString(){return title;}
    }
}

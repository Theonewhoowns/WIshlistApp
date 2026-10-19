package wishlistapp;

import java.math.BigDecimal;

public final class Validation {
    private Validation() { }
    public static String text(String value,int max,String label) {
        String s=value==null?"":value.trim();
        if(s.isEmpty() || s.length()>max) throw new IllegalArgumentException(label+": от 1 до "+max+" символов");
        return s;
    }
    public static String optional(String value,int max,String label) {
        String s=value==null?"":value.trim();
        if(s.length()>max) throw new IllegalArgumentException(label+": максимум "+max+" символов");
        return s.isEmpty()?null:s;
    }
    public static BigDecimal money(String raw) {
        String s=raw==null?"":raw.trim().replace(',','.');
        if(!s.matches("[0-9]+(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Стоимость: неотрицательное число, не более двух знаков после запятой");
        BigDecimal v=new BigDecimal(s);
        if(v.compareTo(new BigDecimal("9999999999.99"))>0) throw new IllegalArgumentException("Стоимость слишком велика");
        return v.setScale(2);
    }
    public static int age(String raw) {
        try {int n=Integer.parseInt(raw.trim());if(n>=0&&n<=120)return n;}catch(Exception ignored){}
        throw new IllegalArgumentException("Возраст должен быть целым числом от 0 до 120");
    }
}

package wishlistapp;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {
    public static final int ITERATIONS=600000;
    public static final class Encoded {
        public final String hash,salt;
        Encoded(String hash,String salt){this.hash=hash;this.salt=salt;}
    }
    private PasswordHasher() { }
    public static Encoded create(char[] password) throws GeneralSecurityException {
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        return new Encoded(Base64.getEncoder().encodeToString(derive(password,salt,ITERATIONS)),Base64.getEncoder().encodeToString(salt));
    }
    static byte[] derive(char[] password,byte[] salt,int iterations) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(password,salt,iterations,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        finally{spec.clearPassword();}
    }
    public static boolean verify(char[] password,String hash,String salt,int iterations) throws GeneralSecurityException {
        if(iterations<1||iterations>5000000) throw new GeneralSecurityException("Некорректные параметры хеша в БД");
        try{return MessageDigest.isEqual(Base64.getDecoder().decode(hash),derive(password,Base64.getDecoder().decode(salt),iterations));}
        catch(IllegalArgumentException e){throw new GeneralSecurityException("Повреждены данные пароля в БД",e);}
    }
}

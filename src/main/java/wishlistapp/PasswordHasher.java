package wishlistapp;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Формирование хеша пароля с индивидуальной солью и проверка пароля при входе. */
public final class PasswordHasher {

    // Число итераций PBKDF2 при создании нового хеша.
    // Повторные вычисления повышают стоимость перебора паролей.
    public static final int ITERATIONS = 600000;

    /** Хеш и соль в формате Base64 для сохранения в БД. */
    public static final class Encoded {
        public final String hash, salt;

        Encoded(String hash, String salt) {
            this.hash = hash;
            this.salt = salt;
        }
    }

    // Служебный класс: все операции выполняются статическими методами.
    private PasswordHasher() { }

    /** Создаёт новую случайную соль и вычисляет хеш переданного пароля. */
    public static Encoded create(char[] password)
            throws GeneralSecurityException {

        // Индивидуальная соль длиной 16 байт.
        // Одинаковые пароли с разными солями дают разные хеши.
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        // Base64 преобразует байты в текст, но не является шифрованием.
        return new Encoded(
                Base64.getEncoder().encodeToString(
                        derive(password, salt, ITERATIONS)),
                Base64.getEncoder().encodeToString(salt)
        );
    }

    /** Вычисляет 256-битный результат PBKDF2-HMAC-SHA256 с заданными параметрами. */
    static byte[] derive(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {

        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);

        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } finally {
            // Очищаем копию пароля внутри PBEKeySpec даже при ошибке.
            spec.clearPassword();
        }
    }

    /** Проверяет введённый пароль по хешу, соли и числу итераций из БД. */
    public static boolean verify(
            char[] password, String hash, String salt, int iterations)
            throws GeneralSecurityException {

        // Отклоняем некорректное или чрезмерное число итераций.
        if (iterations < 1 || iterations > 5000000) {
            throw new GeneralSecurityException(
                    "Некорректные параметры хеша в БД");
        }

        try {
            // Повторяем вычисление с сохранённой солью и числом итераций.
            // Сравниваем байты специальным методом для сравнения хешей.
            return MessageDigest.isEqual(
                    Base64.getDecoder().decode(hash),
                    derive(password, Base64.getDecoder().decode(salt), iterations)
            );
        } catch (IllegalArgumentException e) {
            // В частности, обрабатываем некорректные строки Base64 из БД.
            throw new GeneralSecurityException(
                    "Повреждены данные пароля в БД", e);
        }
    }
}
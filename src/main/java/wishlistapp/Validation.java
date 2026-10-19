package wishlistapp;

import java.math.BigDecimal;

/** Проверка и нормализация введённых пользователем значений. */
public final class Validation {

    private Validation() { }

    /** Обязательный текст: удаляем крайние пробелы и проверяем длину. */
    public static String text(String value, int max, String label) {
        String s = value == null ? "" : value.trim();

        if (s.isEmpty() || s.length() > max) {
            throw new IllegalArgumentException(
                    label + ": от 1 до " + max + " символов");
        }

        return s;
    }

    /** Необязательный текст: пустое значение превращаем в null для записи в БД. */
    public static String optional(String value, int max, String label) {
        String s = value == null ? "" : value.trim();

        if (s.length() > max) {
            throw new IllegalArgumentException(
                    label + ": максимум " + max + " символов");
        }

        return s.isEmpty() ? null : s;
    }

    /** Проверяет стоимость и возвращает точное денежное значение. */
    public static BigDecimal money(String raw) {
        // Принимаем и точку, и запятую как разделитель дробной части.
        String s = raw == null ? "" : raw.trim().replace(',', '.');

        // Разрешаем неотрицательное число максимум с двумя дробными знаками.
        if (!s.matches("[0-9]+(\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException(
                    "Стоимость: неотрицательное число, "
                            + "не более двух знаков после запятой");
        }

        // Верхняя граница соответствует денежным столбцам NUMERIC/DECIMAL(12,2).
        BigDecimal v = new BigDecimal(s);
        if (v.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Стоимость слишком велика");
        }

        // Приводим к двум дробным знакам: например, 1500 → 1500.00.
        return v.setScale(2);
    }

    /** Преобразует введённый возраст в целое число диапазона 0–120. */
    public static int age(String raw) {
        try {
            int n = Integer.parseInt(raw.trim());

            if (n >= 0 && n <= 120) {
                return n;
            }
        } catch (Exception ignored) {
            // Ошибку чтения числа заменяем понятным сообщением ниже.
        }

        throw new IllegalArgumentException(
                "Возраст должен быть целым числом от 0 до 120");
    }
}
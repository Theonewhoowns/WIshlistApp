package wishlistapp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javafx.scene.image.Image;

/** Изображения хранятся в data/images, в БД сохраняется относительный путь. */
public final class Images {

    // Служебный класс: создание объектов не требуется, методы статические.
    private Images() {}

    /** Проверяет выбранное изображение, копирует его и возвращает путь к копии. */
    public static String importFile(File file) throws IOException {
        if (file == null) return null;

        // Определяем расширение файла. Поддерживаются только PNG и JPEG.
        String name = file.getName().toLowerCase(Locale.ROOT);
        String ext = name.endsWith(".png") ? ".png"
                : name.endsWith(".jpg") ? ".jpg"
                : name.endsWith(".jpeg") ? ".jpeg"
                : null;

        if (ext == null)
            throw new IOException("Выберите PNG или JPEG");

        // Ограничиваем размер исходного файла — не более 20 МБ.
        if (Files.size(file.toPath()) > 20L * 1024 * 1024)
            throw new IOException("Изображение должно быть не больше 20 МБ");

        // Проверяем, что JavaFX действительно может прочитать изображение.
        // Загружаем с сохранением пропорций в пределах 160×160 пикселей.
        Image test = new Image(
                file.toURI().toString(), 160, 160, true, true, false);

        if (test.isError())
            throw new IOException("Не удалось прочитать изображение");

        // Создаём папку хранения и формируем случайное имя файла,
        // чтобы изображения с одинаковыми исходными именами не конфликтовали.
        Path dir = Paths.get("data", "images");
        Files.createDirectories(dir);
        Path target = dir.resolve(UUID.randomUUID().toString() + ext);

        // Копируем исходный файл без изменения его содержимого.
        // Возвращаем относительный путь с единым разделителем для Windows и Linux.
        Files.copy(file.toPath(), target);
        return target.toString().replace(File.separatorChar, '/');
    }

    /** Загружает изображение по пути или возвращает заглушку. */
    public static Image load(String path) {
        try {
            // Проверяем наличие пути и существование файла.
            // Относительный путь отсчитывается от рабочей папки приложения.
            if (path != null && !path.trim().isEmpty()) {
                Path p = Paths.get(path);

                if (Files.isRegularFile(p)) {
                    // Загружаем изображение для отображения в интерфейсе.
                    Image i = new Image(
                            p.toUri().toString(), 160, 160, true, true, false);

                    if (!i.isError()) return i;
                }
            }
        } catch (RuntimeException ignored) {
            // При ошибке обработки пути или загрузки переходим к заглушке.
        }

        // Если изображение недоступно, используем ресурс из приложения.
        return new Image(
                Images.class.getResourceAsStream("/images/placeholder.png"),
                160, 160, true, true);
    }
}
package wishlistapp;

import java.math.BigDecimal;

/**
 * Объекты данных, которые Repository создаёт из результатов SQL-запросов
 * и передаёт графическому интерфейсу.
 */
public final class Models {

    // Внешний класс используется как контейнер для вложенных классов.
    private Models() { }

    /** Пользователь приложения: идентификатор, логин и роль. */
    public static final class User {
        public final int id;
        public final String username, role;

        public User(int id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }

        // Проверка административной роли для интерфейса и операций Repository.
        public boolean admin() {
            return "ADMIN".equals(role);
        }
    }

    /** Получатель подарков. id — локальный номер в пределах владельца. */
    public static final class Recipient {
        public final int id, age;
        public final String name;

        public Recipient(int id, String name, int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        // Подпись получателя в списках и элементах выбора интерфейса.
        public String toString() {
            return name + " (" + age + " лет, №" + id + ")";
        }
    }

    /** Список желаний с необязательной привязкой к получателю. */
    public static final class WishList {
        public final int id;

        // Integer допускает null: список может быть личным, без получателя.
        public final Integer recipientId;
        public final String name, recipientName;

        public WishList(int id, String name,
                        Integer recipientId, String recipientName) {
            this.id = id;
            this.name = name;
            this.recipientId = recipientId;
            this.recipientName = recipientName;
        }

        // Показываем название списка и его назначение.
        public String toString() {
            return name + (recipientName == null
                    ? " · для себя" : " · " + recipientName);
        }
    }

    /** Отдельное желание: описание, планируемая цена, изображение и статус покупки. */
    public static final class Wish {
        public final int id;

        // Ссылка на исходную идею; null для желания, созданного самостоятельно.
        public final Integer ideaId;
        public final String title, description, image;

        // BigDecimal используется для точного представления денежных значений.
        public final BigDecimal price;
        public final boolean purchased;

        public Wish(int id, Integer ideaId, String title, String description,
                    BigDecimal price, String image, boolean purchased) {
            this.id = id;
            this.ideaId = ideaId;
            this.title = title;
            this.description = description;
            this.price = price;
            this.image = image;
            this.purchased = purchased;
        }
    }

    /** Элемент общего справочника интересов. */
    public static final class Interest {
        public final int id;
        public final String name;

        public Interest(int id, String name) {
            this.id = id;
            this.name = name;
        }

        // В интерфейсе интерес отображается своим названием.
        public String toString() {
            return name;
        }
    }

    /** Идея подарка из каталога с условиями её подбора. */
    public static final class Idea {
        public final int id, minAge, maxAge, matches;
        public final String title, description, occasion, image;
        public final BigDecimal minPrice, maxPrice;
        public final boolean active;

        // matches — вычисленное количество совпавших интересов при подборе;
        // отдельного столбца matches в таблице gift_idea нет.
        public Idea(int id, String title, String description,
                    BigDecimal min, BigDecimal max,
                    int minAge, int maxAge, String occasion,
                    String image, boolean active, int matches) {
            this.id = id;
            this.title = title;
            this.description = description;
            minPrice = min;
            maxPrice = max;
            this.minAge = minAge;
            this.maxAge = maxAge;
            this.occasion = occasion;
            this.image = image;
            this.active = active;
            this.matches = matches;
        }

        // Текстовое представление идеи — её название.
        public String toString() {
            return title;
        }
    }
}
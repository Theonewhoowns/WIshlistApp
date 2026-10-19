-- Выполнить один раз после 01_schema.sql. Все демонстрационные пароли: DemoPass123!
-- PBKDF2-HMAC-SHA256, 600000 итераций, 32 байта; соль 16 байт; Base64.
-- Изображения пока NULL: приложение должно показывать заглушку.
BEGIN;
INSERT INTO "wishlistApp".app_user(username,password_hash,password_salt,password_iterations,role) VALUES
('admin','JIs7XyxrzGtBivnOzbjdU9CHTWYUKgHW+j12KZvnOVo=','3gtVR/h9nxSghgJsZcAlSQ==',600000,'ADMIN'),
('ivan','HqMrqQumZg2s3oUNkiSjonEgVyqxkW6cuan8qMEmmrU=','ognpXtYpm6BI9X80y+P2jw==',600000,'USER'),
('anna','0LIumN6Vhh3BVKgOQly6DKoTng3S1kYrZP4hSwgbzII=','1oKQbsln//Y6YtOih6ylCA==',600000,'USER'),
('alex','+Ivd2NYLUTJ8i/vvKMPGK2nLg8L8GLJHrnzYvzxlco8=','4Pd+j7lm5tgoShTwBcFFTA==',600000,'USER'),
('olga','S4r4Jyx5B3tCU0FLKW6kXW544lvWABR5IC6qi2Ixc08=','Yt3RVS8wvTMytqi7N3oU1w==',600000,'USER');

INSERT INTO "wishlistApp".recipient(recipient_id,owner_id,name,age) VALUES
(1,2,'Мама',48),(2,2,'Александр',24),(3,3,'Папа',55),
(4,4,'Друг',25),(5,5,'Сестра',16),(6,2,'Коллега',30);

INSERT INTO "wishlistApp".wishlist(owner_id,recipient_id,name) VALUES
(2,NULL,'Мои покупки'),(2,1,'Подарки маме'),(2,2,'День рождения Александра'),
(3,3,'Подарки папе'),(4,4,'Подарки другу'),(5,5,'Подарки сестре'),(2,6,'Подарки коллеге');

INSERT INTO "wishlistApp".interest(name) VALUES
('Музыка'),('Спорт'),('Чтение'),('Путешествия'),
('Рисование'),('Кулинария'),('Игры'),('Садоводство');

INSERT INTO "wishlistApp".gift_idea(title,description,min_price,max_price,min_age,max_age,occasion,is_active) VALUES
('Наушники','Беспроводные наушники',3000,8000,12,120,'ANY',TRUE),
('Книга','Подарочное издание',500,2000,6,120,'ANY',TRUE),
('Набор для рисования','Краски и кисти',1000,3500,6,120,'BIRTHDAY',TRUE),
('Набор для пикника','Термос и посуда',2500,6000,16,120,'ANY',TRUE),
('Кулинарный мастер-класс','Сертификат на занятие',2000,5000,18,120,'ANY',TRUE),
('Настольная игра','Игра для компании',1500,4000,10,120,'ANY',TRUE),
('Набор садовых инструментов','Инструменты для растений',1000,3000,16,120,'ANY',TRUE),
('Новогодний музыкальный набор','Тематический набор',1000,2500,12,120,'NEW_YEAR',FALSE);

INSERT INTO "wishlistApp".recipient_interest(owner_id,recipient_id,interest_id) VALUES
(2,1,6),(2,1,8),(2,2,1),(2,2,7),(3,3,3),(3,3,4),(4,4,2),(4,4,7),(5,5,5),(5,5,1),(2,6,3);
INSERT INTO "wishlistApp".gift_idea_interest(gift_idea_id,interest_id) VALUES
(1,1),(1,2),(2,3),(3,5),(4,4),(5,6),(6,7),(7,8),(8,1);

INSERT INTO "wishlistApp".wish_item(wishlist_id,source_idea_id,title,description,planned_price,is_purchased) VALUES
(1,NULL,'Монитор','Личная покупка',25000,FALSE),
(1,NULL,'Клавиатура',NULL,5000,TRUE),
(2,5,'Кулинарный мастер-класс','Подарок маме',5000,FALSE),
(2,7,'Набор садовых инструментов',NULL,3000,TRUE),
(3,1,'Наушники','Выбрать конкретную модель',8000,FALSE),
(4,2,'Книга','Подарочное издание',2000,FALSE),
(5,6,'Настольная игра',NULL,4000,FALSE),
(6,3,'Набор для рисования',NULL,3500,FALSE),
(7,2,'Книга для коллеги',NULL,2000,FALSE);
COMMIT;

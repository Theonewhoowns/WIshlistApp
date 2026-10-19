-- SQL Server 2012+. Тот же каталог и те же учётные записи, что в PostgreSQL.
-- Выполнить один раз, сразу после 01_schema.sql, до ручных вставок.
-- Логины: admin, ivan, anna, alex, olga; пароль: DemoPass123!
-- PBKDF2WithHmacSHA256, 600000 итераций, 32 байта; соль 16 байт; Base64.
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
 IF OBJECT_ID(N'wishlistApp.app_user',N'U') IS NULL
   THROW 51002,N'Сначала выполните 01_schema.sql в этой базе.',1;
 IF EXISTS(SELECT 1 FROM [wishlistApp].[app_user])
   THROW 51003,N'Демонстрационные пользователи уже существуют; не повторяйте заполнение.',1;
 IF EXISTS(SELECT 1 FROM sys.identity_columns
           WHERE object_id IN (OBJECT_ID(N'wishlistApp.app_user'),OBJECT_ID(N'wishlistApp.recipient'),
           OBJECT_ID(N'wishlistApp.wishlist'),OBJECT_ID(N'wishlistApp.gift_idea'),
           OBJECT_ID(N'wishlistApp.wish_item'),OBJECT_ID(N'wishlistApp.interest')) AND last_value IS NOT NULL)
   THROW 51004,N'Тестовые данные требуют свежей структуры с идентификаторами от 1.',1;
 BEGIN TRANSACTION;
INSERT INTO [wishlistApp].app_user(username,password_hash,password_salt,password_iterations,role) VALUES
(N'admin',N'JIs7XyxrzGtBivnOzbjdU9CHTWYUKgHW+j12KZvnOVo=',N'3gtVR/h9nxSghgJsZcAlSQ==',600000,N'ADMIN'),
(N'ivan',N'HqMrqQumZg2s3oUNkiSjonEgVyqxkW6cuan8qMEmmrU=',N'ognpXtYpm6BI9X80y+P2jw==',600000,N'USER'),
(N'anna',N'0LIumN6Vhh3BVKgOQly6DKoTng3S1kYrZP4hSwgbzII=',N'1oKQbsln//Y6YtOih6ylCA==',600000,N'USER'),
(N'alex',N'+Ivd2NYLUTJ8i/vvKMPGK2nLg8L8GLJHrnzYvzxlco8=',N'4Pd+j7lm5tgoShTwBcFFTA==',600000,N'USER'),
(N'olga',N'S4r4Jyx5B3tCU0FLKW6kXW544lvWABR5IC6qi2Ixc08=',N'Yt3RVS8wvTMytqi7N3oU1w==',600000,N'USER');

INSERT INTO [wishlistApp].recipient(recipient_id,owner_id,name,age) VALUES
(1,2,N'Мама',48),(2,2,N'Александр',24),(3,3,N'Папа',55),
(4,4,N'Друг',25),(5,5,N'Сестра',16),(6,2,N'Коллега',30);

INSERT INTO [wishlistApp].wishlist(owner_id,recipient_id,name) VALUES
(2,NULL,N'Мои покупки'),(2,1,N'Подарки маме'),(2,2,N'День рождения Александра'),
(3,3,N'Подарки папе'),(4,4,N'Подарки другу'),(5,5,N'Подарки сестре'),(2,6,N'Подарки коллеге');

INSERT INTO [wishlistApp].interest(name) VALUES
(N'Музыка'),(N'Спорт'),(N'Чтение'),(N'Путешествия'),
(N'Рисование'),(N'Кулинария'),(N'Игры'),(N'Садоводство');

INSERT INTO [wishlistApp].gift_idea(title,description,min_price,max_price,min_age,max_age,occasion,is_active) VALUES
(N'Наушники',N'Беспроводные наушники',3000,8000,12,120,N'ANY',1),
(N'Книга',N'Подарочное издание',500,2000,6,120,N'ANY',1),
(N'Набор для рисования',N'Краски и кисти',1000,3500,6,120,N'BIRTHDAY',1),
(N'Набор для пикника',N'Термос и посуда',2500,6000,16,120,N'ANY',1),
(N'Кулинарный мастер-класс',N'Сертификат на занятие',2000,5000,18,120,N'ANY',1),
(N'Настольная игра',N'Игра для компании',1500,4000,10,120,N'ANY',1),
(N'Набор садовых инструментов',N'Инструменты для растений',1000,3000,16,120,N'ANY',1),
(N'Новогодний музыкальный набор',N'Тематический набор',1000,2500,12,120,N'NEW_YEAR',0);

INSERT INTO [wishlistApp].recipient_interest(owner_id,recipient_id,interest_id) VALUES
(2,1,6),(2,1,8),(2,2,1),(2,2,7),(3,3,3),(3,3,4),(4,4,2),(4,4,7),(5,5,5),(5,5,1),(2,6,3);
INSERT INTO [wishlistApp].gift_idea_interest(gift_idea_id,interest_id) VALUES
(1,1),(1,2),(2,3),(3,5),(4,4),(5,6),(6,7),(7,8),(8,1);

INSERT INTO [wishlistApp].wish_item(wishlist_id,source_idea_id,title,description,planned_price,is_purchased) VALUES
(1,NULL,N'Монитор',N'Личная покупка',25000,0),
(1,NULL,N'Клавиатура',NULL,5000,1),
(2,5,N'Кулинарный мастер-класс',N'Подарок маме',5000,0),
(2,7,N'Набор садовых инструментов',NULL,3000,1),
(3,1,N'Наушники',N'Выбрать конкретную модель',8000,0),
(4,2,N'Книга',N'Подарочное издание',2000,0),
(5,6,N'Настольная игра',NULL,4000,0),
(6,3,N'Набор для рисования',NULL,3500,0),
(7,2,N'Книга для коллеги',NULL,2000,0);
COMMIT TRANSACTION;
 SELECT N'Тестовые данные добавлены' AS result;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

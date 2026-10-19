-- SQL Server: каждая отрицательная проверка выполняется в собственной
-- транзакции и откатывается, включая транзакции, испорченные THROW из триггера.
-- Запускать сразу после 02_seed.sql. В SSMS — весь файл F5.
-- В DBeaver — выделить весь файл и Ctrl+Enter: один SQL-пакет.
SET NOCOUNT ON;
IF @@TRANCOUNT<>0
 THROW 51020,N'Проверки требуют отсутствия открытой транзакции. Завершите её и включите Auto-commit.',1;
IF NOT EXISTS(SELECT 1 FROM [wishlistApp].app_user WHERE user_id=2 AND username='ivan')
 THROW 51021,N'Проверки рассчитаны на тестовые данные 02_seed.sql.',1;

DECLARE @was_xact_abort BIT = CASE WHEN (@@OPTIONS & 16384)=16384 THEN 1 ELSE 0 END;
SET XACT_ABORT OFF;
DECLARE @cases TABLE (id INT IDENTITY(1,1),test_name NVARCHAR(150),sql_text NVARCHAR(MAX),expected_error INT);
DECLARE @results TABLE (test_name NVARCHAR(150),result VARCHAR(4),error_number INT);
INSERT INTO @cases(test_name,sql_text,expected_error) VALUES
(N'Логин без учёта регистра',N'INSERT INTO [wishlistApp].app_user(username,password_hash,password_salt,password_iterations) VALUES (N''IVAN'',N''x'',N''s'',1)',2601),
(N'Пустое название',N'UPDATE [wishlistApp].wish_item SET title = N''   '' WHERE wish_item_id=1',547),
(N'Обязательное название',N'UPDATE [wishlistApp].wish_item SET title = NULL WHERE wish_item_id=1',515),
(N'Отрицательная стоимость',N'UPDATE [wishlistApp].wish_item SET planned_price = -1 WHERE wish_item_id=1',547),
(N'NaN вместо стоимости',N'UPDATE [wishlistApp].wish_item SET planned_price = N''NaN'' WHERE wish_item_id=1',8114),
(N'Возраст больше 120',N'UPDATE [wishlistApp].recipient SET age=121 WHERE recipient_id=1',547),
(N'Несуществующий список',N'UPDATE [wishlistApp].wish_item SET wishlist_id=-1 WHERE wish_item_id=1',547),
(N'Название списка у владельца',N'INSERT INTO [wishlistApp].wishlist(owner_id,name) VALUES (2,N''мои покупки'')',2601),
(N'Название интереса без учёта регистра',N'INSERT INTO [wishlistApp].interest(name) VALUES (N''музыка'')',2601),
(N'Повтор интереса получателя',N'INSERT INTO [wishlistApp].recipient_interest(owner_id,recipient_id,interest_id) VALUES (2,1,6)',2627),
(N'Повтор интереса идеи',N'INSERT INTO [wishlistApp].gift_idea_interest VALUES (1,1)',2627),
(N'Обратный диапазон цен',N'UPDATE [wishlistApp].gift_idea SET min_price=9000,max_price=100 WHERE gift_idea_id=1',547),
(N'Обратный диапазон возраста',N'UPDATE [wishlistApp].gift_idea SET min_age=40,max_age=20 WHERE gift_idea_id=1',547),
(N'Неизвестная роль',N'UPDATE [wishlistApp].app_user SET role=N''ROOT'' WHERE user_id=2',547),
(N'Неизвестный повод',N'UPDATE [wishlistApp].gift_idea SET occasion=N''UNKNOWN'' WHERE gift_idea_id=1',547),
(N'Чужой получатель при вставке',N'INSERT INTO [wishlistApp].wishlist(owner_id,recipient_id,name) VALUES (3,1,N''Чужой список'')',547),
(N'Чужой получатель при обновлении',N'UPDATE [wishlistApp].wishlist SET recipient_id=3 WHERE wishlist_id=2',547),
(N'Передача получателя другому владельцу',N'UPDATE [wishlistApp].recipient SET owner_id=3 WHERE owner_id=2 AND recipient_id=1',547),
(N'Удаление используемого интереса',N'DELETE FROM [wishlistApp].interest WHERE interest_id=1',547),
(N'Удаление получателя со списком',N'DELETE FROM [wishlistApp].recipient WHERE recipient_id=1',547),
(N'Пробел после названия',N'UPDATE [wishlistApp].wish_item SET title=N''Монитор '' WHERE wish_item_id=1',547),
(N'Логин с пробелом',N'UPDATE [wishlistApp].app_user SET username=''ivan '' WHERE user_id=2',547),
(N'Регистр кода роли',N'UPDATE [wishlistApp].app_user SET role=''user'' WHERE user_id=2',547),
(N'Пробел после кода роли',N'UPDATE [wishlistApp].app_user SET role=''USER '' WHERE user_id=2',547),
(N'Пакетная вставка: одна строка с чужим получателем',N'INSERT INTO [wishlistApp].wishlist(owner_id,recipient_id,name) VALUES (2,1,N''Тест пакет 1''),(3,1,N''Тест пакет 2'')',547),
(N'Пакетная смена владельца получателя',N'UPDATE [wishlistApp].recipient SET owner_id=CASE WHEN recipient_id=1 THEN owner_id ELSE 3 END WHERE owner_id=2 AND recipient_id IN(1,2)',547);
INSERT INTO @cases(test_name,sql_text,expected_error) VALUES
(N'Повтор номера у одного владельца',N'INSERT INTO [wishlistApp].recipient(recipient_id,owner_id,name,age) VALUES(1,2,N''Дубликат'',30)',2627),
(N'Интерес чужого получателя',N'INSERT INTO [wishlistApp].recipient_interest(owner_id,recipient_id,interest_id) VALUES(3,2,1)',547);
-- Без зависимых списков FK не мешает; проверяем именно запрет триггера.
INSERT INTO @cases(test_name,sql_text,expected_error) VALUES
(N'Передача получателя без зависимых записей',
 N'INSERT INTO [wishlistApp].recipient(recipient_id,owner_id,name,age) VALUES(1000000,2,N''Проверка триггера'',30); UPDATE [wishlistApp].recipient SET owner_id=3 WHERE owner_id=2 AND recipient_id=1000000',51011);
DECLARE @id INT=1,@last INT=(SELECT COUNT(*) FROM @cases),@name NVARCHAR(150),
 @sql NVARCHAR(MAX),@expected INT,@actual INT,@message NVARCHAR(2048);
BEGIN TRY
 WHILE @id<=@last
 BEGIN
  SELECT @name=test_name,@sql=sql_text,@expected=expected_error FROM @cases WHERE id=@id;
  SET @actual=NULL;
  BEGIN TRY
   BEGIN TRANSACTION;
   EXEC sys.sp_executesql @sql;
   ROLLBACK TRANSACTION;
  END TRY
  BEGIN CATCH
   SET @actual=ERROR_NUMBER();
   IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
   IF @actual<>@expected
   BEGIN
    SET @message=N'Тест: '+@name+N'; ожидалась ошибка '+CONVERT(NVARCHAR(12),@expected)
       +N', получена '+CONVERT(NVARCHAR(12),@actual)+N': '+ERROR_MESSAGE();
    PRINT @message;
    THROW;
   END;
  END CATCH;
  IF @actual IS NULL
  BEGIN
   SET @message=N'Не сработало ожидаемое ограничение: '+@name;
   THROW 51022,@message,1;
  END;
  INSERT INTO @results VALUES(@name,'PASS',@actual);
  SET @id+=1;
 END;

 -- Положительные проверки; добавленные строки затем откатываются.
 BEGIN TRANSACTION;
 DECLARE @list_id INT,@item_id INT;
 INSERT INTO [wishlistApp].wishlist(owner_id,name) VALUES(3,N'Мои покупки');
 SET @list_id=CONVERT(INT,SCOPE_IDENTITY());
 INSERT INTO [wishlistApp].wish_item(wishlist_id,title,planned_price)
 VALUES(@list_id,N'Проверка каскада',0);
 SET @item_id=CONVERT(INT,SCOPE_IDENTITY());
 DELETE FROM [wishlistApp].wishlist WHERE wishlist_id=@list_id;
 IF EXISTS(SELECT 1 FROM [wishlistApp].wish_item WHERE wish_item_id=@item_id)
   THROW 51023,N'Каскадное удаление не сработало.',1;
 IF NOT EXISTS(SELECT 1 FROM [wishlistApp].wishlist WHERE recipient_id IS NULL)
   OR NOT EXISTS(SELECT 1 FROM [wishlistApp].wish_item WHERE source_idea_id IS NULL)
   THROW 51024,N'Нет данных для проверки необязательных связей.',1;
 INSERT INTO [wishlistApp].recipient(recipient_id,owner_id,name,age) VALUES(1,3,N'Локальный номер 1',30);
 INSERT INTO [wishlistApp].recipient_interest(owner_id,recipient_id,interest_id) VALUES(3,1,1);
 INSERT INTO [wishlistApp].wishlist(owner_id,recipient_id,name) VALUES(3,1,N'Локальный получатель');
 ROLLBACK TRANSACTION;
 INSERT INTO @results VALUES(N'Одинаковые номера у разных владельцев','PASS',NULL);
 INSERT INTO @results VALUES(N'Одинаковые названия у разных владельцев','PASS',NULL),
 (N'Каскадное удаление желаний','PASS',NULL),(N'Необязательные связи','PASS',NULL);
 IF @was_xact_abort=1 SET XACT_ABORT ON;
 SELECT * FROM @results ORDER BY test_name;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 IF @was_xact_abort=1 SET XACT_ABORT ON;
 THROW;
END CATCH;

-- Ожидается 8 строк, по 5 или более записей в каждой таблице.
SELECT 'app_user' AS table_name,COUNT(*) AS row_count FROM [wishlistApp].app_user
UNION ALL SELECT 'recipient',COUNT(*) FROM [wishlistApp].recipient
UNION ALL SELECT 'wishlist',COUNT(*) FROM [wishlistApp].wishlist
UNION ALL SELECT 'wish_item',COUNT(*) FROM [wishlistApp].wish_item
UNION ALL SELECT 'gift_idea',COUNT(*) FROM [wishlistApp].gift_idea
UNION ALL SELECT 'interest',COUNT(*) FROM [wishlistApp].interest
UNION ALL SELECT 'recipient_interest',COUNT(*) FROM [wishlistApp].recipient_interest
UNION ALL SELECT 'gift_idea_interest',COUNT(*) FROM [wishlistApp].gift_idea_interest
ORDER BY table_name;

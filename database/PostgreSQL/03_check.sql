-- Выполнять после структуры и заполнения. Изменения тестов откатываются.
BEGIN;
CREATE TEMP TABLE test_results(test_name TEXT, result TEXT) ON COMMIT DROP;

-- Перехватываем только ожидаемую ошибку. Иная ошибка останавливает тест.
CREATE FUNCTION pg_temp.expect_error(test_name TEXT, statement TEXT, expected_state TEXT)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE caught BOOLEAN := FALSE;
BEGIN
 BEGIN
   EXECUTE statement;
 EXCEPTION WHEN OTHERS THEN
   IF SQLSTATE <> expected_state THEN RAISE; END IF;
   caught := TRUE;
 END;
 IF NOT caught THEN
   RAISE EXCEPTION 'Тест "%": ожидалась ошибка %, но запрос выполнен', test_name, expected_state;
 END IF;
 INSERT INTO test_results VALUES (test_name,'PASS');
END $$;

SELECT pg_temp.expect_error('Логин без учёта регистра',
 $$INSERT INTO "wishlistApp".app_user(username,password_hash,password_salt,password_iterations) VALUES ('IVAN','x','s',1)$$,'23505');
SELECT pg_temp.expect_error('Пустое название',
 $$UPDATE "wishlistApp".wish_item SET title = '   ' WHERE wish_item_id=1$$,'23514');
SELECT pg_temp.expect_error('Обязательное название',
 $$UPDATE "wishlistApp".wish_item SET title = NULL WHERE wish_item_id=1$$,'23502');
SELECT pg_temp.expect_error('Отрицательная стоимость',
 $$UPDATE "wishlistApp".wish_item SET planned_price = -1 WHERE wish_item_id=1$$,'23514');
SELECT pg_temp.expect_error('NaN вместо стоимости',
 $$UPDATE "wishlistApp".wish_item SET planned_price = 'NaN' WHERE wish_item_id=1$$,'23514');
SELECT pg_temp.expect_error('Возраст больше 120',
 $$UPDATE "wishlistApp".recipient SET age=121 WHERE recipient_id=1$$,'23514');
SELECT pg_temp.expect_error('Несуществующий список',
 $$UPDATE "wishlistApp".wish_item SET wishlist_id=-1 WHERE wish_item_id=1$$,'23503');
SELECT pg_temp.expect_error('Название списка у владельца',
 $$INSERT INTO "wishlistApp".wishlist(owner_id,name) VALUES (2,'мои покупки')$$,'23505');
SELECT pg_temp.expect_error('Название интереса без учёта регистра',
 $$INSERT INTO "wishlistApp".interest(name) VALUES ('музыка')$$,'23505');
SELECT pg_temp.expect_error('Повтор интереса получателя',
 $$INSERT INTO "wishlistApp".recipient_interest(owner_id,recipient_id,interest_id) VALUES (2,1,6)$$,'23505');
SELECT pg_temp.expect_error('Повтор интереса идеи',
 $$INSERT INTO "wishlistApp".gift_idea_interest VALUES (1,1)$$,'23505');
SELECT pg_temp.expect_error('Обратный диапазон цен',
 $$UPDATE "wishlistApp".gift_idea SET min_price=9000,max_price=100 WHERE gift_idea_id=1$$,'23514');
SELECT pg_temp.expect_error('Обратный диапазон возраста',
 $$UPDATE "wishlistApp".gift_idea SET min_age=40,max_age=20 WHERE gift_idea_id=1$$,'23514');
SELECT pg_temp.expect_error('Неизвестная роль',
 $$UPDATE "wishlistApp".app_user SET role='ROOT' WHERE user_id=2$$,'23514');
SELECT pg_temp.expect_error('Неизвестный повод',
 $$UPDATE "wishlistApp".gift_idea SET occasion='UNKNOWN' WHERE gift_idea_id=1$$,'23514');
SELECT pg_temp.expect_error('Чужой получатель при вставке',
 $$INSERT INTO "wishlistApp".wishlist(owner_id,recipient_id,name) VALUES (3,1,'Чужой список')$$,'23503');
SELECT pg_temp.expect_error('Чужой получатель при обновлении',
 $$UPDATE "wishlistApp".wishlist SET recipient_id=3 WHERE wishlist_id=2$$,'23503');
SELECT pg_temp.expect_error('Передача получателя другому владельцу',
 $$UPDATE "wishlistApp".recipient SET owner_id=3 WHERE recipient_id=1$$,'23514');
SELECT pg_temp.expect_error('Удаление используемого интереса',
 $$DELETE FROM "wishlistApp".interest WHERE interest_id=1$$,'23503');
SELECT pg_temp.expect_error('Удаление получателя со списком',
 $$DELETE FROM "wishlistApp".recipient WHERE recipient_id=1$$,'23503');

DO $$
DECLARE n INTEGER; list_id INTEGER; item_id INTEGER;
BEGIN
 -- Совпадающее название разрешено у другого пользователя.
 INSERT INTO "wishlistApp".wishlist(owner_id,name) VALUES (3,'Мои покупки') RETURNING wishlist_id INTO list_id;
 INSERT INTO test_results VALUES ('Одинаковые названия у разных владельцев','PASS');
 INSERT INTO "wishlistApp".wish_item(wishlist_id,title,planned_price)
 VALUES (list_id,'Проверка каскада',0) RETURNING wish_item_id INTO item_id;
 DELETE FROM "wishlistApp".wishlist WHERE wishlist_id=list_id;
 IF EXISTS(SELECT 1 FROM "wishlistApp".wish_item WHERE wish_item_id=item_id) THEN
   RAISE EXCEPTION 'Каскадное удаление не сработало';
 END IF;
 INSERT INTO test_results VALUES ('Каскадное удаление желаний','PASS');
 -- Личный список без получателя, ручное желание без исходной идеи уже есть.
 SELECT count(*) INTO n FROM "wishlistApp".wishlist WHERE recipient_id IS NULL;
 IF n=0 THEN RAISE EXCEPTION 'Нет личного списка'; END IF;
 SELECT count(*) INTO n FROM "wishlistApp".wish_item WHERE source_idea_id IS NULL;
 IF n=0 THEN RAISE EXCEPTION 'Нет ручного желания'; END IF;
 INSERT INTO test_results VALUES ('Необязательные связи','PASS');
END $$;

-- Один локальный номер может существовать у разных владельцев.
DO $$
BEGIN
 INSERT INTO "wishlistApp".recipient(recipient_id,owner_id,name,age) VALUES(1,3,'Локальный номер 1',30);
 INSERT INTO "wishlistApp".recipient_interest(owner_id,recipient_id,interest_id) VALUES(3,1,1);
 INSERT INTO "wishlistApp".wishlist(owner_id,recipient_id,name) VALUES(3,1,'Локальный получатель');
 INSERT INTO test_results VALUES ('Одинаковые номера у разных владельцев','PASS');
END $$;
SELECT pg_temp.expect_error('Повтор номера у одного владельца',
 $$INSERT INTO "wishlistApp".recipient(recipient_id,owner_id,name,age) VALUES(1,2,'Дубликат',30)$$,'23505');
SELECT pg_temp.expect_error('Интерес чужого получателя',
 $$INSERT INTO "wishlistApp".recipient_interest(owner_id,recipient_id,interest_id) VALUES(3,2,1)$$,'23503');

SELECT * FROM test_results ORDER BY test_name;
-- Тестовые изменения откатываются. Значения последовательностей могут иметь пропуски.
ROLLBACK;

-- Должно быть 8 строк, в каждой не менее 5 записей.
SELECT 'app_user' AS table_name,count(*) AS row_count FROM "wishlistApp".app_user
UNION ALL SELECT 'recipient',count(*) FROM "wishlistApp".recipient
UNION ALL SELECT 'wishlist',count(*) FROM "wishlistApp".wishlist
UNION ALL SELECT 'wish_item',count(*) FROM "wishlistApp".wish_item
UNION ALL SELECT 'gift_idea',count(*) FROM "wishlistApp".gift_idea
UNION ALL SELECT 'interest',count(*) FROM "wishlistApp".interest
UNION ALL SELECT 'recipient_interest',count(*) FROM "wishlistApp".recipient_interest
UNION ALL SELECT 'gift_idea_interest',count(*) FROM "wishlistApp".gift_idea_interest
ORDER BY table_name;

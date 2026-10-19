-- PostgreSQL 12+. Выполнять целиком в существующей базе postgres.
-- Скрипт для первого запуска; существующие данные не удаляет.
BEGIN;

CREATE SCHEMA "wishlistApp";

CREATE TABLE "wishlistApp".app_user (
 user_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 username VARCHAR(30) NOT NULL,
 password_hash VARCHAR(255) NOT NULL,
 password_salt VARCHAR(100) NOT NULL,
 password_iterations INTEGER NOT NULL,
 role VARCHAR(10) NOT NULL DEFAULT 'USER',
 CONSTRAINT ck_user_username CHECK (username ~ '^[A-Za-z0-9_]{3,30}$'),
 CONSTRAINT ck_user_hash CHECK (btrim(password_hash) <> ''),
 CONSTRAINT ck_user_salt CHECK (btrim(password_salt) <> ''),
 CONSTRAINT ck_user_iterations CHECK (password_iterations > 0),
 CONSTRAINT ck_user_role CHECK (role IN ('USER', 'ADMIN'))
);
CREATE UNIQUE INDEX uk_app_user_username ON "wishlistApp".app_user (lower(username));

CREATE TABLE "wishlistApp".recipient (
 recipient_id INTEGER NOT NULL,
 owner_id INTEGER NOT NULL REFERENCES "wishlistApp".app_user(user_id) ON DELETE NO ACTION,
 name VARCHAR(100) NOT NULL,
 age SMALLINT NOT NULL,
 CONSTRAINT ck_recipient_name CHECK (name = btrim(name) AND name <> ''),
 CONSTRAINT ck_recipient_age CHECK (age BETWEEN 0 AND 120),
 PRIMARY KEY(owner_id,recipient_id),
 CONSTRAINT ck_recipient_id CHECK (recipient_id>0)
);

CREATE TABLE "wishlistApp".wishlist (
 wishlist_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 owner_id INTEGER NOT NULL REFERENCES "wishlistApp".app_user(user_id) ON DELETE NO ACTION,
 recipient_id INTEGER,
 CONSTRAINT fk_wishlist_recipient FOREIGN KEY(owner_id,recipient_id)
 REFERENCES "wishlistApp".recipient(owner_id,recipient_id) ON DELETE NO ACTION,
 name VARCHAR(100) NOT NULL,
 CONSTRAINT ck_wishlist_name CHECK (name = btrim(name) AND name <> '')
);
CREATE UNIQUE INDEX uk_wishlist_owner_name ON "wishlistApp".wishlist(owner_id, lower(name));
CREATE INDEX ix_wishlist_recipient ON "wishlistApp".wishlist(owner_id,recipient_id);

CREATE TABLE "wishlistApp".gift_idea (
 gift_idea_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 title VARCHAR(150) NOT NULL,
 description VARCHAR(2000),
 min_price NUMERIC(12,2) NOT NULL,
 max_price NUMERIC(12,2) NOT NULL,
 min_age SMALLINT NOT NULL,
 max_age SMALLINT NOT NULL,
 occasion VARCHAR(20) NOT NULL DEFAULT 'ANY',
 image_path VARCHAR(500),
 is_active BOOLEAN NOT NULL DEFAULT TRUE,
 CONSTRAINT ck_idea_title CHECK (title = btrim(title) AND title <> ''),
 CONSTRAINT ck_idea_price CHECK (min_price >= 0 AND max_price >= min_price AND max_price <> 'NaN'::numeric),
 CONSTRAINT ck_idea_age CHECK (min_age >= 0 AND max_age >= min_age AND max_age <= 120),
 CONSTRAINT ck_idea_occasion CHECK (occasion IN ('ANY','BIRTHDAY','NEW_YEAR','MARCH_8','FEB_23','ANNIVERSARY','OTHER'))
);

CREATE TABLE "wishlistApp".wish_item (
 wish_item_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 wishlist_id INTEGER NOT NULL REFERENCES "wishlistApp".wishlist(wishlist_id) ON DELETE CASCADE,
 source_idea_id INTEGER REFERENCES "wishlistApp".gift_idea(gift_idea_id) ON DELETE NO ACTION,
 title VARCHAR(150) NOT NULL,
 description VARCHAR(2000),
 planned_price NUMERIC(12,2) NOT NULL,
 image_path VARCHAR(500),
 is_purchased BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT ck_item_title CHECK (title = btrim(title) AND title <> ''),
 CONSTRAINT ck_item_price CHECK (planned_price >= 0 AND planned_price <> 'NaN'::numeric)
);
CREATE INDEX ix_wish_item_wishlist ON "wishlistApp".wish_item(wishlist_id);
CREATE INDEX ix_wish_item_source ON "wishlistApp".wish_item(source_idea_id);

CREATE TABLE "wishlistApp".interest (
 interest_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 CONSTRAINT ck_interest_name CHECK (name = btrim(name) AND name <> '')
);
CREATE UNIQUE INDEX uk_interest_name ON "wishlistApp".interest(lower(name));

CREATE TABLE "wishlistApp".recipient_interest (
 owner_id INTEGER NOT NULL,
 recipient_id INTEGER NOT NULL,
 interest_id INTEGER NOT NULL REFERENCES "wishlistApp".interest(interest_id) ON DELETE NO ACTION,
 PRIMARY KEY (owner_id, recipient_id, interest_id),
 CONSTRAINT fk_recipient_interest_recipient FOREIGN KEY(owner_id,recipient_id)
 REFERENCES "wishlistApp".recipient(owner_id,recipient_id) ON DELETE CASCADE
);
CREATE INDEX ix_recipient_interest_interest ON "wishlistApp".recipient_interest(interest_id);

CREATE TABLE "wishlistApp".gift_idea_interest (
 gift_idea_id INTEGER NOT NULL REFERENCES "wishlistApp".gift_idea(gift_idea_id) ON DELETE CASCADE,
 interest_id INTEGER NOT NULL REFERENCES "wishlistApp".interest(interest_id) ON DELETE NO ACTION,
 PRIMARY KEY (gift_idea_id, interest_id)
);
CREATE INDEX ix_gift_idea_interest_interest ON "wishlistApp".gift_idea_interest(interest_id);

-- Получатель принадлежит создавшему его пользователю; передача не предусмотрена.
CREATE FUNCTION "wishlistApp".keep_recipient_owner() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.owner_id <> OLD.owner_id THEN
   RAISE EXCEPTION 'Изменение владельца получателя не предусмотрено'
     USING ERRCODE = '23514', CONSTRAINT = 'ck_recipient_owner_immutable';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tr_recipient_owner BEFORE UPDATE ON "wishlistApp".recipient
 FOR EACH ROW EXECUTE FUNCTION "wishlistApp".keep_recipient_owner();

COMMENT ON SCHEMA "wishlistApp" IS 'Списки желаний и подбор подарков';
COMMENT ON TABLE "wishlistApp".app_user IS 'Учётные записи приложения; не роли PostgreSQL';
COMMENT ON COLUMN "wishlistApp".app_user.password_hash IS 'PBKDF2WithHmacSHA256, 32 байта в Base64';
COMMENT ON COLUMN "wishlistApp".app_user.password_salt IS 'Индивидуальная случайная соль, 16 байт в Base64';
COMMENT ON TABLE "wishlistApp".recipient IS 'Получатели; ключ (owner_id,recipient_id), номер получателя уникален только у владельца';
COMMENT ON COLUMN "wishlistApp".recipient.recipient_id IS 'Положительный локальный номер, не глобальный идентификатор';
COMMENT ON TABLE "wishlistApp".wishlist IS 'Личный список либо список для одного получателя';
COMMENT ON TABLE "wishlistApp".gift_idea IS 'Общий каталог; устаревшие идеи деактивируются';
COMMENT ON TABLE "wishlistApp".wish_item IS 'Самостоятельная позиция списка, при создании из идеи данные копируются';
COMMENT ON TABLE "wishlistApp".interest IS 'Справочник интересов';
COMMENT ON TABLE "wishlistApp".recipient_interest IS 'Назначения интересов получателям';
COMMENT ON TABLE "wishlistApp".gift_idea_interest IS 'Назначения интересов идеям';

COMMIT;

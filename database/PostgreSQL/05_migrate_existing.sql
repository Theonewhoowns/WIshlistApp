-- Только для уже созданной ПЕРВОЙ версии схемы wishlistApp.
-- Не выполнять после нового 01_schema.sql. Имена и содержимое записей сохраняются.
-- Ключи меняются в одной транзакции. На время миграции закройте редактирование данных.
BEGIN;
LOCK TABLE "wishlistApp".recipient, "wishlistApp".wishlist,
 "wishlistApp".recipient_interest IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE x RECORD;
BEGIN
 IF EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema='wishlistApp' AND table_name='recipient_interest' AND column_name='owner_id') THEN
   RAISE EXCEPTION 'Миграция уже выполнена либо структура отличается от первой версии';
 END IF;
 IF EXISTS(SELECT 1 FROM "wishlistApp".wishlist w JOIN "wishlistApp".recipient r
           ON r.recipient_id=w.recipient_id WHERE r.owner_id<>w.owner_id) THEN
   RAISE EXCEPTION 'Обнаружен список с чужим получателем; сначала исправьте данные';
 END IF;
 -- Убираем только ссылки двух таблиц на старый глобальный ключ получателя.
 FOR x IN SELECT conrelid::regclass AS tbl, conname FROM pg_constraint
          WHERE contype='f' AND confrelid='"wishlistApp".recipient'::regclass
          AND conrelid IN ('"wishlistApp".wishlist'::regclass,'"wishlistApp".recipient_interest'::regclass)
 LOOP
   EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', x.tbl,x.conname);
 END LOOP;
END $$;

DROP TRIGGER tr_wishlist_owner ON "wishlistApp".wishlist;
DROP FUNCTION "wishlistApp".check_wishlist_owner();

ALTER TABLE "wishlistApp".recipient_interest ADD COLUMN owner_id INTEGER;
UPDATE "wishlistApp".recipient_interest ri SET owner_id=r.owner_id
FROM "wishlistApp".recipient r WHERE r.recipient_id=ri.recipient_id;
ALTER TABLE "wishlistApp".recipient_interest ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE "wishlistApp".recipient_interest DROP CONSTRAINT recipient_interest_pkey;
ALTER TABLE "wishlistApp".recipient_interest ADD PRIMARY KEY(owner_id,recipient_id,interest_id);

ALTER TABLE "wishlistApp".recipient DROP CONSTRAINT recipient_pkey;
ALTER TABLE "wishlistApp".recipient ALTER COLUMN recipient_id DROP IDENTITY;
ALTER TABLE "wishlistApp".recipient ADD PRIMARY KEY(owner_id,recipient_id);
ALTER TABLE "wishlistApp".recipient ADD CONSTRAINT ck_recipient_id CHECK(recipient_id>0);
DROP INDEX "wishlistApp".ix_recipient_owner;
DROP INDEX "wishlistApp".ix_wishlist_recipient;
CREATE INDEX ix_wishlist_recipient ON "wishlistApp".wishlist(owner_id,recipient_id);

ALTER TABLE "wishlistApp".wishlist ADD CONSTRAINT fk_wishlist_recipient
 FOREIGN KEY(owner_id,recipient_id) REFERENCES "wishlistApp".recipient(owner_id,recipient_id) ON DELETE NO ACTION;
ALTER TABLE "wishlistApp".recipient_interest ADD CONSTRAINT fk_recipient_interest_recipient
 FOREIGN KEY(owner_id,recipient_id) REFERENCES "wishlistApp".recipient(owner_id,recipient_id) ON DELETE CASCADE;

COMMENT ON TABLE "wishlistApp".recipient IS 'Получатели; ключ (owner_id,recipient_id), номер уникален только у владельца';
COMMENT ON COLUMN "wishlistApp".recipient.recipient_id IS 'Положительный локальный номер, не глобальный идентификатор';
COMMIT;

-- Миграция ПЕРВОЙ версии схемы, без повторного заполнения.
-- SQL Server 2012+. Весь файл одним пакетом: SSMS F5 / DBeaver выделить всё Ctrl+Enter.
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRY
 IF DB_NAME() IN (N'master',N'model',N'msdb',N'tempdb')
   THROW 51000,N'Выберите учебную пользовательскую базу.',1;
 IF COL_LENGTH(N'wishlistApp.recipient_interest',N'owner_id') IS NOT NULL
   THROW 51030,N'Миграция уже выполнена либо структура отличается от первой версии.',1;
 IF COLUMNPROPERTY(OBJECT_ID(N'wishlistApp.recipient'),N'recipient_id','IsIdentity')<>1
   THROW 51031,N'Ожидалась первая версия recipient с IDENTITY.',1;
 BEGIN TRANSACTION;
 DECLARE @lock_count BIGINT;
 SELECT @lock_count=COUNT_BIG(*) FROM [wishlistApp].recipient WITH(TABLOCKX,HOLDLOCK);
 SELECT @lock_count=COUNT_BIG(*) FROM [wishlistApp].wishlist WITH(TABLOCKX,HOLDLOCK);
 SELECT @lock_count=COUNT_BIG(*) FROM [wishlistApp].recipient_interest WITH(TABLOCKX,HOLDLOCK);
 IF EXISTS(SELECT 1 FROM [wishlistApp].wishlist w JOIN [wishlistApp].recipient r
           ON r.recipient_id=w.recipient_id WHERE r.owner_id<>w.owner_id)
   THROW 51032,N'Обнаружен список с чужим получателем.',1;
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].wishlist DROP CONSTRAINT fk_wishlist_recipient';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient_interest DROP CONSTRAINT fk_recipient_interest_recipient';
 EXEC sys.sp_executesql N'DROP TRIGGER [wishlistApp].tr_wishlist_owner';
 EXEC sys.sp_executesql N'DROP TRIGGER [wishlistApp].tr_recipient_owner';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient_interest ADD owner_id INT NULL';
 EXEC sys.sp_executesql N'UPDATE ri SET owner_id=r.owner_id FROM [wishlistApp].recipient_interest ri JOIN [wishlistApp].recipient r ON r.recipient_id=ri.recipient_id';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient_interest ALTER COLUMN owner_id INT NOT NULL';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient_interest DROP CONSTRAINT pk_recipient_interest';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient_interest ADD CONSTRAINT pk_recipient_interest PRIMARY KEY(owner_id,recipient_id,interest_id)';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient DROP CONSTRAINT pk_recipient';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient ADD recipient_id_new INT NULL';
 EXEC sys.sp_executesql N'UPDATE [wishlistApp].recipient SET recipient_id_new=recipient_id';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient ALTER COLUMN recipient_id_new INT NOT NULL';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient DROP COLUMN recipient_id';
 EXEC sys.sp_executesql N'EXEC sys.sp_rename N''wishlistApp.recipient.recipient_id_new'', N''recipient_id'', N''COLUMN''';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient ADD CONSTRAINT pk_recipient PRIMARY KEY(owner_id,recipient_id)';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient ADD CONSTRAINT ck_recipient_id CHECK(recipient_id>0)';
 EXEC sys.sp_executesql N'DROP INDEX ix_recipient_owner ON [wishlistApp].recipient';
 EXEC sys.sp_executesql N'DROP INDEX ix_wishlist_recipient ON [wishlistApp].wishlist';
 EXEC sys.sp_executesql N'CREATE INDEX ix_wishlist_recipient ON [wishlistApp].wishlist(owner_id,recipient_id)';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].wishlist ADD CONSTRAINT fk_wishlist_recipient FOREIGN KEY(owner_id,recipient_id) REFERENCES [wishlistApp].recipient(owner_id,recipient_id) ON DELETE NO ACTION';
 EXEC sys.sp_executesql N'ALTER TABLE [wishlistApp].recipient_interest ADD CONSTRAINT fk_recipient_interest_recipient FOREIGN KEY(owner_id,recipient_id) REFERENCES [wishlistApp].recipient(owner_id,recipient_id) ON DELETE CASCADE';
 EXEC sys.sp_executesql N'CREATE TRIGGER [wishlistApp].tr_recipient_owner
ON [wishlistApp].recipient AFTER UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF UPDATE(owner_id) AND EXISTS(SELECT 1 FROM inserted)
   THROW 51011,N''Изменение владельца получателя не предусмотрено'',1;
END;';
 COMMIT TRANSACTION;
 SELECT N'Миграция завершена; записи сохранены' AS result;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

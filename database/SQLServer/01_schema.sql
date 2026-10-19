-- SQL Server 2012+. Выберите свою пользовательскую базу в SSMS / DBeaver.
-- Новая схема wishlistApp; восемь таблиц. Существующие объекты не удаляются.
-- Весь файл является одним пакетом, GO не требуется.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

BEGIN TRY
 IF DB_NAME() IN (N'master',N'model',N'msdb',N'tempdb')
   THROW 51000, N'Выберите пользовательскую учебную базу, а не системную.', 1;
 IF SCHEMA_ID(N'wishlistApp') IS NOT NULL
   THROW 51001, N'Схема wishlistApp уже существует. Повторный запуск не требуется.', 1;
 BEGIN TRANSACTION;
 EXEC(N'CREATE SCHEMA [wishlistApp] AUTHORIZATION [dbo]');

 CREATE TABLE [wishlistApp].[app_user] (
  user_id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_app_user PRIMARY KEY,
  username VARCHAR(30) COLLATE Latin1_General_100_CI_AS NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  password_salt VARCHAR(100) NOT NULL,
  password_iterations INT NOT NULL,
  role VARCHAR(10) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT df_user_role DEFAULT 'USER',
  CONSTRAINT ck_user_username CHECK (
    DATALENGTH(username) BETWEEN 3 AND 30
    AND username COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Za-z0-9_]%'),
  CONSTRAINT ck_user_hash CHECK (LEN(LTRIM(RTRIM(password_hash)))>0),
  CONSTRAINT ck_user_salt CHECK (LEN(LTRIM(RTRIM(password_salt)))>0),
  CONSTRAINT ck_user_iterations CHECK (password_iterations>0),
  CONSTRAINT ck_user_role CHECK (role IN ('USER','ADMIN') AND DATALENGTH(role)=LEN(role))
 );
 CREATE UNIQUE INDEX uk_app_user_username ON [wishlistApp].[app_user](username);

 CREATE TABLE [wishlistApp].[recipient] (
  recipient_id INT NOT NULL,
  owner_id INT NOT NULL,
  name NVARCHAR(100) COLLATE Cyrillic_General_100_CI_AS NOT NULL,
  age SMALLINT NOT NULL,
  CONSTRAINT fk_recipient_owner FOREIGN KEY(owner_id) REFERENCES [wishlistApp].[app_user](user_id) ON DELETE NO ACTION,
  CONSTRAINT ck_recipient_name CHECK (LEN(name)>0 AND DATALENGTH(name)=DATALENGTH(LTRIM(RTRIM(name)))),
  CONSTRAINT ck_recipient_age CHECK (age BETWEEN 0 AND 120),
  CONSTRAINT pk_recipient PRIMARY KEY(owner_id,recipient_id),
  CONSTRAINT ck_recipient_id CHECK (recipient_id>0)
 );

 CREATE TABLE [wishlistApp].[wishlist] (
  wishlist_id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_wishlist PRIMARY KEY,
  owner_id INT NOT NULL,
  recipient_id INT NULL,
  name NVARCHAR(100) COLLATE Cyrillic_General_100_CI_AS NOT NULL,
  CONSTRAINT fk_wishlist_owner FOREIGN KEY(owner_id) REFERENCES [wishlistApp].[app_user](user_id) ON DELETE NO ACTION,
  CONSTRAINT fk_wishlist_recipient FOREIGN KEY(owner_id,recipient_id) REFERENCES [wishlistApp].[recipient](owner_id,recipient_id) ON DELETE NO ACTION,
  CONSTRAINT ck_wishlist_name CHECK (LEN(name)>0 AND DATALENGTH(name)=DATALENGTH(LTRIM(RTRIM(name))))
 );
 CREATE UNIQUE INDEX uk_wishlist_owner_name ON [wishlistApp].[wishlist](owner_id,name);
 CREATE INDEX ix_wishlist_recipient ON [wishlistApp].[wishlist](owner_id,recipient_id);

 CREATE TABLE [wishlistApp].[gift_idea] (
  gift_idea_id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_gift_idea PRIMARY KEY,
  title NVARCHAR(150) COLLATE Cyrillic_General_100_CI_AS NOT NULL,
  description NVARCHAR(2000) NULL,
  min_price DECIMAL(12,2) NOT NULL,
  max_price DECIMAL(12,2) NOT NULL,
  min_age SMALLINT NOT NULL,
  max_age SMALLINT NOT NULL,
  occasion VARCHAR(20) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT df_idea_occasion DEFAULT 'ANY',
  image_path NVARCHAR(500) NULL,
  is_active BIT NOT NULL CONSTRAINT df_idea_active DEFAULT 1,
  CONSTRAINT ck_idea_title CHECK (LEN(title)>0 AND DATALENGTH(title)=DATALENGTH(LTRIM(RTRIM(title)))),
  CONSTRAINT ck_idea_price CHECK (min_price>=0 AND max_price>=min_price),
  CONSTRAINT ck_idea_age CHECK (min_age>=0 AND max_age>=min_age AND max_age<=120),
  CONSTRAINT ck_idea_occasion CHECK (occasion IN ('ANY','BIRTHDAY','NEW_YEAR','MARCH_8','FEB_23','ANNIVERSARY','OTHER') AND DATALENGTH(occasion)=LEN(occasion))
 );

 CREATE TABLE [wishlistApp].[wish_item] (
  wish_item_id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_wish_item PRIMARY KEY,
  wishlist_id INT NOT NULL,
  source_idea_id INT NULL,
  title NVARCHAR(150) COLLATE Cyrillic_General_100_CI_AS NOT NULL,
  description NVARCHAR(2000) NULL,
  planned_price DECIMAL(12,2) NOT NULL,
  image_path NVARCHAR(500) NULL,
  is_purchased BIT NOT NULL CONSTRAINT df_item_purchased DEFAULT 0,
  CONSTRAINT fk_wish_item_wishlist FOREIGN KEY(wishlist_id) REFERENCES [wishlistApp].[wishlist](wishlist_id) ON DELETE CASCADE,
  CONSTRAINT fk_wish_item_source FOREIGN KEY(source_idea_id) REFERENCES [wishlistApp].[gift_idea](gift_idea_id) ON DELETE NO ACTION,
  CONSTRAINT ck_item_title CHECK (LEN(title)>0 AND DATALENGTH(title)=DATALENGTH(LTRIM(RTRIM(title)))),
  CONSTRAINT ck_item_price CHECK (planned_price>=0)
 );
 CREATE INDEX ix_wish_item_wishlist ON [wishlistApp].[wish_item](wishlist_id);
 CREATE INDEX ix_wish_item_source ON [wishlistApp].[wish_item](source_idea_id);

 CREATE TABLE [wishlistApp].[interest] (
  interest_id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_interest PRIMARY KEY,
  name NVARCHAR(100) COLLATE Cyrillic_General_100_CI_AS NOT NULL,
  CONSTRAINT ck_interest_name CHECK (LEN(name)>0 AND DATALENGTH(name)=DATALENGTH(LTRIM(RTRIM(name))))
 );
 CREATE UNIQUE INDEX uk_interest_name ON [wishlistApp].[interest](name);

 CREATE TABLE [wishlistApp].[recipient_interest] (
  owner_id INT NOT NULL,
  recipient_id INT NOT NULL,
  interest_id INT NOT NULL,
  CONSTRAINT pk_recipient_interest PRIMARY KEY(owner_id,recipient_id,interest_id),
  CONSTRAINT fk_recipient_interest_recipient FOREIGN KEY(owner_id,recipient_id) REFERENCES [wishlistApp].[recipient](owner_id,recipient_id) ON DELETE CASCADE,
  CONSTRAINT fk_recipient_interest_interest FOREIGN KEY(interest_id) REFERENCES [wishlistApp].[interest](interest_id) ON DELETE NO ACTION
 );
 CREATE INDEX ix_recipient_interest_interest ON [wishlistApp].[recipient_interest](interest_id);

 CREATE TABLE [wishlistApp].[gift_idea_interest] (
  gift_idea_id INT NOT NULL,
  interest_id INT NOT NULL,
  CONSTRAINT pk_gift_idea_interest PRIMARY KEY(gift_idea_id,interest_id),
  CONSTRAINT fk_gift_idea_interest_idea FOREIGN KEY(gift_idea_id) REFERENCES [wishlistApp].[gift_idea](gift_idea_id) ON DELETE CASCADE,
  CONSTRAINT fk_gift_idea_interest_interest FOREIGN KEY(interest_id) REFERENCES [wishlistApp].[interest](interest_id) ON DELETE NO ACTION
 );
 CREATE INDEX ix_gift_idea_interest_interest ON [wishlistApp].[gift_idea_interest](interest_id);

 -- inserted может содержать несколько строк; owner_id не включается в UPDATE.
 EXEC sys.sp_executesql N'
 CREATE TRIGGER [wishlistApp].[tr_recipient_owner]
 ON [wishlistApp].[recipient] AFTER UPDATE AS
 BEGIN
  SET NOCOUNT ON;
  IF UPDATE(owner_id) AND EXISTS(SELECT 1 FROM inserted)
    THROW 51011, N''Изменение владельца получателя не предусмотрено'', 1;
 END;';

 COMMIT TRANSACTION;
 SELECT N'Схема wishlistApp: 8 таблиц созданы' AS result;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;

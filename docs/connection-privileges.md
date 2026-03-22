# 连接参数与权限说明

## 第一步

创建同步任务前，先填写以下连接参数：

- `host` 或 `ip`：源库或目标库地址
- `port`：数据库监听端口
- `user`：专用同步账号，生产环境不要直接使用业务超级账号
- `password`：同步账号密码
- `databaseName`：源库名或目标库名
- `schemaName`：使用 schema 的数据库需要填写 schema 名

推荐做法：

- 每个源库单独创建同步账号
- 只授予当前同步模式所需的最小权限
- 纯全量导出账号优先使用只读权限
- TiDB 目标端建议单独创建导入/写入账号

## 源端数据库权限

### MySQL

适用场景：

- 全量导出
- 基于 Debezium MySQL Connector 的 binlog 增量 CDC

说明：

- 全量导出工具使用 `dumpling`
- 增量采集工具使用 Debezium MySQL Connector

本项目需要的权限：

- `SELECT`
- `RELOAD`
- `SHOW DATABASES`
- `REPLICATION SLAVE`
- `REPLICATION CLIENT`
- `LOCK TABLES` when the environment cannot use a global read lock during snapshot

授权示例：

```sql
CREATE USER 'sync_user'@'%' IDENTIFIED BY 'StrongPassword';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'sync_user'@'%';
GRANT LOCK TABLES ON *.* TO 'sync_user'@'%';
FLUSH PRIVILEGES;
```

### Oracle

适用场景：

- 通过 `sqluldr2` 做全量导出
- 通过 Oracle LogMiner 做增量 CDC

本项目需要的权限：

- `CREATE SESSION`
- `SET CONTAINER` for CDB deployments
- `FLASHBACK ANY TABLE`
- `SELECT ANY TABLE`
- `SELECT_CATALOG_ROLE`
- `EXECUTE_CATALOG_ROLE`
- `SELECT ANY TRANSACTION`
- `LOGMINING`
- `CREATE TABLE`
- `LOCK ANY TABLE`
- `CREATE SEQUENCE`
- `EXECUTE ON DBMS_LOGMNR`
- `EXECUTE ON DBMS_LOGMNR_D`
- `SELECT ON V_$...` views used by LogMiner

授权示例：

```sql
CREATE USER c##dbzuser IDENTIFIED BY "StrongPassword"
  DEFAULT TABLESPACE logminer_tbs
  QUOTA UNLIMITED ON logminer_tbs
  CONTAINER=ALL;

GRANT CREATE SESSION TO c##dbzuser CONTAINER=ALL;
GRANT SET CONTAINER TO c##dbzuser CONTAINER=ALL;
GRANT FLASHBACK ANY TABLE TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ANY TABLE TO c##dbzuser CONTAINER=ALL;
GRANT SELECT_CATALOG_ROLE TO c##dbzuser CONTAINER=ALL;
GRANT EXECUTE_CATALOG_ROLE TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ANY TRANSACTION TO c##dbzuser CONTAINER=ALL;
GRANT LOGMINING TO c##dbzuser CONTAINER=ALL;
GRANT CREATE TABLE TO c##dbzuser CONTAINER=ALL;
GRANT LOCK ANY TABLE TO c##dbzuser CONTAINER=ALL;
GRANT CREATE SEQUENCE TO c##dbzuser CONTAINER=ALL;
GRANT EXECUTE ON DBMS_LOGMNR TO c##dbzuser CONTAINER=ALL;
GRANT EXECUTE ON DBMS_LOGMNR_D TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$DATABASE TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$LOG TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$LOG_HISTORY TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$LOGMNR_LOGS TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$LOGMNR_CONTENTS TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$LOGMNR_PARAMETERS TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$LOGFILE TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$ARCHIVED_LOG TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$ARCHIVE_DEST_STATUS TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$TRANSACTION TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$MYSTAT TO c##dbzuser CONTAINER=ALL;
GRANT SELECT ON V_$STATNAME TO c##dbzuser CONTAINER=ALL;
```

### SQL Server

适用场景：

- 全量导出
- 基于 SQL Server CDC 表的增量同步

本项目需要的权限：

- 管理员先为源库开启 CDC
- 管理员先为需要同步的表开启 CDC
- 同步账号需要源表 `SELECT`
- 同步账号需要读取 CDC 变更表的权限，通常通过 `sp_cdc_enable_table` 中的 gating role 赋予

管理员开启 CDC 示例：

```sql
USE MyDB;
GO
EXEC sys.sp_cdc_enable_db;
GO

EXEC sys.sp_cdc_enable_table
  @source_schema = N'dbo',
  @source_name   = N'Orders',
  @role_name     = N'cdc_reader',
  @supports_net_changes = 0;
GO
```

同步账号授权示例：

```sql
USE MyDB;
GO
CREATE LOGIN sync_login WITH PASSWORD = 'StrongPassword';
CREATE USER sync_user FOR LOGIN sync_login;
GRANT SELECT ON dbo.Orders TO sync_user;
EXEC sp_addrolemember N'cdc_reader', N'sync_user';
GO
```

### PostgreSQL

适用场景：

- 全量导出
- 逻辑复制增量同步

本项目需要的权限：

- `LOGIN`
- `REPLICATION`
- 初始全量需要源表 `SELECT`
- 若连接器自动创建 publication，需要对应对象创建权限
- `pg_hba.conf` 必须放通来源地址

授权示例：

```sql
CREATE ROLE sync_user REPLICATION LOGIN PASSWORD 'StrongPassword';
GRANT CONNECT ON DATABASE appdb TO sync_user;
GRANT USAGE ON SCHEMA public TO sync_user;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO sync_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO sync_user;

CREATE PUBLICATION sync_pub FOR TABLE public.orders;
```

`pg_hba.conf` 示例：

```conf
host    replication    sync_user    10.0.0.0/24    md5
host    appdb          sync_user    10.0.0.0/24    md5
```

### SAP HANA

适用场景：

- 全量导出
- 基于高水位字段的增量轮询

本项目需要的权限：

- 源 schema 或源表 `SELECT`
- 建议授予 `CATALOG READ` 便于元数据读取

授权示例：

```sql
CREATE USER SYNC_USER PASSWORD "StrongPassword";
GRANT CATALOG READ TO SYNC_USER;
GRANT SELECT ON SCHEMA APP TO SYNC_USER;
```

### MongoDB

适用场景：

- 通过 `mongoexport` 做全量导出
- 通过 change streams 做增量同步

本项目需要的权限：

- 源库读取权限
- 被监听集合的 change stream 权限
- 常见场景下内置 `read` 角色可以满足集合级读取和 `watch()` 访问

授权示例：

```javascript
use source_db
db.createUser({
  user: "sync_user",
  pwd: "StrongPassword",
  roles: [
    { role: "read", db: "source_db" }
  ]
})
```

## 目标 TiDB 权限

适用场景：

- `tidb-lightning` 全量导入
- 增量 upsert 和 delete

建议权限：

- `SELECT`
- `INSERT`
- `UPDATE`
- `DELETE`
- `CREATE`
- `ALTER`
- `INDEX`

授权示例：

```sql
CREATE USER 'sync_user'@'%' IDENTIFIED BY 'StrongPassword';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON target_db.* TO 'sync_user'@'%';
```

POC 环境可临时使用：

```sql
GRANT ALL PRIVILEGES ON target_db.* TO 'sync_user'@'%';
```

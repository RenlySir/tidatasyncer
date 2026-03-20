# TiDB Sync Platform

多模块 Spring 工程，用于将 MySQL、Oracle、SQL Server、PostgreSQL、HANA 的全量和增量数据同步到 TiDB。

## 模块

- `sync-engine-core`: 同步领域模型与 SPI
- `sync-engine-connectors`: 全量导出、Lightning 导入、CDC 采集、TiDB 写入实现
- `sync-admin-server`: Spring Boot 控制面，提供任务配置、启动停止、监控与日志 API
- `sync-ui`: Vue 3 前端页面

## 核心设计

- 全量：源库导出 CSV -> `tidb-lightning` 导入 TiDB
- 增量：CDC 采集 -> 标准化事件 -> TiDB Sink Upsert / Delete
- 全量 + 增量：先启动 CDC 缓冲，再执行全量，导入结束后切换为实时增量直写

## 目录说明

- [architecture.md](./docs/architecture.md): 总体架构与链路说明
- [sync-admin-server](./sync-admin-server): 控制面与任务编排
- [sync-engine-connectors](./sync-engine-connectors): 连接器实现
- [sync-ui](./sync-ui): Web UI

## 当前实现说明

- MySQL、Oracle、SQL Server、PostgreSQL 的增量链路基于 Debezium Embedded
- HANA 增量链路采用高水位轮询 CDC 方案，保持统一控制面接口
- 全量导出依赖源端客户端工具，命令模板可在任务配置中覆盖
- TiDB 写入默认使用 MySQL 兼容 JDBC

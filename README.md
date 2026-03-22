# TiDB Sync Platform

多模块 Spring 工程，用于构建一个批量 + 实时一体的数据管道平台，将 MySQL、Oracle、SQL Server、PostgreSQL、HANA、MongoDB、CSV 目录等源端数据同步到 TiDB。

## 模块

- `sync-engine-core`: 同步领域模型与 SPI
- `sync-engine-connectors`: 全量导出、Lightning 导入、CDC 采集、TiDB 写入实现
- `sync-admin-server`: Spring Boot 控制面，提供任务配置、启动停止、监控与日志 API
- `sync-ui`: Vue 3 前端页面

## 核心设计

- 数据采集：数据库连接、CSV 目录、日志位点和权限前置检查
- 批量装载：源库原生工具导出数据文件 -> `tidb-lightning` 导入 TiDB
- 实时同步：CDC 采集 -> 标准化事件 -> TiDB Sink Upsert / Delete
- 批流协同：先完成全量导出和 Lightning 导入，再启动增量同步
- 运行观测：统一展示进度、阶段、日志位点、延迟、错误和最新事件元数据

## 目录说明

- [architecture.md](./docs/architecture.md): 总体架构与链路说明
- [connection-privileges.md](./docs/connection-privileges.md): 第一步连接参数填写说明、源端/目标端权限要求与授权示例
- [sync-admin-server](./sync-admin-server): 控制面与任务编排
- [sync-engine-connectors](./sync-engine-connectors): 连接器实现
- [sync-ui](./sync-ui): Web UI

## 当前实现说明

- MySQL 到 TiDB 的增量链路基于 Debezium MySQL Connector 读取 binlog，之后由平台参考 TiFlow/DM 的 DML 组织思路写入 TiDB
- Oracle、SQL Server、PostgreSQL 的增量链路基于 Debezium Embedded
- MongoDB 的全量链路基于 `mongoexport` 导出 CSV，增量链路基于 MongoDB change streams
- HANA 增量链路采用高水位轮询 CDC 方案，保持统一控制面接口
- Oracle 的全量链路使用 `sqluldr2` 导出 CSV，按 `128 MiB` 切分并生成符合 Lightning 要求的文件名，再调用 `tidb-lightning` 导入
- MySQL 的全量链路使用 `dumpling` 导出 SQL 文件，单文件大小通过 `-F 128MiB` 控制，再调用 `tidb-lightning` 直接导入
- 页面新增独立的 CSV 目录导入面板，支持先检查目录并将超过 `256 MiB` 的 CSV 按 `128 MiB` 切分，再手动触发 `tidb-lightning` 导入
- 首页工作台按“接入 -> 检测 -> 结构 -> 交付 -> 运营”展示数据管道生命周期，并给出管道就绪度和批流覆盖指标
- 兼容性报告支持同时输出 Markdown 和 HTML 文件，便于留档和页面内直观预览
- 任务配置页首屏会先提示填写源库和目标库的 `host/ip`、`port`、`user`、`password`，并按源库类型展示所需权限和授权 example
- MySQL 的 `FULL_AND_INCREMENTAL` 模式会先走 `dumpling SQL dump + Lightning` 全量装载，再以 Debezium `snapshot.mode=no_data` 启动增量同步，避免重复做初始快照
- 全量导出依赖源端客户端工具，命令模板可在任务配置中覆盖
- MongoDB 全量导出要求在表映射中显式填写 `includedColumns`，用于生成 `mongoexport --fieldFile`
- TiDB 写入默认使用 MySQL 兼容 JDBC

## 工具管理

- 项目内工具统一放在 `vendor/tools`
- `x86` 环境工具放在 `vendor/tools/x86/<tool-name>/<tool-name>`
- `arm` 环境工具放在 `vendor/tools/arm/<tool-name>/<tool-name>`
- 页面会先保存全局部署机器架构 `AMD64` 或 `ARM64`，运行时统一按该架构解析工具目录
- `tidb-lightning` 通过 Java 在运行时优先从 `vendor/tools/x86/tidb-lightning/tidb-lightning` 或 `vendor/tools/arm/tidb-lightning/tidb-lightning` 查找
- `tidb-lightning` 按 PingCAP 官方 toolkit 地址下载，解压后仅保留 `tidb-lightning` 二进制，不保留压缩包与其他工具文件
- 如果未配置显式路径，Java 会尝试按官方 Linux 地址自动安装当前架构对应的 `tidb-lightning`，安装完成后同样只保留二进制
- `dumpling` 需要在运行主机预装，或在任务配置中填写显式工具路径
- `sqluldr2` 优先从 `vendor/tools/x86/sqluldr2/sqluldr2` 或 `vendor/tools/arm/sqluldr2/sqluldr2` 查找
- 由于作者官网未公开稳定直链，`sqluldr2` 自动安装需要设置 `SQLULDR2_DOWNLOAD_URL` 或预先把二进制放到项目目录
- `psql`、`bcp`、`hdbsql` 仍需在运行主机预装，或在任务配置中填写显式工具路径
- 可先运行 [bootstrap-tools.sh](./scripts/bootstrap-tools.sh) 初始化项目内工具目录

## 本地启动

- 一键启动：`./scripts/dev-up.sh`
- 一键停止：`./scripts/dev-down.sh`
- 前端默认地址：[http://localhost:5173](http://localhost:5173)
- 后端默认地址：[http://localhost:8080](http://localhost:8080)
- 一键启动脚本会先执行本地 `mvn install`，再分别启动 `sync-admin-server` 和 `sync-ui`

## 并发运行说明

- 数据同步任务通过独立线程池执行，支持多个任务并发运行
- 平台会对源端、目标端和增量位点文件做资源级占用检查，避免多个任务同时写同一目标库或复用同一 offset 路径
- 监控中心已按“兼容性对比 / 表结构迁移 / 数据同步”三类任务统一展示，便于运维排障

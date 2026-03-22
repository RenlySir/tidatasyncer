# Architecture

## 目标

构建一个统一的数据管道平台，支持：

- 源端：MySQL、Oracle、SQL Server、PostgreSQL、HANA、MongoDB
- 目标端：TiDB
- 模式：仅全量、仅增量、全量 + 增量
- 能力：连接接入、权限检查、兼容性报告、结构同步、批量装载、实时 CDC、进度跟踪、报错展示、同步延迟、最新事件元数据展示

## 模块视图

```text
sync-ui
  -> sync-admin-server
       -> sync-engine-core
       -> sync-engine-connectors
```

## 关键组件

### 1. 控制面

- 数据接入：创建数据源、目标、目录型 CSV 源和部署架构
- 任务管理：创建、更新、启动、停止兼容性、结构和数据同步任务
- 运行编排：根据同步模式调度全量导出、Lightning 导入、CDC 采集
- 监控聚合：收集进度、阶段、错误、延迟、最新事件元数据和批流覆盖
- 审计与日志：保留任务状态变化与运行日志

### 2. 批量链路

1. 读取任务配置与表映射
2. 按源库类型调用对应 CSV 导出器
3. 通过 Java 解析项目内工具路径，优先使用工程内 `vendor/tools/x86` 或 `vendor/tools/arm` 下预置的工具
4. MySQL 优先通过 `dumpling` 导出 SQL，Oracle 优先通过项目内 `sqluldr2` 导出 CSV，MongoDB 优先通过 `mongoexport` 调用
5. 生成 Lightning 导入配置
6. 调用 `tidb-lightning` 导入 TiDB
7. 上报导出进度、导入进度和错误信息

### 3. 实时链路

- MySQL:
  使用 Debezium MySQL Connector 读取 binlog，平台侧再按 TiFlow/DM 的 DML 组织思路写入 TiDB
- Oracle / SQL Server / PostgreSQL:
  使用 Debezium Embedded 订阅日志变更
- HANA:
  使用基于高水位列的 CDC 轮询实现
- MongoDB:
  使用 `mongoexport` 承接全量 CSV 导出，使用 MongoDB change streams 承接增量 CDC

所有增量事件都会被标准化成统一模型，再交给 TiDB Sink 执行 Upsert / Delete。MySQL 链路额外参考 TiFlow/DM 的实现思路，对主键变更场景执行“先删后写”。

### 4. 批流协同

标准链路：

1. 先执行全量 CSV 导出
2. 对导出的 CSV 按 Lightning 规则整理
3. 调用 `tidb-lightning` 完成全量导入
4. 全量导入完成后启动增量同步

Oracle 典型链路：

1. 通过 `sqluldr2` 导出表数据到 CSV
2. 将 CSV 按 `128 MiB` 切分
3. 将切分后的文件按 TiDB Lightning 要求命名
4. 通过 `tidb-lightning` 导入 TiDB
5. 导入完成后再启动增量同步

MySQL 典型链路：

1. 先执行 `dumpling` 全量导出，输出 SQL dump
2. 通过 `-F 128MiB` 控制单个导出文件大小
3. 调用 `tidb-lightning` 导入 SQL dump
5. 全量导入完成后，再启动 Debezium MySQL Connector
6. 默认使用 `snapshot.mode=no_data`，避免重复执行 Debezium 初始快照
7. TiDB Sink 参考 TiFlow/DM 的下游 DML 组织逻辑，对普通更新执行 upsert，对主键变更执行 delete + upsert

### 5. 交付与运营视角

- 接入层：连接画像、权限检测、工具目录、CSV 目录预处理
- 检测层：兼容性报告、对象差异、字段类型映射
- 兼容性报告产物同时输出 Markdown 与 HTML，便于页面预览和线下评审
- 交付层：结构同步、全量装载、增量位点衔接
- 运营层：阶段、日志位点、延迟、错误和最近任务统一展示
- 页面工作台按“接入 -> 检测 -> 结构 -> 交付 -> 运营”组织，避免用户直接跳过前置步骤
- 并发控制层：通过线程池 + 资源占用协调器避免同一源端、目标端或位点文件被多个任务同时占用

## 数据模型

### 任务配置

- 源端连接
- 目标端连接
- 部署机器架构选择，用于解析 `arm` / `x86` 工具目录
- 同步模式
- 表映射
- 全量工具路径与命令模板
- CDC 配置
- MySQL Debezium 增量配置，例如 `mysqlServerId`、`mysqlSnapshotMode`
- MongoDB 集合导出字段列表与字段映射

### 运行态

- 任务状态
- 当前阶段
- 进度百分比
- 最新错误
- 最新延迟
- 最新事件的库、表、主键和值

## 前端页面

- 任务列表
- 新建 / 编辑任务
- 任务详情
- 运行日志
- 指标看板

## 优化点

- 导出器支持命令模板覆盖，避免强耦合某个客户端工具
- CDC 统一成抽象接口，便于后续替换 HANA 实现
- MongoDB 全量文件会按 Lightning 兼容 CSV 头与文件名落盘
- 导入层优先通过项目内 `vendor/tools` 管理二进制，并按官方 toolkit 规则仅保留 `tidb-lightning` 本体，减少环境漂移
- TiDB Sink 基于主键动态生成 Upsert / Delete SQL

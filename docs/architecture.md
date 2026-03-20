# Architecture

## 目标

构建一个统一的数据同步平台，支持：

- 源端：MySQL、Oracle、SQL Server、PostgreSQL、HANA
- 目标端：TiDB
- 模式：仅全量、仅增量、全量 + 增量
- 能力：任务配置、进度跟踪、报错展示、同步延迟、最新事件元数据展示

## 模块视图

```text
sync-ui
  -> sync-admin-server
       -> sync-engine-core
       -> sync-engine-connectors
```

## 关键组件

### 1. 控制面

- 任务管理：创建、更新、启动、停止任务
- 运行编排：根据同步模式调度全量导出、Lightning 导入、CDC 采集
- 监控聚合：收集进度、阶段、错误、延迟、最新事件元数据
- 审计与日志：保留任务状态变化与运行日志

### 2. 全量链路

1. 读取任务配置与表映射
2. 按源库类型调用对应 CSV 导出器
3. 生成 Lightning 导入配置
4. 调用 `tidb-lightning` 导入 TiDB
5. 上报导出进度、导入进度和错误信息

### 3. 增量链路

- MySQL / Oracle / SQL Server / PostgreSQL:
  使用 Debezium Embedded 订阅日志变更
- HANA:
  使用基于高水位列的 CDC 轮询实现

变更事件被标准化成统一模型，再交给 TiDB Sink 执行 Upsert / Delete。

### 4. 全量 + 增量并行

1. 先启动 CDC 捕获并写入内存缓冲
2. 执行全量 CSV 导出和 Lightning 导入
3. 切换缓冲到直写模式，并将缓冲事件顺序回放到 TiDB
4. 保持 CDC 持续运行

## 数据模型

### 任务配置

- 源端连接
- 目标端连接
- 同步模式
- 表映射
- 全量工具路径与命令模板
- CDC 配置

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
- TiDB Sink 基于主键动态生成 Upsert / Delete SQL

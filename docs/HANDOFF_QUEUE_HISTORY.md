# 任务交接：队列页与历史页体验重构

> 生成时间：2026-11-11  
> 仓库：`D:\Projects\NekoPaint`  
> 最后已提交版本：`af4c5bb feat: 完成工作流提示词编辑器、参数预设与服务器页收尾`  
> 当前状态：**大量改动未提交、未推送**

---

## 1. 下一轮对话怎么开始

在新对话中直接发送：

```text
请读取 D:\Projects\NekoPaint\docs\HANDOFF_QUEUE_HISTORY.md。
先执行 git status 和 git diff --stat，确认工作区状态；
不要覆盖已有未提交改动；
按交接文件的“下一步”继续，完成后跑构建、单测、Lint，并在模拟器验证。
```

注意：

- 新会话不会自动继承当前聊天上下文，必须依赖本文件和 Git 工作区。
- 不要执行 `git reset --hard`、`git checkout -- .` 或删除未跟踪文件。
- 不要提交 `design/screenshots/`、`workflow_analysis.txt`、`workflow_keynodes.txt`；截图目录包含历史截图，可能含真实局域网 IP。
- API Key、真实服务器地址、提示词正文不要写进交接文件或日志。

---

## 2. 本轮目标

按设计交接文档重塑：

- `QueueScreen.kt`：执行队列页
- `HistoryScreen.kt` / `HistoryViewModel.kt` / `HistoryDetailScreen.kt`：历史记录页
- 历史页顶部极简轻透单行 Sub-header
- 参数抽屉、三视图、长按多选、批量导出/删除、回填工作流闭环

参考设计：

- `C:\Users\xiaowu\.gemini\antigravity\brain\501cc652-6773-49bd-9521-2a075dea2098\queue_history_design_v2.html`
- `...\history_multi_view_prototype.html`
- `...\history_header_redesign.html`

---

## 3. 已完成内容

### 3.1 队列页

- `RunningCard`
  - 44dp 浅蓝图标方块、工作流名、提示词副标
  - 内部纯白数据面板：双进度架构
    - 总进度：已完成 / 总节点数 + 百分比，6dp Primary 主进度条
    - 当前节点：节点名 + 步数/百分比，4dp Secondary 次进度条
  - 速率、ETA、耗时、Prompt 短 ID
    - 遥测行使用 `速率 · 预估剩余 · 已耗时` 点号紧凑排布，Prompt ID 限宽 100dp 并省略，避免挤压变形
    - 耗时由 `produceState` 每秒心跳驱动，模型加载期间也能平滑自增
  - 批次胶囊、本机/其他客户端标签、错误色中断按钮
- 数据层：`TaskProgress` 携带 `completedNodes / totalNodes / overallPercent`，`TaskManager` 跟踪 `execution_cached` 与 `executed` 节点并实时计算总进度
- `PendingCard`
  - 大序号方块、本机任务浅蓝底
  - 提示词副标题、尺寸/步数规格摘要、本机/其他客户端标签、快捷删除
- `QueueEmptyState`
  - 空闲引导卡片
  - 仅 Debug 显示“演示数据”按钮
- 定时刷新、断线轮询、中断/删除确认弹窗保留
- 外层卡片 `surfaceContainer`，内层面板 `surfaceContainerLowest`，无描边

### 3.2 历史页三视图

- **Timeline 时间轴**
  - 按自然日分组：今天 / 昨天 / 日期
  - 批次内图片横向滚动；点击缩略图直接进入全屏 Lightbox，点击卡片主体打开参数抽屉
  - 纯白参数盒：提示词、种子、步数、采样器、模型、尺寸
  - 「以此参数回填至工作流」主按钮
- **Grid 双列画廊**
  - 大图卡片、成图/草稿角标、提示词与时间
  - 点击打开参数抽屉
- **Compact 3 列矩阵**
  - 按图片平铺，适合高密度浏览
  - 长按进入多选

### 3.3 顶部极简 Sub-header

- 彻底移除原先的外层大 Card
- 单行结构（方案 A）：
  - 仅保留左侧 M3 椭圆 `FilterChip`：「全部 (N) / 仅成图 / 草稿预览」
  - 移除右侧孤立的灰色胶囊视图切换容器，列表整体上移
- TopAppBar 操作（从左到右）：
  - 普通模式：视图循环图标、刷新、更多 ⋮（批量选择 / 清空全部历史）
  - 多选模式：全选（全选后变为取消全选）、批量导出、删除选中（错误色）、完成
  - 移除底部多选浮条，底部视口全部留给列表滚动
  - 仍复用 `NekoTopBarIconAction`，支持 `label` 与 `menuItems`
  - 长按任意卡片直接进入多选；Sub-header 显示「已选 N 项」药丸徽章
  - `HistoryScreen` 通过 `DisposableEffect` 注册/释放
- 增加离线/错误轻提示（非正常状态才显示）

### 3.4 参数抽屉与业务闭环

- `ModalBottomSheet` 参数透视镜：
  - 大图预览：图片高度 `heightIn(180.dp, 340.dp)`，支持双指 1x~4x 缩放与拖动查看细节
  - 点击进入纯黑沉浸全屏 Lightbox，图片无任何文字遮罩
  - 长按图片弹出操作菜单：保存原图、分享、复制提示词、从历史记录删除
  - 全屏 Lightbox 内长按同样弹出该操作菜单；点击背景/返回键退出
  - 提示词纯白卡 + 一键复制
  - 采样参数双列展示
  - 保存原图到相册
  - 回填工作流
  - 「完整详情」入口
- **回填不直接覆盖模板**
  - 新增 `PendingWorkflowRefillStore`
  - 历史页只把参数暂存到内存
  - 工作流详情页消费一次，装载参数并标记“已修改”
  - 用户主动保存才落盘；放弃修改则保持模板原值
  - 决策记录：`DECISIONS.md` D-037

### 3.5 多选与性能

- Timeline / Grid / Compact 均支持长按进入多选
- 三视图统一使用标准 M3 `Checkbox`（圆形触控区 48dp，方圆角视觉）
- 多选操作全部迁移到顶栏右上角：
  - 全选 / 取消全选、批量导出、删除选中（错误色）、完成
  - 底部不再有悬浮操作条，Sub-header 显示「已选 N 项」轻量药丸徽章
  - 列表底部留白从 80dp 收敛到 24dp，内容视口更完整
- 任务查找从每项 `firstOrNull` 改为一次 `associateBy(promptId)` 的 `Map`
- 历史图片失效时显示“源文件已失效或主机离线”占位，本地提示词和参数仍可用
- 卡片不再显示 `ID: demo-his` 等调试文本；无提示词且无参数时不渲染空白色块

### 3.6 文案与清理

- Queue / History 可见文案收敛到 `strings.xml`
- 回填按钮文案精简为「回填工作流」
- 新增图片操作菜单文案（保存/分享/复制提示词/删除）
- 移除 19 个不再使用的旧字符串
- 移除旧 `ViewModeTab` 残留代码
- 当前 Lint：**0 errors / 9 warnings**（仅原有基线）

### 3.7 历史详情页视觉统一（本轮新增）

- `HistoryDetailScreen.kt` 外层卡片改为 `surfaceContainer` + 16dp 圆角 + 14dp 内边距，与 Timeline / Grid 卡片一致
- 标题行改为：工作流名 + 时间/耗时 + 状态徽章；Prompt 短 ID 单独一行
- 操作按钮改为胶囊形：`再次生成`（FilledTonal）+ `回填工作流`（Button）
- 时间与删除入口合并到底部一行，删除使用错误色文字按钮
- 输出图片复用 `HistorySubcomposeImage` 失效占位，补上「成图 / 草稿」角标
- 参数快照改为：顶部参数药丸 + 纯白 `surfaceContainerLowest` 明细面板
- 错误信息独立成卡，错误正文放进纯白面板
- 移除详情页不再使用的 `history_duration`、`image_open_desc` 两个字符串
- 暗色模式已实机截图核对：外层 `surfaceContainer` 与内层 `surfaceContainerLowest` 层级清晰，文字对比度正常

---

## 4. 关键文件

### 本轮主要修改

```text
app/src/main/java/io/github/xiaowu20245/nekopaint/feature/queue/QueueScreen.kt
app/src/main/java/io/github/xiaowu20245/nekopaint/feature/queue/QueueViewModel.kt
app/src/main/java/io/github/xiaowu20245/nekopaint/feature/history/HistoryScreen.kt
app/src/main/java/io/github/xiaowu20245/nekopaint/feature/history/HistoryViewModel.kt
app/src/main/java/io/github/xiaowu20245/nekopaint/feature/history/HistoryDetailScreen.kt
app/src/main/java/io/github/xiaowu20245/nekopaint/feature/history/HistoryMetadataExtractor.kt
app/src/main/java/io/github/xiaowu20245/nekopaint/data/repository/PendingWorkflowRefillStore.kt
```

### 顶栏与导航

```text
app/src/main/java/io/github/xiaowu20245/nekopaint/core/ui/NekoTopBarAction.kt
app/src/main/java/io/github/xiaowu20245/nekopaint/core/ui/NekoPaintApp.kt
app/src/main/java/io/github/xiaowu20245/nekopaint/core/ui/navigation/NekoPaintNavHost.kt
```

### 回填相关

```text
app/src/main/java/io/github/xiaowu20245/nekopaint/feature/workflow/WorkflowDetailViewModel.kt
app/src/main/java/io/github/xiaowu20245/nekopaint/di/AppContainer.kt
```

### 测试

```text
app/src/test/java/io/github/xiaowu20245/nekopaint/feature/history/HistoryMetadataExtractorTest.kt
app/src/test/java/io/github/xiaowu20245/nekopaint/data/repository/PendingWorkflowRefillStoreTest.kt
```

### 文档

```text
ROADMAP.md
DECISIONS.md（D-037）
AGENTS.md
```

---

## 5. 验证结果

| 项目 | 结果 |
| --- | --- |
| `gradlew.bat :app:assembleDebug` | 成功 |
| `gradlew.bat :app:testDebugUnitTest` | **97 个测试 / 0 失败** |
| `gradlew.bat :app:lintDebug` | **0 errors / 9 warnings** |

模拟器已验：

- 队列运行卡：进度、速率、ETA、耗时、批次、中断按钮
- 队列运行卡（V4）：工作流总进度 5/8 节点 65% + 当前节点 14/20 步 70% 双进度条
- 队列运行卡（V5）：指标行不挤压、耗时每秒自增、Prompt ID 限宽省略
- 历史缩略图（V5）：点击直达全屏 Lightbox；卡片主体仍打开透视镜
- 参数透镜（V5）：图片高度弹性 180~340dp，双指缩放/拖动，单击全屏、长按菜单保留
- 等待卡：规格、本机/其他客户端标签、删除
- 历史三视图切换
- 极简顶部单行 Sub-header
- TopAppBar 刷新 / 选择文字按钮 / 视图循环 / 更多菜单
- 长按多选与两行批量操作栏
- 参数抽屉、复制提示词、完整详情入口
- 回填成功进入工作流详情并装载参数
- 放弃修改后模板保持原值
- 历史详情页（本轮）：成功记录、失败记录、参数面板、图片角标、暗色模式
- V3：正常列表（纯图标顶栏、回填工作流 4 字、无 ID 文本）
- V3：多选模式（标准 Checkbox、单行底栏、errorContainer 删除）
- V3：参数透镜长按菜单与全屏 Lightbox 长按联动

本轮详情页验证方式：向模拟器 App 私有目录临时注入一条带真实 `TaskRecord` 的历史缓存（图片用占位 URL），验证后已恢复为空历史并删除注入的任务文件。截图：

```text
design/screenshots/history_detail_aligned_light.png
design/screenshots/history_detail_aligned_dark.png
design/screenshots/history_detail_aligned_error_dark.png
design/screenshots/history_topbar_refined.png
design/screenshots/history_v3_normal.png
design/screenshots/history_v3_selection.png
design/screenshots/history_v3_drawer_menu.png
design/screenshots/history_v3_lightbox.png
design/screenshots/history_v4_clean_drawer.png
design/screenshots/history_v4_topright_selection.png
design/screenshots/queue_overall_progress_verified.png
design/screenshots/queue_telemetry_flow_verified.png
design/screenshots/history_lightbox_direct_verified.png
```

截图位于 `design/screenshots/`：

```text
queue_clean.png
history_header_clean_timeline.png
history_header_clean_grid.png
history_header_clean_compact.png
history_header_clean_drawer.png
history_header_clean_selection.png
queue_history_redesign_refill_result.png
queue_history_redesign_refill_unsaved.png
queue_history_redesign_template_restored.png
```

---

## 6. 已知问题与风险

1. **未提交、未推送**
   - 工作区包含本轮及更早的未提交改动，不要误认为已入库。

2. **演示数据**
   - 队列/历史“演示数据”仅 Debug 显示。
   - 历史演示图片来自 `picsum.photos`，不是 ComfyUI 真实图片。
   - 演示历史不在服务器历史中，点“完整详情”会显示“找不到记录”，这是预期现象。

3. **长列表压力**
   - 已完成任务 Map 优化，但尚未用数百条真实历史做极限滚动测试。

4. **截图目录**
   - `design/screenshots/` 未跟踪，包含历史截图，可能含真实局域网 IP。
   - 不要整体提交；如需提交截图，先逐张检查并脱敏。

5. **历史详情页**
   - 视觉已与三视图统一（2026-11-11）；若后续调整三视图卡片规格，需同步详情页的圆角与内边距。

6. **顶栏 Actions**
   - 历史页依赖 `DisposableEffect` 注册/释放顶栏图标；若未来其他二级页也需要图标 Actions，可继续复用 `NekoTopBarIconAction`。

---

## 7. 下一步建议

1. 用真实 ComfyUI 历史数据验证 100+ 条记录的滚动与图片加载。
2. 逐张检查截图脱敏后，再决定是否提交 `design/screenshots/`。
3. 用户确认后，再按仓库 Git 规范拆分提交：
   - `feat: 重构队列与历史页体验`
   - `fix: 历史回填改为确认后保存`
   - `docs: 更新队列历史交接与决策`
4. 提交前重新执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
git diff --check
```

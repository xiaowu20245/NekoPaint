# NekoPaint 当前 UI 现状

> 更新时间：2026-11-08
> 用途：供 Antigravity 做视觉审计与 Token 设计
> 范围：服务器、工作流列表、工作流详情、队列、历史、历史详情、设置、蒙版编辑器
> 约束：这是现状记录，不代表最终设计；本文件不修改业务代码

---

## 0. 截图索引

截图来自 Android 模拟器 `Medium_Phone_API_37.0`，1080×2400，浅色主题，中文。

| 页面 | 截图 |
| --- | --- |
| 服务器 | `design/screenshots/server.png` |
| 工作流列表 | `design/screenshots/workflows.png` |
| 工作流详情 | `design/screenshots/workflow_detail.png` |
| 队列 | `design/screenshots/queue.png` |
| 历史列表 | `design/screenshots/history.png` |
| 历史详情 | `design/screenshots/history_detail.png` |
| 设置 | `design/screenshots/settings.png` |
| 蒙版选图 | `design/screenshots/mask_picker.png` |
| 蒙版编辑器 | `design/screenshots/mask_editor.png` |

---

## 1. 全局框架

### 文件

- `core/ui/NekoPaintApp.kt`
- `core/ui/navigation/NekoPaintNavHost.kt`
- `core/ui/navigation/NekoPaintDestination.kt`
- `core/ui/theme/Theme.kt`
- `core/ui/theme/Color.kt`
- `core/ui/theme/Type.kt`
- `MainActivity.kt`

### 结构

- 单 Activity + Compose
- `Scaffold`
  - `TopAppBar`：标题、返回按钮、可选右侧操作（目前用于工作流详情的“提交生成”）
  - `NavigationBar`：服务器 / 工作流 / 队列 / 设置
  - 子页面：`workflow/{id}`、`history`、`history/{id}`、`generate`
- 页面切换：水平滑动 + 淡入，260ms
- Edge-to-edge 已启用
- 主题：跟随系统 / 浅色 / 深色，持久化；Android 12+ 使用动态取色

### 当前主题 token

颜色定义在 `Color.kt`：

| Token | Light | Dark |
| --- | --- | --- |
| `NekoBlue` / primary | `#4F6BED` | `#B8C4FF` |
| `NekoBlueContainer` | `#DDE2FF` | `#33416F` |
| `NekoPink` / secondary | `#FF8FA3` | `#FF8FA3` |
| `NekoPinkContainer` | `#FFD9DF` | `#6B3440` |
| `NekoBackground` | `#FFFBFF` | `#101114` |
| `NekoSurfaceVariant` | `#E2E1EC` | `#45464F` |

现状：

- 没有独立的 spacing / radius / elevation token 文件
- 页面直接写 `16.dp`、`12.dp`、`10.dp`、`8.dp`、`6.dp`
- 圆角多使用默认 Material Card 圆角，历史缩略图使用 `RoundedCornerShape(10.dp)`
- 排版使用 `Typography()` 默认值，没有自定义字号/行高
- 图标主要使用 Material 基础 Outlined 图标

---

## 2. 服务器页

### 文件

- `feature/server/ServerScreen.kt`
- `feature/server/ServerViewModel.kt`
- `feature/server/ServerUiState.kt`

### 组件

- `ServerScreen`
- `EmptyServers`
- `ServerListCard`
- `CardActions`（设为当前 / 刷新 / 编辑 / 删除）
- `CardStatus`
- `ServerFormCard`（名称、地址、端口、协议、测试连接、保存并连接、取消）
- `GpuSection`
- `GpuCard`
- `SystemSection`
- `InfoRow`
- `SecurityNotice`

### 当前布局

- 顶部是服务器卡片列表
- 每张卡：
  - 名称
  - 当前/非当前标识
  - 展开/折叠箭头
  - 地址
  - 在线/未检测/离线状态
- 展开后：
  - 操作按钮行
  - ComfyUI 版本、延迟、更新时间
  - GPU 卡：名称、显存进度、总量/可用/已用
  - 系统信息：OS、ComfyUI、Python、PyTorch
- 底部是安全提示卡
- 右下角 Extended FAB：添加服务器

### 状态

- 空状态：`EmptyServers`
- 测试连接中：按钮 loading
- 在线 / 未检测 / 离线
- 删除确认对话框
- 表单校验错误

### 视觉痛点

- 服务器卡片和“安全提示”卡视觉重量接近，主次不够清楚
- 在线/未检测/离线状态目前主要靠文字 + 小图标
- GPU 显存进度条颜色与主题关系较弱
- 卡片展开后信息密度较高，缺少分组标题层级
- 右下角 FAB 会遮挡最后一张卡片的部分内容

---

## 3. 工作流列表

### 文件

- `feature/workflow/WorkflowsScreen.kt`
- `feature/workflow/WorkflowsViewModel.kt`

### 组件

- `WorkflowsScreen`
- `EmptyWorkflows`
- `WorkflowCard`
- `LabeledValue`
- 重命名 `AlertDialog`
- 删除 `AlertDialog`
- 右上角溢出菜单：重命名 / 复制 / 删除

### 当前布局

- `LazyColumn`，16dp 内容边距，10dp 间距
- 每张工作流卡：
  - 名称
  - 右上角“更多操作”
  - 文件名
  - 三个元数据：节点 / 可达节点 / 已选参数
  - 缺失节点警告（可选）
  - 导入时间
- 右下角 Extended FAB：导入工作流

### 状态

- 空状态：导入引导
- 导入中：顶部 LinearProgressIndicator
- 操作中：卡片右上角 CircularProgressIndicator
- 错误：Snackbar

### 视觉痛点

- 卡片信息是“名称 + 文件名 + 三个数字 + 时间”，缺少更直观的缩略图/类型标识
- 文件名和名称视觉层级接近
- 三个数字没有单位或解释，初次使用不易理解
- 缺失节点警告与普通信息混在一起
- 长列表没有搜索/排序

---

## 4. 工作流详情

### 文件

- `feature/workflow/WorkflowDetailScreen.kt`
- `feature/workflow/WorkflowDetailViewModel.kt`
- `feature/workflow/MaskEditorDialog.kt`
- `feature/workflow/EnumField` 等在同文件内

### 组件

- `WorkflowDetailScreen`
- `WorkflowPage`（简单/高级两页 HorizontalPager）
- `SummaryCard`
- `CompatibilityCard`
- `SubmitErrorCard`
- `SubmissionDialog`
- `RawJsonDialog`
- `CandidateRow`
- `ParameterField`
- `EnumField`
- `ConfidenceBadge`
- `MetaValue`
- 蒙版编辑入口：`MaskEditorDialog`

### 当前布局

- 顶部动态操作：滚动后出现“提交生成”
- 简单/高级模式：`HorizontalPager`
- 页面内从上到下：
  1. 摘要卡：名称、文件名、节点/可达/已选参数、输出节点、查看原始 JSON
  2. 兼容性卡：运行类型、外部图片/蒙版槽、问题列表
  3. 模式切换 Chip + 编辑按钮
  4. 提交按钮
  5. 参数分组（提示词 / 图像 / 采样 / 模型 / 高级 / 其他）
  6. 参数卡：标签、节点 ID、类型、置信度、输入控件
- 高级模式顶部：
  - 搜索框
  - 分组筛选 Chip
- 图片参数：
  - 文件名输入
  - 上传按钮
  - 蒙版槽额外有“从历史图片绘制”
- 底部提示：提交后会创建任务记录

### 状态

- 加载 / 模板不存在
- 简单/高级
- 编辑模式
- 字段错误
- 提交中 / 提交成功对话框 / 提交失败
- 原始 JSON 对话框
- 蒙版编辑器全屏 Dialog

### 视觉痛点

- 页面信息量最大，摘要卡、兼容性卡、模式切换、提交按钮、参数列表连续堆叠，层级偏平
- 兼容性问题列表用纯文本 `•`，错误/警告/信息区别不够明显
- 参数卡重复信息多：标签、节点 ID、类型、置信度、输入控件
- 高级模式搜索/筛选和参数列表之间的视觉分隔较弱
- 提交按钮同时出现在页面内和顶栏，位置不统一
- 图片上传/蒙版绘制入口在参数卡内部，发现性一般
- 参数分组标题与卡片间距接近，分组感不强

---

## 5. 队列页

### 文件

- `feature/queue/QueueScreen.kt`

### 组件

- `QueueScreen`
- `QueueHeader`
- `RunningCard`
- `PendingCard`
- `SourceBadge`
- `ConfirmDialog`

### 当前布局

- 顶部连接卡：
  - 连接状态 Chip（实时连接 / 重连中 / 离线）
  - 刷新按钮
  - 说明文字
  - 队列剩余数量
  - “历史记录”按钮
- 运行中任务卡：
  - 模板名 / Prompt 短码
  - 来源标识（本机/其他客户端）
  - 当前节点
  - 节点进度条 + 数值
  - 中断按钮
- 等待中任务卡：
  - 序号、Prompt 短码、来源
  - 删除按钮
- 空状态：队列为空
- 错误：轮询降级、无法刷新、旧数据提示

### 状态

- 运行中 / 等待中 / 空
- 连接正常 / 重连中 / 离线
- 任务中断确认 / 删除确认

### 视觉痛点

- 运行中卡的进度表达较基础，只看得到当前节点和 value/max
- 连接状态卡与任务卡视觉重量接近
- 断线、轮询降级、旧数据提示叠加时信息较杂
- Pending 卡的按钮和元数据排列偏紧凑
- 空状态比较朴素

---

## 6. 历史列表

### 文件

- `feature/history/HistoryScreen.kt`
- `feature/history/HistoryViewModel.kt`

### 组件

- `HistoryScreen`
- `HistoryHeader`
- `EmptyHistory`
- `HistoryCard`
- `StatusBadge`
- 清空历史确认 `AlertDialog`
- 批量删除确认 `AlertDialog`
- 底部多选操作栏（内联在 `HistoryScreen`）

### 当前布局

- 顶部历史头卡：
  - 连接状态
  - “选择”按钮
  - 刷新按钮
  - 说明文字
  - 仅最终输出 / 全部图片 Chip
  - 清空历史按钮
  - temp 预览提示
- 历史卡：
  - 模板名或 Prompt 短码
  - 状态徽章
  - 时间、耗时
  - 最多 3 张缩略图
  - 图片数量
  - Prompt ID 短码
- 列表底部：
  - 加载更多按钮 / 没有更多历史
- 长按进入多选：
  - 卡片左侧复选框
  - 底部选择栏：已选数量、全选、取消选择、删除选中

### 状态

- 空 / 加载 / 刷新
- 断线 / 旧数据
- temp 过滤
- 多选模式
- 删除/清空失败 Snackbar
- 加载更多 / 没有更多

### 视觉痛点

- 头卡信息较多，连接、选择、刷新、过滤、清空都挤在一块
- 每张历史卡的元数据多，但层级接近
- 时间、耗时、图片数量、Prompt ID 的重要性不明确
- 缩略图尺寸和卡片高度在小屏上偏大
- 多选底部栏和 Snackbar 可能重叠
- 清空历史是危险操作，但视觉上与普通操作接近
- 历史卡片没有明显的“任务分组”视觉边界

---

## 7. 历史详情

### 文件

- `feature/history/HistoryDetailScreen.kt`
- `feature/history/ImagePreviewDialog.kt`

### 组件

- `HistoryDetailScreen`
- `HistoryDetailContent`
- 摘要卡：模板名、状态、Prompt ID、时间、耗时、再次生成、删除历史
- 错误信息卡
- 输出图片网格（2 列）
- 参数快照卡
- `ImagePreviewDialog`（全屏、缩放、翻页）
- 保存/分享 Snackbar 与权限流程

### 当前布局

- 顶部摘要卡
- 错误卡（失败任务）
- 输出图片卡：图片、文件名、保存、分享
- 参数快照卡
- 大图预览：全屏黑色背景、顶部文件名、页码、缩放提示、关闭按钮

### 状态

- 加载 / 找不到记录
- 成功 / 失败 / 中断
- 保存中 / 分享中 / 权限拒绝
- 删除中
- 图片加载中 / 失败

### 视觉痛点

- 摘要卡操作按钮较多（再次生成、删除历史），危险操作与普通操作并列
- 输出图片卡文件名较小，长文件名截断
- 参数快照是原始 key/value 文本，缺少折叠和可读性处理
- 错误信息的视觉层级可以更强
- 图片网格与卡片边界偏弱

---

## 8. 设置页

### 文件

- `feature/settings/SettingsScreen.kt`
- `feature/settings/SettingsViewModel.kt`

### 组件

- `SettingsScreen`
- 显示卡：主题 Chip、显示临时预览图 Switch
- 缓存卡：大小、刷新、清理缓存
- 诊断卡：说明、导出诊断信息
- 关于卡：版本、安全提示
- 清理缓存确认 `AlertDialog`
- Snackbar

### 当前布局

- `LazyColumn`，16dp 边距，12dp 间距
- 卡片顺序：显示 → 图片缓存 → 诊断 → 关于
- 主题使用三个 FilterChip
- 诊断导出通过系统分享面板

### 状态

- 缓存读取中 / 清理中
- 清理成功 / 失败
- 诊断生成中 / 生成失败

### 视觉痛点

- 设置项卡片结构统一，但视觉偏平
- 主题 Chip 和临时预览 Switch 在同一张卡，分组层级可以更清楚
- 危险操作“清理缓存”与普通按钮样式相同
- 关于卡信息较少，留白多
- 诊断卡偏“工具化”，缺少状态反馈

---

## 9. 蒙版编辑器

### 文件

- `feature/workflow/MaskEditorDialog.kt`

### 组件

- `MaskEditorDialog`（全屏 Dialog）
- `ImageSourcePicker`
- `MaskCanvas`
- `MaskTools`
- `renderMaskBitmap`

### 当前布局

- 顶部：标题“绘制蒙版” + 关闭按钮
- 未选图：历史输出图片 2 列网格
- 已选图：
  - 黑色画布区域
  - 图片按比例居中
  - 红色半透明蒙版笔迹
  - 底部工具区：
    - 画笔 / 橡皮 Chip
    - 重新选择图片
    - 画笔大小 Slider
    - 撤销 / 清空
    - 上传蒙版
    - 提示文字

### 状态

- 图片加载中
- 图片加载失败 / 超时
- 重试
- 上传中
- 上传失败
- 笔迹为空
- 画笔 / 橡皮

### 视觉痛点

- 工具区控件密集，底部占用高度较大
- 画笔大小 Slider 与模式 Chip 的层级不够清晰
- 画布没有缩放/平移，4K 图细节涂抹困难
- 画笔/橡皮状态只靠 Chip 选中态
- 上传按钮与撤销/清空按钮权重不同，但排列接近
- 图片加载失败/重试状态是后补的，样式还可统一
- 全屏 Dialog 的顶部栏与全局 TopAppBar 样式不完全一致

---

## 10. 共享视觉模式

### 卡片

- 大多数页面使用 `Card` + `Column(padding = 16.dp)`
- 页面级卡片间距：10dp / 12dp
- 卡片内部间距：6dp / 8dp / 10dp
- 缺少统一的卡片层级：普通信息卡 / 操作卡 / 危险卡 / 状态卡

### 状态徽章

- `StatusBadge`：成功/失败/中断
- 服务器状态：在线/未检测/离线
- 连接状态：实时连接/重连中/离线
- 置信度：高/中/低
- 目前各自实现，颜色和形状不完全统一

### 按钮

- `Button`：主操作
- `TextButton`：次级/文字操作
- `OutlinedButton`：次级/危险操作
- `IconButton`：刷新/更多/关闭
- 缺少统一的危险操作样式

### 表单

- `OutlinedTextField`
- `FilterChip`
- `Switch`
- `Slider`
- 错误提示通过 `supportingText` 或 Snackbar

### 空/错/加载

- 空状态：图标 + 标题 + 说明 + 操作按钮
- 加载：居中 CircularProgressIndicator
- 错误：Snackbar / 文本 / 卡片
- 没有统一骨架屏

---

## 11. 当前视觉问题优先级（建议审计顺序）

1. **全局 token 缺失**：颜色、间距、圆角、字号、状态色未统一
2. **页面层级偏平**：工作流详情、历史列表、队列最明显
3. **状态表达不统一**：在线/离线/成功/失败/警告/危险操作
4. **空/加载/错误状态风格不一致**
5. **图标体系不统一**，缺少空状态插画
6. **危险操作不突出**：清空历史、删除模板、清理缓存
7. **多选底部栏与 Snackbar 的层级/安全区**
8. **蒙版编辑器工具区密度与小屏适配**
9. **无障碍**：contentDescription、触控尺寸、文字缩放、色盲友好
10. **动效**：目前只有页面滑动，缺少列表更新和状态过渡

---

## 12. 设计约束

- Jetpack Compose + Material 3
- 不引入新的重型 UI 框架
- 保留现有 ViewModel / StateFlow / 业务逻辑
- 需要同时支持浅色、深色、动态色
- 中文文案放在 `res/values/strings.xml`
- 触控目标不小于 48dp
- 图片、状态、进度需要色盲友好
- 不改变 API-only 执行模型

---

## 13. 需要 Antigravity 重点输出

1. 设计 token：颜色、字号、行高、间距、圆角、阴影、图标尺寸
2. 组件规范：卡片、按钮、Chip、状态徽章、输入框、空/错/加载
3. 关键页面布局建议：服务器、工作流详情、队列、历史、蒙版编辑器
4. 状态矩阵：loading / empty / error / offline / disabled / selected / danger
5. 动效建议：页面进入、列表更新、选择模式、进度变化
6. 无障碍建议：对比度、触控、文字缩放、contentDescription
7. 可选：Compose token 代码草案或关键页面高保真示意

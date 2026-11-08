# NekoPaint 视觉审计与设计规范（Visual Audit & Design Spec）

> **版本**：v1.0
> **日期**：2026-11-08
> **作者**：Antigravity
> **交接对象**：OpenCode
> **配套 Token 文件**：`design/tokens.json`
> **约束**：严格遵循 Jetpack Compose + Material 3，不引入第三方重型 UI 库，保持现有 ViewModel / StateFlow / 业务逻辑不变，所有用户可见文案放入 `res/values/strings.xml`。

---

## 一、 全局视觉审计结论（Top 6 核心痛点）

通过对 `design/CURRENT_STATE.md`、9 张模拟器真机截图以及现有 Compose 源码的审查，当前 UI 的核心问题并非“功能缺失”，而是**容器层级扁平、状态语义色缺失、高频信息噪音大**：

1. **所有卡片长得一样（容器层级单一）**
   - 现状：页面内所有模块均使用默认 `Card()`（即 `surfaceVariant` 灰紫色底），导致“顶部状态横幅”、“主业务卡片”、“次级参数卡片”、“底部安全提示”视觉权重完全一致，一眼看去全是同色块堆叠。
   - 方案：将卡片拆分为 **4 种明确语义容器**（详见第三节）：主业务卡、当前高亮卡、次级/内嵌卡、状态/提示横幅。
2. **缺少独立的状态语义色（Semantic Status Tokens）**
   - 现状：在线、成功、实时连接混用 `primary`；重连中、未检测混用 `tertiary`；GPU 显存进度条无负载告警色；色盲用户或深浅色切换时辨识度不足。
   - 方案：在 `Theme.kt` 扩展 `NekoExtendedColors`（`success` / `warning` / `danger` / `info` 四组 Container + OnContainer），并**强制绑定差异化图标**（✓ / ⚠ / ✕ / ℹ），兼顾色盲友好。
3. **危险操作（Destructive Actions）与主操作无区分**
   - 现状：设置页“清理缓存”使用主色实心 `Button`；历史列表“清空历史”使用普通主色 `TextButton`；历史详情“删除历史”使用普通 `OutlinedButton`；确认弹窗的确认按钮也是普通蓝色。
   - 方案：所有删除/清空/中断操作统一使用 `danger` 语义色（详情见第三节按钮规范）。
4. **信息密度高但缺乏“降噪”与等宽字体区分**
   - 现状：工作流详情页的每个参数卡片同时展示 4 行字（中文标签 + `节点 4 · CLIPTextEncode` + `多行文本` + `高`），技术元信息喧宾夺主；历史卡片同时堆砌时间、耗时、张数、Prompt ID。
   - 方案：技术标识符（节点 ID、类名、Prompt ID、文件名、URL）统一使用 `FontFamily.Monospace` + `labelSmall` 胶囊或单行副标题，合并冗余行。
5. **顶部状态头卡（QueueHeader / HistoryHeader）过于臃肿**
   - 现状：队列页和历史页顶部都放了一张占屏近 1/4 的大卡片，塞满了连接状态、说明文案、筛选 Chip、操作按钮，把真正重要的任务/历史列表挤到了屏幕下方。
   - 方案：压缩为紧凑的 **Toolbar Strip + 筛选栏**，移除静态废话文案（如“浏览已完成任务与生成结果”），仅在断线/降级时展开异常提示条。
6. **小屏/手势区遮挡与工具栏密度问题**
   - 现状：工作流列表底部缺少 FAB 避让高度（`96.dp`），右下角“导入工作流”遮挡最后一张卡片；历史列表进入多选模式时，底部操作栏与 `SnackbarHost` 重叠；蒙版编辑器底部工具栏占高过大（4 行堆叠），挤压画布空间。

---

## 二、 设计 Token 接入规范（对应 `design/tokens.json`）

请 OpenCode 在 `core/ui/theme/` 下新增或扩展以下 Token 结构（不破坏现有 `MaterialTheme` 调用）：

### 1. 扩展语义色 `NekoSemanticColors`（通过 `LocalNekoColors` 提供）

| 语义角色 | Light Container / Content | Dark Container / Content | 适用场景 | 必配图标（色盲友好） |
| :--- | :--- | :--- | :--- | :--- |
| **Success** | `#D2F6DF` / `#00391D` (Accent: `#1B7F4B`) | `#00522C` / `#94F9B9` (Accent: `#78DC9F`) | 服务器在线、WebSocket 实时连接、任务成功、GPU 显存 < 70%、高置信度 | `Icons.Outlined.CheckCircle` |
| **Warning** | `#FFDFB3` / `#311B00` (Accent: `#9A6100`) | `#704400` / `#FFDFB3` (Accent: `#FFB951`) | 服务器未检测、重连中/轮询降级、工作流警告、GPU 显存 70%~90%、中置信度 | `Icons.Outlined.Warning` |
| **Danger** | `#FFDAD6` / `#410002` (Accent: `#BA1A1A`) | `#93000A` / `#FFDAD6` (Accent: `#FFB4AB`) | 离线/连接失败、任务失败、缺失节点错误、GPU 显存 > 90%、删除/清空/中断操作 | `Icons.Outlined.ErrorOutline` / `Delete` |
| **Info** | `#CDE5FF` / `#001D32` (Accent: `#006399`) | `#004A75` / `#CDE5FF` (Accent: `#95CCFF`) | 运行类型标签（如“蒙版重绘”）、本机/其他客户端来源标识、提示横幅 | `Icons.Outlined.Info` |

### 2. 间距、圆角与字阶（`NekoTokens`）

- **页面边距**：水平 `16.dp`，顶部 `12.dp`，带右下角 FAB 的列表底部 `contentPadding(bottom = 96.dp)`。
- **卡片间距**：列表项之间统一 `12.dp`；分组标题与首张卡片间距 `8.dp`，分组与分组之间 `20.dp`。
- **圆角层级**：
  - 一级卡片（服务器卡、工作流卡、历史卡）：`RoundedCornerShape(16.dp)`
  - 二级/内嵌容器（参数输入卡、GPU 子卡、代码块）：`RoundedCornerShape(12.dp)`
  - 缩略图：`RoundedCornerShape(8.dp)`
  - 状态徽章（StatusBadge）：`RoundedCornerShape(50)`（胶囊形）
- **字体排版（`Type.kt`）**：
  - 明确覆写 `titleLarge` (20sp SemiBold)、`titleMedium` (16sp SemiBold)、`titleSmall` (14sp SemiBold)、`bodyMedium` (14sp)、`bodySmall` (12sp)、`labelSmall` (11sp Medium)。
  - 新增辅助样式或扩展属性 `monoSmall`：`fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp`，专用于节点 ID、Prompt ID、URL、文件名。

---

## 三、 共享组件规范与状态矩阵（Component & State Matrix）

### 1. 四级卡片容器体系
1. **激活/焦点卡片（Active / Highlight Card）**
   - 场景：当前选中的服务器卡片、历史多选模式下被勾选的卡片、队列页“运行中”卡片。
   - 样式：`OutlinedCard`，背景 `colorScheme.surfaceContainerLow`（或 `primaryContainer.copy(alpha = 0.25f)`），边框 `BorderStroke(1.5.dp, colorScheme.primary)`。
2. **标准业务卡片（Standard Primary Card）**
   - 场景：非当前服务器卡、工作流列表卡、未选中的历史卡。
   - 样式：`Card`，背景 `colorScheme.surfaceContainer`（替代原先全灰紫的 `surfaceVariant`，更清爽），圆角 `16.dp`，内边距 `16.dp`。
3. **紧凑/参数卡片（Compact / Parameter Card）**
   - 场景：工作流详情页的每个参数项、服务器展开后的 GPU 信息块、设置项分组卡。
   - 样式：`Surface` 或 `Card`，背景 `colorScheme.surfaceContainerLow`，边框 `BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))`，圆角 `12.dp`，内边距 `12.dp`。
4. **提示/横幅卡片（Banner / Notice Card）**
   - 场景：服务器底部“安全提示”、断线/轮询降级提示、工作流兼容性告警。
   - 样式：不使用普通业务卡底色！根据严重程度使用 `warningContainer` / `dangerContainer` / `surfaceContainerHighest`（`alpha = 0.6f`），圆角 `12.dp`，内边距 `12.dp`，左侧固定 `20.dp` 状态图标。

### 2. 统一状态徽章 `NekoStatusBadge`
抽取公共组件，消除各页面手写 `Surface(RoundedCornerShape(50))` 的不一致：
- **高度**：`24.dp`，水平内边距 `8.dp`，图标 `14.dp`，间距 `4.dp`，文字 `labelSmall (SemiBold)`。
- **状态映射**：
  - `Online / Connected / Success` → `SuccessContainer` 底 + `OnSuccessContainer` 字 + `CheckCircle` 图标
  - `Connecting / Reconnecting / Untested / Interrupted` → `WarningContainer` 底 + `OnWarningContainer` 字 + `Warning` / `Sync` 图标
  - `Offline / Disconnected / Failed` → `DangerContainer` 底 + `OnDangerContainer` 字 + `ErrorOutline` 图标
  - `Neutral / Info`（如“当前”、“本机”）→ `PrimaryContainer` 底 + `OnPrimaryContainer` 字

### 3. 按钮与危险操作层级
- **主操作（Primary CTA）**：实心 `Button`（如“提交生成”、“保存并连接”、“再次生成”、“上传蒙版”）。
- **次级操作（Secondary）**：`FilledTonalButton` 或 `OutlinedButton`（如“测试连接”、“查看原始 JSON”、“重新选择图片”）。
- **危险操作（Destructive）**：
  - 页面内按钮（如设置页“清理缓存”、历史详情“删除历史”、队列页“中断”）：使用 `OutlinedButton`，边框色 `colorScheme.error.copy(alpha = 0.5f)`，文字与图标色 `colorScheme.error`。
  - 顶栏/工具栏文字操作（如历史页“清空历史”）：`TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error))`。
  - 删除/清空/中断确认弹窗（`AlertDialog`）：确认按钮必须使用 `Button(colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError))`。

### 4. 空状态 / 加载 / 错误状态矩阵

| 状态 | 视觉规范 |
| :--- | :--- |
| **Loading（首屏加载）** | 居中 `CircularProgressIndicator(strokeWidth = 3.dp)` + 下方 `8.dp` 处 `bodySmall` 状态文字（如“正在同步队列…”），避免空白转圈。 |
| **Empty（空状态）** | 抽取出公共组件 `NekoEmptyState(icon, title, subtitle, actionLabel, onAction)`：顶部 `56.dp` 圆形淡色底（`surfaceContainerHigh`）内嵌 `28.dp` 图标，`titleMedium` 标题 + `bodySmall` 灰色说明 + 可选主操作按钮，上下留白 `48.dp`。队列为空也改用此组件，替代单调的一行字卡片。 |
| **Offline / Degraded（断线降级）** | 在队列/历史顶部显示紧凑横幅（`WarningContainer` 底 + 图标 + “实时连接已断开，正每 2 秒轮询刷新”），不混在业务说明文字里。 |
| **Selected（多选态）** | 卡片左侧平滑出现 `Checkbox`，卡片背景变为 `primaryContainer.copy(alpha = 0.18f)` 并带 `1.5.dp` 主色描边。 |

---

## 四、 关键页面逐页改进清单（Screen-by-Screen Specs）

### 1. 服务器页（`ServerScreen.kt`）
- **区分当前服务器与普通服务器**：当前激活的服务器卡片（`card.isActive == true`）使用 `1.5.dp` 主色边框（`BorderStroke(1.5.dp, colorScheme.primary)`），让用户一眼识别当前连接目标。
- **展开区域分组层次**：展开后在操作按钮行下方加 `HorizontalDivider(color = outlineVariant.copy(alpha = 0.5f))`；“GPU 状态”与“系统信息”使用 `titleSmall` + `colorScheme.primary` 作为小节标题。
- **GPU 显存进度条语义色**：根据显存占用率动态切换颜色：`< 70%` 用 `Success`（或 `primary`），`70% ~ 90%` 用 `Warning`，`> 90%` 用 `Danger`，并在右侧用等宽字体显示百分比（如 `6.2 / 12.0 GB (52%)`）。
- **底部安全提示降重**：`SecurityNotice` 改用轻量横幅样式（`surfaceContainerLow` 底 + 细描边 + `bodySmall` 文字），降低视觉干扰，突出上方的服务器卡片。

### 2. 工作流列表页（`WorkflowsScreen.kt`）
- **修复 FAB 遮挡 bug**：`LazyColumn` 的 `contentPadding` 改为 `PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)`，防止右下角“导入工作流”FAB 遮挡最后一张卡片。
- **卡片信息降噪与视觉锚点**：
  - 第一行：左侧工作流标题（`titleMedium`），右侧溢出菜单 `IconButton`（补齐 `48.dp` 触控区与 `contentDescription`）。
  - 第二行：文件名使用 `monoSmall`（`onSurfaceVariant` 色）。
  - 第三行：将原本孤立的三个数字（`节点 9 / 可达节点 9 / 已选参数 9`）改为**紧凑横向统计条（Stat Pills）**，例如：`[9 节点]` · `[9 可达]` · `[9 可调参数]`，其中“可调参数”用 `primaryContainer` 浅底高亮，突出用户最关心的数字。
  - 缺失节点警告：若存在缺失节点，使用 `WarningContainer` 小横幅展示，不与普通文字混排。

### 3. 工作流详情页（`WorkflowDetailScreen.kt`）—— **重点减负**
- **顶部摘要与兼容性整合**：
  - `SummaryCard` 压缩高度：标题与“查看原始 JSON”（改为右上角 `OutlinedButton` 或带代码图标的紧凑按钮）同行或紧凑排列；节点统计使用与列表页一致的单行胶囊条。
  - `CompatibilityCard`：将运行类型（如“蒙版重绘”）做成醒目的 `Info` 胶囊徽章放在卡片标题右侧；外部图片槽/蒙版槽用紧凑列表展示；问题列表按 `WorkflowIssueSeverity` 区分图标与颜色（`ERROR` 用红色 `ErrorOutline`，`WARNING` 用琥珀色 `Warning`，`INFO` 用蓝色 `Info`），替代单调的 `•` 纯文本。
- **模式切换与提交栏**：
  - 将“简单模式 / 高级模式”切换与右侧“编辑”按钮放在同一吸顶/紧凑控制行；
  - 页面内的“提交生成”按钮改为**全宽醒目主按钮**（`Modifier.fillMaxWidth().height(48.dp)`），带生成图标（如 `Icons.Outlined.PlayArrow` 或 `AutoAwesome`），避免右侧孤零零一个小椭圆按钮显得失衡。
- **参数卡片（`ParameterField` / `CandidateRow`）降噪**：
  - 合并原本占 3 行的技术头部！改为**单行头部**：左侧参数中文名（`titleMedium`），右侧将 `节点 4 · CLIPTextEncode` 与置信度徽章合并为紧凑的 `monoSmall` 副标题（简单模式下甚至可弱化类型文案“多行文本/整数”，因为输入框本身已自解释）。
  - 分组标题（`提示词`、`采样`、`模型` 等）：上方增加 `12.dp` 间距，左侧加一根 `3.dp × 14.dp` 的主色竖条（Accent Bar）或使用 `titleSmall` + `primary` 色，拉开分组边界。
  - 图片/蒙版参数卡：将“上传图片”和“从历史图片绘制蒙版”做成并排的 `FilledTonalButton` + 图标，提升可发现性。

### 4. 队列页（`QueueScreen.kt`）
- **精简 `QueueHeader`**：去掉冗余说明文字“查看运行中与等待中的任务”；第一行展示连接状态徽章 + `队列剩余: N` + 右侧“历史记录”按钮与刷新按钮；仅当 `connection != Connected` 或 `error != null` 时，在下方展开告警横幅。
- **强化 `RunningCard`**：使用激活卡片样式（主色描边 + `surfaceContainerLow`），进度条加粗到 `8.dp` 圆角进度条，右侧大字号显示百分比（如 `65%`），下方用 `monoSmall` 显示当前执行节点与 `step/max`。
- **空状态**：队列为空时使用统一的 `NekoEmptyState`（图标 + “当前没有运行或等待中的任务” + “去历史记录查看”次级入口）。

### 5. 历史列表与历史详情（`HistoryScreen.kt` / `HistoryDetailScreen.kt`）
- **精简 `HistoryHeader`**：
  - 移除静态说明“浏览已完成任务与生成结果”。
  - 第一行：左侧连接状态徽章 + `仅最终输出 / 全部图片` 切换 Chip；右侧“选择”按钮、刷新图标、“清空历史”（改为 `error` 红色文字按钮或放入更多菜单，防止误触）。
  - `temp` 预览提示改为单行 `labelSmall` 浅灰提示。
- **重构 `HistoryCard` 布局（左图右文 / 紧凑双栏）**：
  - 当只有 1 张输出图（最常见情况）时，目前 `112.dp` 缩略图独占一行导致卡片过高且右侧大片空白。建议：
    - **顶部行**：左侧任务名（`titleMedium`），右侧 `NekoStatusBadge`（成功/失败/中断）。
    - **主体区**：若 1 张图，采用**左图（`72.dp × 72.dp` 圆角缩略图）+ 右侧元数据列**（时间、耗时、`monoSmall` Prompt ID）；若 2~3 张图，采用紧凑的 `80.dp` 缩略图横排，元数据合併为单行 `2026-11-08 09:25 · 耗时 22s · 3 张 · #cdb159c3`。
- **修复多选底部栏与 Snackbar 冲突**：
  - `SnackbarHost` 的底部边距在 `selectionMode == true` 时动态增加 `72.dp`（即 `Modifier.padding(bottom = if (selectionMode) 88.dp else 16.dp)`），彻底避免底部选择栏遮挡提示条。
  - 底部选择栏中的“删除选中”按钮改用 `danger` 实心色（`containerColor = MaterialTheme.colorScheme.error`）。
- **历史详情页（`HistoryDetailScreen.kt`）**：
  - 顶部摘要卡：“再次生成”（主按钮 `Button`）与“删除历史”（危险次级按钮 `OutlinedButton` + `error` 色）改为**同行并排**（`Row` + `weight(1f)`），节省垂直空间并拉开主次对比。
  - 参数快照卡：使用 `surfaceContainerLowest`（代码块风格底色）+ `monoSmall` 字体，每行 key（浅灰）与 value（深色）对齐，超过 8 行时支持展开/折叠。

### 6. 设置页（`SettingsScreen.kt`）
- **危险按钮修正**：“图片缓存”卡中的“清理缓存”按钮由实心主色 `Button` 改为 `OutlinedButton`（`error` 色描边与文字），避免看起来像全页最重要的主推操作。
- **分组内部分隔**：“显示”卡中的“主题”行与“显示临时预览图”开关之间增加 `HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))`，理清两个独立设置项的边界。

### 7. 蒙版编辑器（`MaskEditorDialog.kt`）—— **小屏与工具栏减高**
- **压缩底部工具区高度（从 4 行减为 2 行紧凑布局）**：
  - **第一行（工具与笔刷粗细同行）**：左侧紧凑分段按钮/Chip（`画笔` / `橡皮`），中间直接放 `Slider`（画笔大小）+ 数值显示，右侧放“重选图片”图标按钮。
  - **第二行（历史与提交操作）**：左侧 `IconButton` 或紧凑 `OutlinedButton`（`撤销`、`清空`），右侧主按钮 `Button`（`上传蒙版`）。
  - 底部说明文字压缩为单行 `11.sp` 居中弱提示，整体为画布多释放约 `70.dp` 的垂直可视空间！
- **选图网格（`ImageSourcePicker`）**：图片底部文件名栏使用 `monoSmall` 单行居中截断，增加图片长宽比一致性（`aspectRatio(1f)` 裁剪预览）。

---

## 五、 动效与无障碍落地清单（Motion & Accessibility Checklist）

### 1. 轻量动效（不拖慢高刷屏）
- **服务器卡片展开/折叠**：在 `ServerListCard` 的 `Column` 上添加 `Modifier.animateContentSize(animationSpec = tween(220))`。
- **历史多选栏进出场**：使用 `AnimatedVisibility(visible = selectionMode, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut())`。
- **队列进度平滑过渡**：`RunningCard` 中的 `LinearProgressIndicator` 进度值使用 `animateFloatAsState(targetValue = progressFraction, animationSpec = tween(150))`。

### 2. 无障碍（Accessibility）硬性检查点
- **补齐 `contentDescription`**：检查所有 `IconButton`（刷新、更多操作、展开折叠、关闭蒙版编辑器）以及状态徽章图标，全部绑定 `strings.xml` 中文描述。
- **48dp 最小触控目标**：工作流卡片右上角溢出菜单、队列页刷新按钮、蒙版编辑器撤销/清空按钮，确保外层可点击区域 `>= 48.dp`。
- **色盲友好双重编码**：所有状态徽章（在线/离线/成功/失败/警告）和兼容性提示必须**同时具备颜色 + 形状各异的图标**，不得仅靠红绿颜色区分。

---

## 六、 建议 OpenCode 分阶段实施顺序（Implementation Phases）

为了保证每次改动小步可验证、不破坏现有未提交代码，建议 OpenCode 按以下 3 个批次落地：

1. **Batch 1（基础设计系统与公共组件）**
   - 更新 `Color.kt`、`Type.kt`、`Theme.kt`，接入 `design/tokens.json` 中的语义色、字阶与圆角间距常量。
   - 新增公共组件（如 `core/ui/component/NekoStatusBadge.kt`、`NekoEmptyState.kt`）。
2. **Batch 2（核心高频页面打磨：服务器 + 工作流列表/详情 + 设置）**
   - 落地服务器激活态描边、GPU 显存告警色、安全提示降重；
   - 修复工作流列表 FAB 遮挡、升级统计胶囊；
   - 落地工作流详情参数卡头部合并降噪、兼容性严重度分级、全宽提交按钮；
   - 修正设置页危险按钮样式。
3. **Batch 3（状态密集与工具页面：队列 + 历史列表/详情 + 蒙版编辑器）**
   - 精简队列与历史顶部 Header 卡、重构历史卡片紧凑布局、修复多选栏与 Snackbar 重叠；
   - 落地历史详情并排操作按钮与参数快照等宽样式；
   - 压缩蒙版编辑器底部工具栏为 2 行紧凑布局。

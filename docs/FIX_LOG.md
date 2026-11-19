# 修复日志

## 第 1 批：CR-001 / CR-002 / CR-004

> 修复日期：2026-11-15  
> 分支：`fix/security-data-correctness`  
> 基于：`master@354c7b3`  
> 状态：**已实现并完成本地自动验证；等待独立代码审核。尚未提交 Git commit。**

### 1. 范围

本轮只处理：

- CR-001：AI Base URL 非法时崩溃
- CR-002：切换 AI 服务商时保留旧 API Key
- CR-004：AI API Key 通过明文 HTTP 发送

未包含：

- CR-003（AI 第三方数据披露、脱敏、隐私文档、跨设置页保存拆分）
- CR-006、CR-007、CR-014、CR-017、CR-019

### 2. 修改文件

#### 新增生产代码

- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/network/AiEndpointValidator.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/network/AiCredentialPolicy.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/network/AiEndpointPolicy.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/model/ai/AiHttpConsent.kt`

#### 修改生产代码

- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/network/AiAssistantClient.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/model/ai/AiAssistantConfig.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/datastore/AiAssistantStore.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/di/AppContainer.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/feature/settings/AiAssistantSettingsViewModel.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/feature/settings/AiAssistantSettingsScreen.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/feature/settings/AiProviderPreset.kt`
- `app/src/main/res/values/strings.xml`

#### 新增测试

- `app/src/test/java/io/github/xiaowu20245/nekopaint/core/network/AiEndpointValidatorTest.kt`
- `app/src/test/java/io/github/xiaowu20245/nekopaint/core/network/AiEndpointPolicyTest.kt`
- `app/src/test/java/io/github/xiaowu20245/nekopaint/core/network/AiEndpointConsistencyTest.kt`
- `app/src/test/java/io/github/xiaowu20245/nekopaint/core/network/AiCredentialPolicyTest.kt`
- `app/src/test/java/io/github/xiaowu20245/nekopaint/core/network/AiAssistantClientUrlTest.kt`
- `app/src/test/java/io/github/xiaowu20245/nekopaint/core/network/AiAssistantClientCredentialTest.kt`

### 3. 实际实现

#### CR-001

- 新增共享 `AiEndpoint`、`AiAddressClass` 和 `AiEndpointValidator`。
- 使用 OkHttp `HttpUrl` 解析 Base URL。
- 拒绝空地址、空白、反斜杠、非法 IPv4、非法端口、query、fragment/userinfo。
- 统一使用 `HttpUrl.Builder` 生成 `/chat/completions`，完整 endpoint 不重复拼接。
- `AiAssistantClient.executeChat()` 将 URL、`Request`、实际请求全部放入异常边界。
- UI 增加 Base URL 错误提示；非法地址无法测试/保存。
- `AiAssistantConfig.isConfigured` 保持“必填字段非空”语义，安全校验由 validator 和客户端执行。

#### CR-002

- `AiAssistantConfig` 增加 `apiKeyOrigin`，`AiAssistantStore` 持久化该字段。
- `AiCredentialPolicy` 统一判断：
  - 无 Key：无需处理；
  - 缺少 origin：fail-closed；
  - origin 相同：允许；
  - origin 不同：要求确认清空；
  - 新地址非法：阻止请求。
- `AiAssistantClient` 在请求前再次校验 `apiKeyOrigin`，旧 Key 不能跨 origin 发送。
- 设置页：
  - 预设切换、手动改 origin 不静默清空 Key；
  - 保存/测试时通过确认对话框处理绑定或清空；
  - 用户取消编辑时保留原 Key；
  - 旧配置缺少 `apiKeyOrigin` 时显示警告并要求显式绑定或重输。
- 删除未使用的 `AiProviderPreset.toConfig(currentKey)`，避免误用旧 Key。

#### CR-004

- 新增 `AiEndpointPolicy`：
  - 公网 HTTP 无论有无 Key、是否信任均阻止；
  - 回环/私网 HTTP 无 Key 允许并显示明文警告；
  - 回环/私网 HTTP + Key 需要 `httpKeyApprovedOrigins` 显式确认；
  - `.local`/单标签域名归为 `LocalDomain`，默认阻止，`httpTrustedOrigins` 显式信任后按私网处理，带 Key 时仍需 Key 确认；
  - 信任按精确 origin 记忆。
- `AiAssistantStore` 新增 `httpConsent`、`trustHttpOrigin()`、`approveHttpKeyOrigin()`；清除 AI 配置时一并清理。
- AI OkHttp 客户端与 `AiAssistantClient` 都关闭自动重定向，3xx 返回受控错误。
- 设置页增加 HTTP 安全提示、确认对话框和错误状态。

### 4. 测试与构建结果

实际执行：

```text
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
git diff --check
```

结果：

| 项目 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest` | **通过**：61 个测试套件，297 tests，0 failures，0 errors |
| `:app:lintDebug` | **通过**：0 errors / 9 warnings / 3 hints |
| `:app:assembleDebug` | **成功** |
| `git diff --check` | **通过**（仅 LF/CRLF 提示，无空白错误） |

新增测试数：45。

Baseline：审查前的 252 tests / 0 failures；本批后为 297 tests / 0 failures。

### 4.1 第 1 批代码复审修复（B1-01～B1-07）

- **B1-01**：`onApiKeyChange()` 不再在 Base URL 非法时沿用旧 `apiKeyOrigin`；新 Key 只有在当前 endpoint 合法时才绑定当前 origin。
- **B1-02**：抽出纯逻辑 `AiAssistantSettingsPolicy`，并新增 `AiAssistantSettingsPolicyTest`，覆盖非法 URL、旧 Key 缺失 origin、同 origin/跨 origin 预设、公网 HTTP 阻断和旧 Base URL 非法不可绑定。
- **B1-03**：`AiAssistantFormState` 增加 `policyBlocked`；公网 HTTP 等硬阻断时 `canSubmit=false`，测试/保存按钮不可点击。
- **B1-04**：`applyPreset()` 通过 `presetRequiresKeyClear` 判断；同 origin 且旧 Key 缺少 origin 时保留 Key，跨 origin 才提示清空。
- **B1-05**：`originalBaseUrlValid` 记录加载时原始 Base URL 是否合法；旧地址非法时只允许清空/重输，不提供“绑定到新地址”。
- **B1-06**：策略阻断提示仍部分硬编码在 ViewModel，未全部迁移到 `strings.xml`；已在本日志列为未完成项。策略阻断提示颜色已改为 error 语义色。
- **B1-07**：新增 `AiAssistantClientPolicyTest`，验证公网 HTTP、未信任 `.local`、私网 HTTP + 未确认 Key 均不经过 transport；确认后请求可到达 transport。

新增/修改相关文件：

- `app/src/main/java/io/github/xiaowu20245/nekopaint/feature/settings/AiAssistantSettingsPolicy.kt`
- `app/src/test/java/io/github/xiaowu20245/nekopaint/feature/settings/AiAssistantSettingsPolicyTest.kt`
- `app/src/test/java/io/github/xiaowu20245/nekopaint/core/network/AiAssistantClientPolicyTest.kt`
- `AiAssistantSettingsViewModel.kt`
- `AiAssistantSettingsScreen.kt`

### 4.2 第 1 批复审修复（B2-01～B2-05）

- **B2-01**：新增 `originalApiKey` 和 `keyEdited`；`canBindLegacyKey(originalBaseUrlValid, keyEdited)` 区分旧 Key 与用户新输入 Key。新 Key 在原始 Base URL 非法时仍可显式绑定到当前合法地址；旧 Key 仍 fail-closed。
- **B2-02**：抽取 `AiAssistantSettingsStore`、`AiAssistantSettingsClient` 接口；新增 `AiAssistantSettingsViewModelTest`，使用 fake store/client 覆盖绑定、清空、取消、保存持久化、公网阻断和 consent 写入失败流程。
- **B2-03**：`trustHttpOrigin()` / `approveHttpKeyOrigin()` / `currentHttpConsent()` 包在 `runCatching` 中；失败显示错误，不继续请求。
- **B2-04**：文案迁移仍推迟，已明确记录为未完成项。
- **B2-05**：`prepareRequest()` 在 `policyBlocked=true` 时使用策略消息，不再误报 Base URL 格式错误。

新增/修改相关文件：

- `core/datastore/AiAssistantSettingsStore.kt`
- `core/network/AiAssistantSettingsClient.kt`
- `feature/settings/AiAssistantSettingsPolicy.kt`
- `feature/settings/AiAssistantSettingsViewModel.kt`
- `app/src/test/.../feature/settings/AiAssistantSettingsPolicyTest.kt`
- `app/src/test/.../feature/settings/AiAssistantSettingsViewModelTest.kt`

### 5. 手工验收状态

已完成模拟器 UI smoke test（设备 `emulator-5554`，AVD `Medium_Phone_API_37.0`；adb 位于 `C:\Users\xiaowu\AppData\Local\Android\Sdk\platform-tools\adb.exe`，此前只是未加入 PATH）：

1. 非法 Base URL：显示“Base URL 格式无效，请检查地址”，测试/保存按钮不可点击。
2. 旧 Key 缺失 origin：显示绑定提示，测试时出现“确认 AI 配置变更”对话框；取消后不绑定、不保存。
3. 公网 HTTP：显示阻断提示，测试/保存按钮不可点击。
4. 预设切换：缺失 origin 时出现清空确认，取消后保持原配置。
5. Ollama HTTP + Key：显示明文 HTTP 警告，测试时出现 Key 二次确认；取消后不发送、不保存。
6. 真实保存：确认绑定后保存成功，`apiKeyOrigin` 写入 DataStore。
7. 杀进程重开：Base URL、Key 和绑定状态保留。
8. 已备份并还原模拟器 DataStore；未留下测试配置修改。

真实 AI 服务响应仍未连接；MockWebServer 已覆盖请求路径、请求头、重定向和策略阻断。

### 6. 与计划的差异

1. 新增 `AiEndpointConsistencyTest`，用于验证 credential 和 transport policy 使用同一 origin。
2. 设置页错误/警告文案暂用 ViewModel 内的中文提示，未全部迁移到 `strings.xml`；弹窗标题、按钮使用资源字符串。
3. `AiAssistantStore.updateServiceConfig` / `updatePersonaInstruction` 尚未实现，属于 CR-003 第 2 批范围；当前设置页保存仍可能整体写回缓存的人设/预指令，**该已知问题保留到第 2 批修复**。
4. 未实现 CR-003 的首次披露、隐私文档、脱敏和 AI 配置清除 UI。

### 7. 未解决/低优先级事项

- CR-003、CR-006、CR-007、CR-014、CR-017、CR-019 仍未实现，进入后续批次。
- B1-06/B2-04：部分用户可见文案仍硬编码在 ViewModel，未全部迁移到 `strings.xml`。
- B3-02：少量 ViewModel 路径未直接覆盖：`OriginChanged` 确认清空、`NeedsLocalTrust`/`NeedsKeyApproval` 成功路径、`applyPreset` ViewModel 状态、`testConnection` 失败、`store.save` 失败。
- B3-03：`originalApiKey` 使用未 trim 的 UI 值；低优先级整洁性问题。
- 真实 AI 服务/真实 ComfyUI 未连接；只使用了 MockWebServer。

独立复审结论：第 1 批代码与自动验证 **PASS**；模拟器 UI smoke test **PASS**；真实 DataStore 保存/重启/还原 **PASS**；批次验收 **PASS**。

### 8. Git 状态

```text
分支：fix/security-data-correctness
基线：354c7b3
第一批代码：已实现，待提交
文档：CODE_REVIEW.md / FIX_PLAN.md / FIX_REVIEW.md 未跟踪，不纳入本批提交
```

第 1 批提交范围：本日志、AI 相关生产代码和测试；不包含 `docs/CODE_REVIEW.md`、`docs/FIX_PLAN.md`、`docs/FIX_REVIEW.md`。

---

## 第 2 批：CR-003 AI 第三方数据披露、脱敏与隐私文案

> 修复日期：2026-11-15
> 分支：`fix/security-data-correctness`
> 基于：第 1 批提交 `b86fde6`
> 状态：已实现并完成 B5/B6 复审修复与自动验证；独立复审 **APPROVED**（`docs/FIX_REVIEW.md` 第 19 节，审核报告未纳入本批提交）；真实服务未连接。第 2 批 commit 已提交。

### 9. 范围与修改

本轮处理：

- AI 第三方数据披露与 origin 级确认
- 未确认时阻止测试/实际请求
- AI 客户端最终披露校验
- 系统/工具上下文脱敏
- AI 服务字段与 Neko 人设字段拆分保存
- “AI 未启用/本地兜底”状态与清除配置入口
- 关于页、README、PRODUCT、REQUIREMENTS、DECISIONS 隐私说明

新增生产文件：

- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/model/ai/AiDisclosurePolicy.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/network/AiPrivacySanitizer.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/privacy/SensitiveHostProvider.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/data/repository/ServerSensitiveHostProvider.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/datastore/NekoSettingsPreferences.kt`

修改生产文件：

- `core/datastore/AiAssistantStore.kt`
- `core/datastore/AiAssistantSettingsStore.kt`
- `core/datastore/SettingsStore.kt`
- `core/network/AiAssistantClient.kt`
- `di/AppContainer.kt`
- `feature/settings/AiAssistantSettingsViewModel.kt`
- `feature/settings/AiAssistantSettingsScreen.kt`
- `feature/settings/NekoSettingsViewModel.kt`
- `feature/chat/ChatContextBuilder.kt`
- `feature/chat/ChatToolRegistry.kt`
- `feature/chat/ChatViewModel.kt`
- `res/values/strings.xml`
- `REQUIREMENTS.md`
- `PRODUCT.md`
- `README.md`
- `DECISIONS.md`

新增测试：

- `core/model/ai/AiDisclosurePolicyTest.kt`
- `core/network/AiPrivacySanitizerTest.kt`
- `data/repository/ServerSensitiveHostProviderTest.kt`
- `feature/settings/CrossSettingsSaveTest.kt`
- `AiAssistantClientPolicyTest`（补充未确认披露请求数为 0）
- `AiAssistantSettingsViewModelTest`（补充披露确认流程、清除配置）

### 10. 实现说明

- `AiDisclosurePolicy` 使用 `origin + 披露版本` 记录确认；origin 变化或版本升级需要重新确认。
- `AiAssistantStore` 新增 `currentDisclosureAcknowledgements()`、`acknowledgeDisclosure()`；清除 AI 配置时一并清理。
- 设置页增加第三方数据披露卡片和“我已知晓并继续”；未配置时显示本地兜底状态。
- 测试连接在未确认披露时先弹出确认框；保存本地配置仍允许，但实际 AI 请求由客户端最终阻止。
- `AiAssistantClient` 在 URL、凭据、HTTP 策略校验后检查披露状态，未确认时请求数为 0。
- `AiPrivacySanitizer` 脱敏 Windows/UNC/Unix home 路径、URL、IPv4/IPv6 和当前服务器 host；短 host 不参与裸文本替换。
- `SensitiveHostProvider` 第 2 批只提供当前 `servers`；归档主机 provider 扩展留到 CR-006 第 4 批。
- `ChatContextBuilder` 和 `ChatToolRegistry` 的输出统一脱敏；用户主动输入的提示词正文不经过路径规则改写。
- `AiAssistantStore.updateServiceConfig` / `updatePersonaInstruction` 拆分保存；AI 设置页保存不再覆盖 Neko 人设，Neko 设置也不再覆盖 AI 服务字段。
- 设置页增加“清除 AI 配置”，同时清理 Key、origin、HTTP 信任和披露确认。

### 10.1 第 2 批复审修复（B4-01～B4-05）

- **B4-01**：`AiAssistantClient.disclosureProvider` 默认改为 `{ false }`；相关客户端测试显式传入 `{ true }`，新增默认拒绝测试。
- **B4-02**：Windows/UNC 路径规则改为在逗号、中文标点、分号或参数起始处截断；增加路径后跟 `seed/steps/cfg` 的测试。
- **B4-03**：新增裸 IPv6 匹配；Unix 路径规则覆盖 `/data/`、`/storage/`、`/sdcard/`、`/mnt/`；增加测试。
- **B4-04**：新增 `requestClearConfig` 的确认、取消、成功和失败 ViewModel 测试；修正本日志关于清除配置测试覆盖的描述。
- **B4-05**：增加披露写入失败、清除配置失败、路径+参数、裸 IPv6、Android 路径等测试；新增 `AiAssistantClientPrivacyTest`，直接检查实际 MockWebServer 请求体中的系统/工具内容已脱敏。
- **B4-07**：披露确认写入成功后直接更新本地确认集合，不再依赖写后读回；读回失败不会再次弹确认。
- B4-06/B4-08 仍作为低优先级残余项记录。

### 10.2 第 2 批复审修复（B5-01～B5-04）

- **B5-01**：`ServerSensitiveHostProvider` 改为可注入 `Flow<List<ServerProfile>>`，保留 `ServerConfigStore` 便捷构造；新增 `awaitReady()`，在首个 emission 前挂起。`AiPrivacySanitizer.awaitReady()` 转发该等待，`ChatViewModel.send()` 在组装请求前等待，关闭首条请求的空 host 窗口。新增 `ServerSensitiveHostProviderTest`，覆盖首轮 host、多服务器、更新后替换和空配置。
- **B5-02**：抽取 `NekoSettingsPreferences` seam，`NekoSettingsViewModel` 改用 `AiAssistantSettingsStore` / `NekoSettingsPreferences` 接口。新增 `CrossSettingsSaveTest`，用共享 fake store 覆盖两个顺序：先保存人设再保存 AI 服务字段、先保存 AI 服务字段再保存人设；断言互不覆盖。
- **B5-03**：发送历史消息时 `ChatViewModel` 对 `assistant` 内容调用 sanitizer；`AiAssistantClient.outboundContent()` 同时覆盖 `system` / `tool` / `assistant`，用户消息仍保留原文。`AiAssistantClientPrivacyTest` 增加 assistant 历史路径断言。
- **B5-04**：`AiAssistantClient.privacySanitizer` 改为无默认值的显式必传参数；生产 `AppContainer`、`ChatViewModel` 工厂和全部客户端测试显式注入。避免未注入调用方在 host 集合为空时静默不脱敏。
- **B5-05（未解决，低优先级）**：部分 ViewModel 错误/确认文案仍硬编码，沿用 B1-06/B2-04 记录，不阻塞本批。

### 10.3 第 2 批 B6 复审修复（B6-01～B6-09）

- **B6-01**：`ServerSensitiveHostProvider` 的 flow 失败、未发射即完成或超时都会让 `awaitReady()` 返回 `false`，不再永久挂起；失败不再逃逸到 `appScope`。`ChatViewModel.send()` 在等待失败时调用新的 `failUserMessage()`，清除 `sending`、标记用户消息错误并追加受控错误提示。
- **B6-02**：路径正则把双引号纳入边界，避免 JSON 工具参数被整段吞掉；`redactPath()` 在文件扩展名后保留中文诊断文本（如 `<本地路径> 处理失败`），但不保留无扩展名目录中的空格片段。
- **B6-03**：`AiAssistantClient` 的 `tool_calls[].function.arguments` 也经过 sanitizer；实际请求体测试解析嵌套 JSON，断言路径/host 已脱敏且 `cfg` 字段结构保留。
- **B6-04**：明确 `ServerConfigStore` 首次 emission 是完整 DataStore 快照，空列表即“当前无服务器”；新增“首个空快照放行、后续 host 更新”时序测试，避免把正常空配置误判为永久等待。
- **B6-05**：`AiAssistantClient.executeChat()` 在构建请求体前统一等待 `privacySanitizer.awaitReady()`；失败时返回 `Result.failure`，不产生网络请求。新增“等待期间请求数为 0、放行后 host 被脱敏”和“provider 不可用时请求数为 0”两个实际客户端测试，覆盖 `testConnection`、翻译/润色和工具调用的共同入口。
- **B6-06**：`ServerSensitiveHostProviderTest` 增至 7 个测试，覆盖首个 emission 前挂起、flow 抛异常、流结束无 emission、永不发射超时、首个空快照后更新、多服务器和更新替换。`ChatViewModel.send()` 失败收口因依赖具体 Android 类未新增直接单测，由代码路径和 provider 测试共同覆盖。
- **B6-07**：移除 `AiPrivacySanitizer`、`ChatViewModel`、`ChatContextBuilder`、`ChatToolRegistry` 的空 host 默认构造；所有调用方（含测试）显式传入 sanitizer，测试用 `SensitiveHostProvider { emptySet() }` 明确表达“无 host”。
- **B6-08（低，未解决）**：`CrossSettingsSaveTest` 仍使用共享 fake store；真实 `AiAssistantStore` 需要 Android Context/DataStore，本轮未新增真实存储回归，已记录为残余。
- **B6-09**：删除 AI 设置页不再保存的 `onPersonaChange` / `onPreInstructionChange` 和 `AiAssistantFormState` 中的 persona/preInstruction 字段，避免未来复用入口时静默不保存。

### 11. 测试与构建结果

实际执行：

```text
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
git diff --check
```

结果：

| 项目 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest --rerun-tasks` | **通过**：66 个测试套件，330 tests，0 failures，0 errors |
| `:app:lintDebug --no-daemon` | **通过**：0 errors / 9 warnings / 3 hints |
| `:app:assembleDebug` | **成功** |
| `git diff --check` | **通过**（仅 LF/CRLF 提示，无空白错误） |

新增测试数：33（第 2 批新增；B4 阶段 18，B5 阶段 4，B6 阶段 11）。

### 12. 手工验收状态

已在模拟器 `emulator-5554`（Medium_Phone_API_37.0）打开 AI 服务页：

- 页面显示“第三方 AI 数据披露”卡片和“我已知晓并继续”按钮。
- 点击确认后卡片消失，确认状态写入 DataStore。
- 独立复审已在同一模拟器执行“清除 AI 配置”确认：页面显示本地兜底状态，Base URL/Key 清空，披露卡片重新出现。
- 已备份并还原 `files/datastore/ai_assistant_settings.preferences_pb`，未保留测试确认状态。
- 真实第三方 AI 服务未连接；实际请求体脱敏仍以单元测试为主。
- B6 修复主要涉及等待失败路径和客户端最终防线，未改变页面布局，本轮未重复模拟器 UI smoke test。

### 13. 已知限制与未解决项

- CR-003 的第三方实际数据流尚未通过真实 AI 服务验证；只验证了本地逻辑和模拟器 UI。
- `ServerSensitiveHostProvider` 首个 emission 前的空 host 窗口已由 `awaitReady()` 关闭；`AiAssistantClient.executeChat()` 在所有真实发送路径的统一入口再次等待，失败时不发请求。首个 emission 被视为完整快照，空配置按“当前无服务器”处理。
- 发送历史消息时 `assistant` / `tool` / `system` 内容纳入脱敏，用户消息保留原文；这是有意设计，避免改写用户主动输入。旧会话中未归档的裸短 host 仍受脱敏规则残余限制。
- `AiPrivacySanitizer` 对旧历史中无归档元数据、仅裸短 host 的覆盖仍有残余限制；第 4 批 provider 扩展后继续回归。
- `CrossSettingsSaveTest` 仍基于 fake store，未直接覆盖真实 `AiAssistantStore`/DataStore 写入（B6-08，低优先级）。
- `ChatViewModel.send()` 的 provider 失败收口未新增直接单测；该方法依赖具体 Android 类，本轮由 provider 失败测试 + 代码路径确认（B6-06 残余）。
- 第 2 批新增的文案已进入 `strings.xml`；此前第 1 批剩余的部分错误提示仍在 ViewModel 中硬编码，继续作为低优先级项记录（B5-05）。
- 复审 B7 低优先级残余：`ChatViewModel.send()` 失败无直接单测（B7-01）、tool arguments 中 URL 紧邻 JSON 引号会破坏嵌套 JSON（B7-02）、参数标记后可能保留第二个路径（B7-03）、既有 suspend provider 的 `runCatching` 取消语义（B7-04）、损坏 `servers_json` 退化为空快照（B7-05）。

### 14. 独立复审结论

- 第 2 批 B6 修复后独立复审：**APPROVED**。
- 第 2 批已提交为 `fix: 完成 AI 第三方披露与隐私边界修复`；审核报告 `docs/FIX_REVIEW.md` 保持未跟踪，不纳入提交。
- 独立验证：66 suites / 330 tests / 0 failures / 0 errors；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- 允许提交第 2 批 commit 并进入第 3 批 CR-017。
- 上表 B5-05、B6-08 和 B7-01～B7-05 均为低优先级残余，不阻断本批；复审建议进入第 3 批前优先处理 B7-02。

---

## 第 3 批：CR-017 服务器地址校验与 WebSocket 异常边界

> 修复日期：2026-11-15
> 分支：`fix/security-data-correctness`
> 基于：第 2 批提交 `9c8ad0e`
> 状态：已实现并完成 B8 复审修复与独立复核；**APPROVED**（`docs/FIX_REVIEW.md` 第 22 节）。第 3 批 commit 已提交。

### 15. 范围与修改

本轮处理：

- CR-017：服务器 host 严格校验、IPv6 规范化与端口校验。
- 旧 DataStore 非法配置的 `InvalidConfig` 终止状态。
- WebSocket URL/请求创建异常边界，非法配置不进入无限重连。
- 非法配置下停止 Queue/History/Server 自动远程刷新，保留本地缓存。
- `ComfyApiFactory` 非法 base URL 的受控异常与调用点审计。
- 第 2 批复审建议的 B7-02：URL 正则 JSON 边界与 tool arguments 嵌套 JSON 测试。

新增生产文件：

- `app/src/main/java/io/github/xiaowu20245/nekopaint/core/network/ServerEndpointValidator.kt`
- `app/src/main/java/io/github/xiaowu20245/nekopaint/data/repository/RemoteRefreshPolicy.kt`

修改生产文件：

- `core/model/ServerConfig.kt`
- `core/network/AiPrivacySanitizer.kt`
- `core/network/ComfyApiFactory.kt`
- `core/network/ComfyWebSocketManager.kt`
- `data/repository/NodeInstallRepository.kt`
- `data/repository/QueueRepository.kt`
- `data/repository/TaskManager.kt`
- `feature/chat/ChatContextBuilder.kt`
- `feature/history/HistoryScreen.kt`
- `feature/queue/QueueScreen.kt`
- `feature/server/ServerError.kt`
- `feature/server/ServerScreen.kt`
- `feature/server/ServerUiState.kt`
- `feature/server/ServerViewModel.kt`
- `res/values/strings.xml`

新增测试：

- `core/network/ServerEndpointValidatorTest.kt`
- `core/network/ComfyApiFactoryTest.kt`
- `data/repository/RemoteRefreshPolicyTest.kt`
- `feature/server/ServerFormTest.kt`（补充非法 host 和 IPv6 规范化）
- `core/network/AiPrivacySanitizerTest.kt`（补充 URL JSON 边界）
- `core/network/AiAssistantClientPrivacyTest.kt`（补充 tool arguments 嵌套 URL）

### 16. 实现说明

#### 16.1 地址校验与模型

- 新增 `ServerEndpointValidator`，只接受 `http`/`https`、合法端口 `1..65535`、合法 IPv4/域名/IPv6。
- 拒绝空白、`/`、`\`、`#`、`?`、`@`、userinfo、不完整或重复 IPv6 方括号、单冒号 host、非法 IPv4 octet。
- 裸 IPv6（如 `::1`、`fe80::1`）规范化为方括号形式；已带方括号的地址保持可用。
- `ServerConfig.baseUrl` 保留为显示/兼容字符串，但明确标注可能对旧配置非法；网络代码统一走 `ServerEndpointValidator.require`。
- `ServerForm.toConfigOrNull()` 和 `ServerViewModel.validateForm()` 共用 validator；保存时写入规范化 host。
- 新增 `ServerError.InvalidConfig` 和对应文案；服务器卡片显示可编辑的配置错误。

#### 16.2 WebSocket 与 API 工厂

- `ComfyConnectionState` 增加 `InvalidConfig`。
- `ComfyWebSocketManager.start()` 在进入重连循环前校验配置；非法配置直接进入 `InvalidConfig`，关闭旧 socket，不启动退避重连。
- URL 构造、`Request.Builder`、`newWebSocket` 全部包在异常边界内；`CancellationException` 原样传播；非法 URL 归类为 `InvalidConfig`，其他创建失败进入 `Reconnecting`。
- 修正 OkHttp WebSocket URL 语义：`HttpUrl` 只接受 `http`/`https`，实际请求使用 `http(s)://host:port/ws?clientId=...`，不再把 `ws://` 字符串交给 `Request.Builder`。
- 配置 Flow 变化后重新校验；修正配置可自动恢复连接。
- `ComfyApiFactory.create(config)` / `createManager(config)` 先验证 endpoint，非法时抛 `InvalidServerConfigException`；`create(baseUrl)` 同样校验 scheme/host/port/根路径。
- `NodeInstallRepository` 的 `createManager` / `create` 裸调用补 `runCatching`，返回受控 `Result.failure`。
- `QueueRepository` 的 history image base URL 使用已验证 endpoint。

#### 16.3 自动刷新与 UI

- 新增 `RemoteRefreshPolicy`；`TaskManager.refresh()` 对非法配置只加载本地任务，不调用 `queue()` / `history()`。
- 非法配置下设置受控错误，保留上次队列/历史缓存，不清空本地记录。
- `QueueScreen` / `HistoryScreen` 在 `InvalidConfig` 时停止自动轮询；`ServerScreen` 在活动卡片为 `InvalidConfig` 时停止 5 秒探测。
- `QueueScreen` 无缓存且配置非法时显示配置错误空状态，不再永久转圈。
- `HistoryScreen` 显示配置无效/上次数据提示；`ChatContextBuilder` 连接状态增加“配置无效”。
- 其他 `taskManager.refresh()` 调用点（WorkflowDetail、Settings、ChatTaskAction）统一经过带守卫入口。

#### 16.4 B7-02

- `AiPrivacySanitizer.URL` 正则增加 `"`、`}` 边界，避免 URL 位于 JSON 字符串值末尾时吞掉结束引号；不移除 IPv6 URL 的 `]`。
- 新增 sanitizer 纯函数测试、IPv6 URL JSON 测试和实际客户端 tool arguments 嵌套 JSON 解析测试。

### 17. 测试与构建结果

实际执行：

```text
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :app:lintDebug --no-daemon
.\gradlew.bat :app:assembleDebug
git diff --check
```

结果：

| 项目 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest --rerun-tasks` | **通过**：72 个测试套件，362 tests，0 failures，0 errors |
| `:app:lintDebug --no-daemon` | **通过**：0 errors / 9 warnings / 3 hints |
| `:app:assembleDebug` | **成功** |
| `git diff --check` | **通过**（仅 LF/CRLF 提示，无空白错误） |

新增测试数：32（第 3 批新增；第 2 批基线 330）。

### 18. 手工验收

模拟器 `emulator-5554`（Medium_Phone_API_37.0）：

1. 服务器表单输入 `foo#bar`：显示“地址格式无效…”，保存被拦截。
2. 输入 `[::1`：显示同一错误，保存被拦截。
3. 输入裸 `::1` 并保存：卡片地址显示 `http://[::1]:8188/`，连接成功（模拟器可访问本机 ComfyUI）。
4. 通过临时 instrumentation 写入旧非法配置 `foo#bar` 后重启：
   - App 不崩溃；
   - 服务器卡片显示“服务器配置无效，请编辑地址或端口”；
   - 队列页显示“配置无效”和配置错误空状态，不再永久转圈；
   - 历史页保持本地展示并提示远程刷新已暂停；
   - 未出现持续 WebSocket 重连。
5. 将同一配置改为 `127.0.0.1:8188` 并保存：卡片无需手动刷新即自动进入探测，随后恢复在线；Queue/History 自动刷新恢复。
6. 临时 instrumentation 测试文件已删除，测试 APK 已卸载；未纳入提交。

### 19. 已知限制与未解决项

- 真实 ComfyUI 只验证了模拟器可访问的 `127.0.0.1` / `::1`；未覆盖真实局域网 HTTPS、自签名证书和公网反向代理。
- B7-03（参数标记后可能保留第二个路径）、B7-04（既有 suspend provider 的 `runCatching` 取消语义）、B7-05（损坏 `servers_json` 退化为空快照）仍为低优先级残余。
- B5-05（ViewModel 文案硬编码）、B6-08（跨设置页只测 fake store）继续作为低优先级记录。
- B8-03（`ComfyWebSocketManager` 状态机仍缺自动化测试；已补 `ComfyWebSocketUrlTest`）、B8-07（`TaskManager` 的 `activeServerId` 与 `config` 分开读取）仍为低优先级残余。
- `ServerConfig.baseUrl` 仍保留给显示/旧测试；网络入口已迁移到 validator，但未来新增调用方仍需遵守该约束。
- 非法配置的持久化注入使用临时 instrumentation；正式回归建议后续增加可复用的 androidTest 或 debug 诊断入口。

### 20. 独立复审结论

- 初次第 3 批复审：**CONDITIONAL**（`docs/FIX_REVIEW.md` 第 20 节）。核心问题是保存非法配置修正后，ServerScreen 的 `refreshCard()` 可能读取尚未合并的旧 profile，导致 `InvalidConfig` 卡住。
- 已修复 B8-01，并处理 B8-02/B8-04/B8-05/B8-06；B8-03/B8-07 继续作为低优先级残余。
- 修复后：72 suites / 362 tests / 0 failures；lint 0 errors / 9 warnings / 3 hints；assemble 成功；`git diff --check` 通过。
- 等待 B8 修复后的独立复审。

### 21. 第 3 批复审修复（B8-01～B8-08）

- **B8-01**：`ServerViewModel.saveAndConnect()` 保存成功后直接更新卡片状态并调用 `refreshProfile(profile)`，不再依赖 `_uiState.cards` 已合并；`combine` 使用 `mergeServerCard()`，endpoint 变化时清除旧 `InvalidConfig`/snapshot/时间；`ServerScreen` 轮询 effect key 增加 profile id 和 `endpointKey`。新增 `ServerCardMergeTest` 覆盖旧错误清理和同 endpoint 错误保留。模拟器复验：非法配置改为合法地址保存后，卡片自动从“配置无效”进入探测，无需手动刷新；设置 adb reverse 后 5 秒内自动在线。
- **B8-02**：`AiPrivacySanitizer.URL` 不再排除 `]`，IPv6 URL 可完整脱敏；新增 IPv6 URL JSON 测试。
- **B8-04**：`HistoryDetailScreen` 在 `InvalidConfig` 时跳过 10 秒刷新循环。
- **B8-05**：服务器折叠卡片对 `ServerError.InvalidConfig` 显示“配置无效”，不再统一显示“离线”。
- **B8-06**：`validateForm()` 独立校验 host 和 port，host 非法时不因端口也非法而被吞掉。
- **B8-08**：`ServerViewModel` 使用 `ServerProbeTracker` 维护每个 server id 的检测 generation；保存新 endpoint 时旧在途检测结果会被判定为过期并丢弃，不再回写旧 profile/snapshot/error。新增 `ServerProbeTrackerTest` 覆盖新 endpoint 使旧 generation 失效、重复同 endpoint 不重复启动、invalidate 丢弃在途结果和 finish 后允许重试。
- **B8-03/B8-07**：新增 `ComfyWebSocketUrlTest` 覆盖 http/https upgrade URL、clientId 编码和非法 endpoint；`ComfyWebSocketManager` 完整状态机测试仍作为低优先级残余，B8-07 仍低优先级。

### 22. 独立复审结论

- 第 3 批 B8-08 修复后独立复审：**APPROVED**（`docs/FIX_REVIEW.md` 第 22 节）。
- 独立验证：72 suites / 362 tests / 0 failures / 0 errors；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- 允许提交第 3 批 commit 并进入第 4 批 CR-006。
- 低优先级残余（不阻塞）：B9-01（A→B→A 快速回切时旧检测结果短暂写入，5 秒轮询自愈）、B9-02（缺 ViewModel 级检测回写测试）、B8-03（WebSocket 完整状态机测试）、B8-06（host/port 双错无直接 ViewModel 测试）、B8-07（`activeServerId`/`config` 分读）。
- 第 3 批 commit 已提交为 `fix: 完善服务器地址校验与连接异常边界`。

### 23. 原复审 Agent 最终确认

- 出具第 20 节初审的原复审 Agent 对 HEAD `4ecbe9d` 复核：**APPROVED**，第 20 节阻塞项 B8-01/B8-08 已闭合。
- 独立验证：72 suites / 362 tests / 0 failures / 0 errors；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- 确认 B8-01 保存后直接探测新 profile、`mergeServerCard` 清理旧 endpoint 状态、ServerScreen 轮询 key 包含 endpointKey。
- 确认 B8-08 的 `ServerProbeTracker` generation 机制会丢弃旧在途检测结果，且不再回写 profile。
- 低优先级残余（不阻塞）：B9-01、B9-02、B8-03、B8-06、B8-07。
- 允许第 3 批进入第 4 批 CR-006。

### 24. 指定复审会话最终确认

- 用户指定的复审会话对 HEAD `4ecbe9d` 独立复核：**APPROVED**，报告追加于 `docs/FIX_REVIEW.md` 第 24 节。
- 独立验证：72 suites / 362 tests / 0 failures / 0 errors；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- 确认 B8-01、B8-08 原始路径闭合；B8-02/B8-04/B8-05/B8-06 保持有效。
- 关键残余仍为 B9-01、B9-02、B8-03、B8-06、B8-07，均低优先级、不阻塞。
- 允许第 3 批正式关闭并进入第 4 批 CR-006。

---

## 第 4 批：CR-006 编辑服务器地址的缓存身份隔离

> 修复日期：2026-11-16
> 分支：`fix/security-data-correctness`
> 基于：第 3 批提交 `4ecbe9d`（复审记录 `57f2e04`）
> 状态：已实现并完成 B11～B18 复审修复与独立复核；**APPROVED**（`docs/FIX_REVIEW.md` 第 33 节）。第 4 批 commit 已提交。

### 25. 范围与修改

- 地址变化（scheme/host/port）生成新 `serverId`；只改名称/头像沿用原 id。
- 旧主机归档为 `ArchivedServer`，旧缓存目录不迁移、不删除。
- DataStore 同一 `edit` 替换/删除 profile、写入归档和 activeId。
- TaskManager 以 `ServerIdentity(serverId, endpointKey)` 作为缓存身份。
- History 主机分组使用归档名称；AI host 脱敏集合包含归档 host。
- 地址变化确认 UI；头像 staged 保存/回滚。

新增生产文件：

- `core/model/ServerEditPlanner.kt`
- `core/model/ArchivedServer.kt`
- `core/datastore/ServerConfigReplacement.kt`
- `data/repository/ServerIsolationCoordinator.kt`
- `data/repository/TaskRequestGuard.kt`
- `data/repository/TaskEventGuard.kt`
- `feature/history/HistoryHostBuilder.kt`

修改生产文件：

- `core/model/ServerProfile.kt`
- `core/datastore/ServerConfigStore.kt`
- `core/network/ComfyWebSocketManager.kt`
- `core/network/ServerEndpointValidator.kt`
- `data/repository/QueueRepository.kt`
- `data/repository/ServerRepository.kt`
- `data/repository/ServerSensitiveHostProvider.kt`
- `data/repository/ServerAvatarStore.kt`
- `data/repository/TaskManager.kt`
- `feature/history/HistoryViewModel.kt`
- `feature/server/ServerUiState.kt`
- `feature/server/ServerViewModel.kt`
- `feature/server/ServerScreen.kt`
- `res/values/strings.xml`
- `DECISIONS.md`（D-063）

新增测试：

- `core/model/ServerEditPlannerTest.kt`
- `core/datastore/ServerConfigReplacementTest.kt`
- `data/repository/ServerIsolationCoordinatorTest.kt`
- `data/repository/TaskRequestGuardTest.kt`
- `data/repository/TaskEventGuardTest.kt`
- `feature/history/HistoryHostBuilderTest.kt`
- `data/repository/ServerSensitiveHostProviderTest.kt`（归档 host 用例）

### 26. 实现说明

- `ServerEditPlanner` 只比较规范化 `endpointKey`；scheme/host/port 变化要求新 UUID。
- `ServerProfile.endpointKey` 对所有 IPv6 去方括号，`localhost`/`::1` 统一到 `127.0.0.1`。
- `ArchivedServer` 只保存 id、名称、地址、归档时间，不含凭据。
- `ServerConfigReplacementPlanner` 纯逻辑负责替换、删除、activeId 和归档 upsert；`ServerConfigStore` 在同一事务写入 servers/archived/active/cache 身份映射。
- `replaceServerEndpoint` 校验持久化 old endpoint、new id 冲突；失败返回 `Result.failure`，不再误报成功。
- `saveServer` 拒绝已存在 id 的 endpoint 变化和已归档 id 复活；移除未使用的 `save(config)` 兼容入口。
- `ServerViewModel` 地址变化弹确认框；确认生成新 UUID，取消不保存；头像先 stage 并备份旧文件，配置保存成功后才 commit；失败 rollback 并保留 draft/旧头像。
- `TaskManager` 使用 `ServerIdentitySnapshot(identity, config)`、`cache_identities_json` 持久化身份、`blockedIdentities` 集合、`TaskRequestGuard`/generation、`TaskEventGuard`、`stateMutex`；旧在途请求/旧 WebSocket 事件不能写新身份。
- `HistoryHostBuilder` 当前 profile 按 endpoint 分组；归档 id 独立只读组、始终 `active=false`，优先归档名称，无归档回退短 id。
- `ServerSensitiveHostProvider` 合并当前和归档 host。

### 27. 测试与构建结果

实际执行：

```text
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
.\gradlew.bat :app:lintDebug --no-daemon
.\gradlew.bat :app:assembleDebug
git diff --check
```

结果：

| 项目 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest --rerun-tasks` | **通过**：78 个测试套件，392 tests，0 failures，0 errors |
| `:app:lintDebug --no-daemon` | **通过**：0 errors / 9 warnings / 3 hints |
| `:app:assembleDebug` | **成功** |
| `git diff --check` | **通过**（仅 LF/CRLF 提示） |

新增测试数：30（第 4 批新增；第 3 批基线 362）。

### 28. 手工验收

模拟器 `emulator-5554`：

1. 添加 `http://127.0.0.1:8188` 并连接成功。
2. 编辑 host 为 `127.0.0.2` 保存：显示地址变化确认框；确认后卡片显示新地址并自动探测。
3. 读取 `server_config.preferences_pb`：新 profile id 与旧 id 不同，旧 id 出现在 `archived_servers_json`，旧 host `127.0.0.1` 保留。
4. 再次编辑为 `127.0.0.3` 并取消：配置仍为 `127.0.0.2`，DataStore 没有 `127.0.0.3`。
5. 只修改名称：不出现地址变化确认框，server id 保持不变。
6. 临时 instrumentation 测试已删除，测试 APK 已卸载。

### 29. 已知限制与未解决项

- 未在模拟器制造旧主机的任务/历史/模型缓存后再替换地址；缓存隔离由 identity/token 逻辑、DataStore 归档检查和纯逻辑测试覆盖。
- `ServerConfigStore` 的真实 DataStore 事务未做 JVM 集成测试；使用 `ServerConfigReplacementPlanner` 测试 + 手工 DataStore 检查，不声称已做事务级 JVM 测试。
- 头像文件操作依赖 Android `Uri`/文件系统，未新增完整 JVM 集成测试；已补 staged 失败恢复逻辑。
- 旧版本升级前已发生同 id endpoint 变化且没有 `cache_identities` 的用户，无法在首次启动识别旧缓存；之后保存/替换开始记录身份。
- 归档元数据目前只增不减；历史清空是否清理归档仍待产品策略。
- B9-01/B9-02、B8-03/B8-06/B8-07 等第 3 批低优先级残余仍保留。
- TaskManager 完整集成测试、头像并发测试、`handleEvent` 旧事件测试仍为已知测试缺口。

### 30. 复审修复 B11～B15

- **B11-01～B11-08**：TaskRequestToken/generation、QueueRepository 显式 config、删除/清空历史使用捕获 serverId、activeId stale 修复、saveServer endpoint 守卫、归档 History active=false、队列状态清理。
- **B12-01～B12-06**：ServerIdentitySnapshot 绑定 identity+config；`stateMutex`/`withCurrentState` 原子检查+写回；staged 头像备份/回滚；stale activeId 测试。
- **B13-01～B13-06**：持久化 `cache_identities_json` + blockedIdentities set；stateMutex 覆盖 refresh/loadMore/delete/clear/reloadTasks；restoreTarget Result/rollback 观察；stale activeId 测试。
- **B14-01～B14-05**：ComfyEventEnvelope + TaskEventGuard；移除无条件 seed；restore 失败受控、compress false 检查；cache identity 只在 save/replace/delete 更新。
- **B15-01～B15-06**：WebSocket connectionToken 丢弃旧 socket 事件；identity 加载在锁内确认；commitStaged/rollback 结果观察；无 cache identity 的旧 profile fail-closed；saveServer 修复 stale activeId。
- 当前验证：78 suites / 392 tests / 0 failures；lint 0 errors / 9 warnings / 3 hints；assemble 成功；`git diff --check` 通过。

### 31. 复审修复 B16～B17

- **B16-01/B17-04**：`ComfyEventEnvelope` 携带 connectionToken + socketToken；连接循环每次重连递增 socketToken，旧 socket 的 onMessage/onOpen/onClosed/onFailure 全部校验；TaskManager 在 stateMutex 内先校验连接 token 和 socket token，再校验 endpoint/blocked。
- **B16-02/B17-02**：identity 切换、cache blocked 计算、状态清理合并到同一个 stateMutex 块；本地缓存加载后校验 identity + generation；updateTask 异步写盘前校验 token。
- **B16-03/B17-03**：commitStaged 检查 File.delete 结果并返回 Result；setAvatar/commitProfile 观察 commit/rollback 失败；restoreTarget 使用 ATOMIC_MOVE + fallback；writeBitmap/stageBitmap 失败删除 partial；avatarMutex 串行化所有头像 stage/save/rollback/commit。
- **B16-04**：新增 `ServerError.CacheIdentityUnknown` 与文案；ViewModel 区分缓存身份错误和头像错误。
- **B17-01**：saveServer 校验 cache identity 的值必须等于当前持久化 endpoint，避免改名/头像时把旧缓存 map 从 A 盖章成 B。
- **B17-05/B17-06**：无 cache map 的旧 profile 全部 fail-closed、测试缺口仍记录为低优先级。
- **B18-01～B18-03**：`commitProfile` 成功 cleanup 和 `delete(profile)` 纳入 avatarMutex；同 identity cache 缺失/清空也加入 blocked；`saveServer` 拒绝孤立 cache map；restoreTarget fallback 使用临时文件+rename；writeBitmap 异常删除 partial；cleanup 失败保留在最终卡片状态；`stop()` 递增 token。
- 当前验证：78 suites / 392 tests / 0 failures；lint 0 errors / 9 warnings / 3 hints；assemble 成功；`git diff --check` 通过。

### 32. 独立复审结论

- 第 4 批 CR-006 B18 修复后独立复审：**APPROVED**（`docs/FIX_REVIEW.md` 第 33 节）。
- 独立验证：78 suites / 392 tests / 0 failures / 0 errors；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- 允许提交第 4 批 commit 并进入第 5 批 CR-007。
- 低优先级残余 B19-01～B19-04：同 identity cache 失效时在途 refresh、draft/delete best-effort、新建 profile 旧缓存目录、集成测试缺口，不阻塞本批。
- 第 4 批 commit 已提交为 `fix: 隔离服务器地址变更后的缓存身份`。

### 33. 第 5 批 CR-007 修复（工作区，待最终复审）

> 状态：源码/测试已通过独立复审批次验收（APPROVED，`docs/FIX_REVIEW.md` 第 38 节），准备提交。

- **B20-01～B20-04（CR-007 主体）**：新增 `AtomicFileWriter`（同目录唯一临时文件 + fsync + 原子替换 + 失败保留原文件/清理临时文件）；`TaskStore`、`HistoryStore`、`HistoryAutoSaver`、`ModelCatalogStore`、`PromptLexiconRepository`、`WorkflowRepository` 写盘改用原子写；为任务/历史/模型目录增加 Mutex。
- **B20-05/B21-03**：新增 `TaskPersistenceQueue` 合并进度快照；single-flight drain、flush、仅新 key 淘汰、writer 失败重试一次并回调日志、shutdown 强制落盘；TaskManager 身份切换前后各 flush 一次，`MainActivity.onStop` 调用 `TaskManager.flushPending()`。
- **B21-01/B22-02**：抽取 `WorkflowTemplateStore`，用单一 Mutex 串行化 list/get/update/repair/rename/copy/delete/saveCompatibility/reanalyze/refreshAll；copy/refresh 全程持锁，避免重入。
- **B21-02/B22-01**：`readProfile` 在 sourceHash 不一致时内存重建检测字段、合并兼容候选值并标记 `repairRequired=true`，不再隐藏写回；普通保存经 `WorkflowRepairFlag` 三态 OR 保留标记，只有 `repairTemplate()` 能清除；普通保存与提交都调用 `WorkflowCandidateSafety`。
- **B22-03/B23-03**：队列 drain 区分普通/强制 flush；普通 drain 保留旧 generation 条目而不是丢弃，身份切换/关闭时强制落盘；writer 连续失败通过 `onWriteError` 记录 `AppLog`。
- **B22-04/B23-02**：新增 `WorkflowRepairFlowTest`（hash mismatch 普通保存保留标记、修复清除、修复失败保留、候选值/预设合并、只读/类型/未知候选拒绝、并发 update/repair/copy/refresh）、`JsonStorePersistenceTest`（History/ModelCatalog round-trip、legacy 迁移、损坏文件、原子失败保留、并发保存）、`WorkflowCandidateSafetyTest`、`TaskPersistenceQueueTest` 扩展、`AtomicFileWriterTest` 失败注入/并发写。
- **B23-01**：新增 `WorkflowCandidateSafety`：校验空/重复/不一致 id、只读选中、只读翻转、类型/分组变化、未知候选、INT/FLOAT/BOOLEAN 标量值；普通保存和提交前使用同一校验，失败不写 profile。
- **B23-04**：`AtomicFileWriter` 增加可注入 move seam，覆盖“已有目标文件 + move 失败内容不变 + 临时文件清理”；并发写同目标测试；Windows 下 move 增加有限重试。
- **B23-05**：修复合并仅迁移类型/分组/classType 兼容且值可解析的候选；primary prompt 必须可编辑且为文本类型；预设条目按 id/语义解析后清理失效/只读/类型不兼容项，activePreset 同步失效。
- **测试与验证**：84 suites / 432 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- **独立复审**：**APPROVED**（`docs/FIX_REVIEW.md` 第 38 节），独立统计与本轮一致；B24-01～B24-04 为低优先级残余，不阻塞本批。
- **手工验收**：模拟器 `emulator-5554`（API 28）注入 sourceHash 不一致模板后，列表显示“需修复”且快捷运行按钮 `enabled=false`；详情页显示修复横幅、修复按钮可用、提交生成按钮 `enabled=false`；点击“修复并保存”后横幅消失、profile `repairRequired=false` 且 sourceHash 与 api.json 一致、提交生成恢复可用；全程无 FATAL。
- **测试缺口/限制**：TaskManager 完整集成测试仍无法在无 Android Context/真实依赖下构造；关闭落盘由 `TaskPersistenceQueue.shutdown()`、scope cancel、身份切换双 flush 测试及 `MainActivity.onStop` 手工冒烟覆盖。`HistoryStore/ModelCatalogStore` 通过 internal 临时目录构造测试，生产仍使用 Context 构造。
- 第 5 批 commit 已提交为 `fix: 原子化工作流与任务持久化`（`5ce9d23`）。

### 34. 第 5 批补充 CR-019（已复审 APPROVED）

> 计划中第 5 批包含 CR-007 与 CR-019；前一批只提交了 CR-007，本轮补 CR-019。独立复审见 `docs/FIX_REVIEW.md` 第 40 节。

- **SafeFileNames**：新增 `data/repository/SafeFileNames.kt`。合法 `[A-Za-z0-9_-]{1,64}` 原样保留；已是 `sha256_<64 hex>`（71 字符）直接返回，避免二次哈希；空值、`.`、`..`、路径分隔符、冒号、空白、控制字符、中文、超长值统一哈希为固定 71 字符。
- **TaskStore**：`get/write/update/list/listAll` 全部通过安全文件名定位；`serverId` 目录名和 `promptId` 文件名都应用安全函数；原始 `promptId`/`serverId` 仍保存在 JSON 内容中，读取不从文件名反推；保存前用 `canonicalFile` 校验目标仍在 `rootDir` 内。合法 UUID/default 文件名不迁移；异常旧文件名不做路径回退读取。
- **TaskStore 测试 seam**：新增 internal 构造（临时 `filesDir`），生产 Context 构造不变。
- **新增测试**：`SafeFileNamesTest`（安全值原样、恶意值哈希、固定长度、稳定性/不同值不碰撞、已哈希不二次哈希）；`TaskStorePathTest`（合法 round-trip、`../../evil` 不越界且内容保留原值、恶意 serverId 哈希、list 从内容取 promptId、legacy default 迁移、损坏/非 JSON 文件忽略）。
- **验证**：86 suites / 442 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- **C20 修复**：`listAll` 不再用 hashed 目录名冒充空 serverId；`list(serverId)` 过滤内容 serverId 不一致的记录；`get` 拒绝内容 serverId 与请求不一致；legacy 迁移增加锁，避免并发 rename/copy 竞争；新增旧 UUID 文件可读、旧异常文件名不回退、serverId 不一致过滤、hashed 目录空 serverId 不复用目录名 4 个测试。
- **限制**：未在模拟器伪造 ComfyUI 返回恶意 promptId；路径穿越防护由纯函数 + 临时目录 TaskStore 测试覆盖，未做真实服务器联调。
- **独立复审**：**APPROVED**（`docs/FIX_REVIEW.md` 第 40 节）；86 suites / 446 tests、lint、assemble、diff check 与本地一致；C21-01～C21-04 为非阻塞低优先级残余。
- CR-019 补充 commit 已提交为 `fix: 安全处理任务记录文件名`（`78c474a`）。

### 35. 第 6 批 CR-014（已复审 APPROVED）

> 范围：高级模式把对象/数组等非标量输入当字符串编辑/写回，破坏工作流 JSON 类型。

- **ParameterDetector**：只有非 `JsonNull` 的 `JsonPrimitive` 才生成候选；对象/数组输入直接跳过，已知输入（如 `ckpt_name`）和上游提示词/尺寸解析同样要求标量。
- **WorkflowParser.scalarText()**：对象/数组返回 `null`，不再 `toString()`；JsonNull 也返回 null。
- **WorkflowJsonApplier**：
  - 写入前要求目标输入存在且为原始标量；对象/数组/JsonNull/连线/缺失输入均抛 `WorkflowApplyException`，不新增字段、不写字符串。
  - `UNKNOWN` 按原始标量类型写回：字符串→字符串、布尔→布尔、数字→Long/Double；解析失败抛异常。
  - 新增 `safeCandidates(rawJson, candidates)` 安全视图，只保留原始输入为标量的候选，不修改候选值。
- **WorkflowTemplateStore.readProfile**：读取旧 profile 后用 `safeCandidates` 过滤结构化候选，只影响内存不自动写回；api.json 缺失/不可解析时 fail-closed，候选置空并 `repairRequired=true`。
- **WorkflowRepository.submitSelected**：读取 api.json 后对模板候选和 selected 再做一次 `safeCandidates` 过滤，过滤后为空返回“没有可提交的标量参数”；api.json 缺失仍返回明确错误。
- **测试**：ParameterDetectorTest 对象/数组/已知输入/上游标量；WorkflowJsonApplierTest 对象/数组拒绝、UNKNOWN 字符串/数字/布尔类型保留、非法值/缺失输入拒绝、safeCandidates 过滤；WorkflowParserTest scalarText；WorkflowRepairFlowTest 旧 profile 结构化候选内存过滤 + 不改写 profile、api 缺失 fail-closed。
- **验证**：86 suites / 460 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- **手工验收**：注入含 `widget` 对象/`items` 数组旧候选的 profile；列表显示“1 项可选”，详情高级仅显示标量“文本”；点击保存后 profile 中结构化候选被清理，`api.json` 的 `widget`/`items` 原结构和类型保持不变；无 FATAL。
- **独立复审**：**APPROVED**（`docs/FIX_REVIEW.md` 第 41 节）；86 suites / 460 tests、lint、assemble、diff check 与本地一致；B25-01～B25-04 为非阻塞低优先级残余。
- 第 6 批 commit 已提交为 `fix: 保护工作流非标量输入不被字符串化`（`dd6e900`）。

### 36. 服务页离线徽章高频闪烁修复（已复审 APPROVED）

- **原因**：服务页 5 秒静默轮询在卡片尚无 snapshot 时仍把 `error` 清空并置 `isTesting=true`，导致“离线→检测中→离线”周期闪烁；`LaunchedEffect` 又把 `state.activeCard?.error` 作为 key，错误变化会反复重启轮询循环；另有 id 变化时的重复立即探测。
- **修复**：
  - 新增 `ServerRefreshPolicy.showTesting()`：手动刷新始终显示检测中；静默刷新只有“从未得到任何结果”（snapshot=null 且 error=null）才显示检测中，已在线/已离线卡片保持原徽章。
  - `ServerViewModel.refreshActive()` 跳过 `InvalidConfig`，避免无效地址自动重试；`refreshProfile()` 改用统一策略，不再清空已失败卡片的 error。
  - `ServerScreen` 的轮询 `LaunchedEffect` 移除 `error` key 和重复的立即刷新 effect，仅在 active id/endpoint/lifecycle 变化时重启；循环内 `refreshActive()` 读取最新状态。
  - 新增 `ServerRefreshPolicyTest` 覆盖手动/首次静默/离线/在线/缺失卡片。
- **验证**：87 suites / 465 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- **手工验收**：API 37 模拟器 `emulator-5554`，active 服务器 `127.0.0.2:8188` 离线；连续 10 次 uiautomator 采样（约 22 秒）状态徽章始终为“离线”，未再出现“检测中/离线”交替。
- **独立复审**：**APPROVED**（`docs/FIX_REVIEW.md` 第 42 节）；87 suites / 465 tests、lint、assemble、diff check 与本地一致；S1-01～S1-03 为非阻塞低优先级残余。
- 本次修复 commit 已提交为 `fix: 稳定服务页离线徽章刷新`（`af9696e`）。

### 37. 检测中最短展示时长补充（已复审 APPROVED）

- **问题**：即使后台轮询不再反复清空离线状态，首次/手动探测遇到连接立即失败时，“检测中”只出现几十毫秒就被“离线”替换，看起来仍像闪烁。
- **修复**：
  - `ServerRefreshPolicy` 增加 `MIN_TESTING_VISIBLE_MS = 1200` 和 `remainingTestingMs()`。
  - `ServerViewModel.refreshProfile()` 在网络结果返回后，如果本次显示“检测中”，先补足最短 1.2 秒再应用成功/失败状态；连接本身较慢时不额外延迟。
  - 保留上一轮策略：后台轮询在已有在线/离线结果时不进入“检测中”，只在首次无结果或手动刷新时展示。
  - `ServerRefreshPolicyTest` 增加最短展示剩余时间用例。
- **验证**：87 suites / 466 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- **手工验收**：API 37 模拟器离线服务器手动刷新；截图像素采样显示徽章背景从 danger 色切到 warning（检测中）并持续 4 帧，约 1.2 秒后才回到 danger（离线），不再瞬时闪一下。
- **独立复审**：**APPROVED**（`docs/FIX_REVIEW.md` 第 43 节）；87 suites / 466 tests、lint、assemble、diff check 与本地一致；S2-01～S2-03 为非阻塞低优先级残余。
- 本次补充 commit 已提交为 `fix: 延长检测中状态的最短展示时间`（`23143a0`）。

### 38. 第二轮第 1 批：共享契约 + CR-005/008/009（已复审 APPROVED）

- **共享契约**：
  - 新增 `TaskStatus`（submitting/unknown/queued/running/terminal）和 `TaskStatusSource`（DISK/LOCAL/WS/QUEUE/HISTORY）。
  - `TaskRecord` 增加 `requestedAt/requestedPromptId/statusUpdatedAt/statusSource`，旧 JSON 默认兼容。
  - 新增 `PromptSubmissionAttempt`/`AttemptState` 与可序列化 `ServerBinding`。
  - 新增 `TaskRecordRepository` + `TaskRecordObserver` + `TaskRecordWriteState`：写 TaskStore 后通知内存读模型，写失败可见；recorder 不依赖 TaskManager。
  - `TaskStore.delete(serverId, promptId)` 已实现。
- **CR-005**：`ClientIdStore` 增加 `Mutex`，抽出 `ClientIdAllocator`；32 并发只写一次；空白 ID 重生成；写异常向上传递。`WorkflowRepository` 捕获 clientId 读取异常并返回明确 Error。
- **CR-008**：`TaskStatusSyncPolicy` 改为“来源+时间”合并，不使用单一 rank；本地 `submitting/unknown` 不被旧磁盘 queued 覆盖；`reloadTasks` 合并 stored + live-only，不丢未写盘任务；队列/历史同步产生变更后通过 `TaskRecordRepository`/`TaskPersistenceQueue` 写盘；`success` 清空旧错误；TaskManager 注册 observer 同步内存读模型。
- **CR-009**：新增 `TaskProgressPolicy`，`ProgressState` 的 `finished` 节点计入完成集合；`total=0` 时 `overallPercent=null`，不再生成假 `0/1`；running value/max 只作为当前节点分数。
- **测试**：`ClientIdAllocatorTest`、`TaskStatusSyncPolicyTest`、`TaskProgressPolicyTest`、`TaskRecordRepositoryTest`、`TaskListSyncPolicyTest`、`TaskStorePathTest` 扩展 requested/delete/并发。
- **R2B1 修复**：
  - `TaskStatusSyncPolicy` 仅在 `useIncoming=true` 时采用 incoming 的 finishedAt/error/currentNode/progress；本地较新的终态元数据不被旧磁盘/历史回退。
  - Observer 回调投递到 TaskManager scope，并在 `stateMutex` 内更新 `_tasks`；与 WS/queue/history 串行化。
  - `TaskStore.delete` 使用同一写 Mutex；新增并发 update/delete 测试。
  - 新增 `TaskManager.remove/replace`、`TaskRecordWriteState.Deleted` 和 observer 删除通知。
  - `TaskRecordRepository.upsert` 归一化空白 serverId 后再通知 observer。
  - `ProgressState` 全部 finished 时不再取最后一个节点伪装 running；新增 `TaskProgressPolicy.runningNode()` 测试。
  - 新增 `TaskListSyncPolicy` 纯函数，覆盖 reload live-only、queue 变更只写一次、history 终态、observer 旧记录不覆盖 running。
  - `TaskManager.updateTask` 缺失任务时从 repository 读取或构造 placeholder，保证 WS 先到不丢事件；`TaskListSyncPolicy.applyEvent` 覆盖 upsert。
  - `TaskRecordRepository` 增加写 Mutex + sequence；`TaskRecordEventDispatcher` 单消费者按顺序投递 upsert/delete；TaskManager 丢弃过期 sequence。
  - `TaskManager.remove/replace`、`TaskRecordWriteState.Deleted`、observer 删除通知；record serverId 始终归一化为存储 scope。
  - `TaskProgressPolicy.finishing()` 清除 currentNode；QueueScreen 无 currentNode 时不再显示“未知节点”。
  - `TaskRecordRepository` 增加删除 tombstone：delete 后同一 serverId+promptId 的旧 upsert 被拒绝；不同 promptId 可正常写入。
  - `TaskManager.remove/replace` 改为走 repository delete/upsert，由 sequence/dispatcher 统一排序；replace 不再直接改内存。
  - 新增 `TaskRecordSequenceGate`；TaskManager 只接受递增 sequence，防止迟到事件复活/覆盖。
  - QueueScreen 无 currentNode 时隐藏整个节点步骤行/细进度条，`finishing()` 同时清空 value/max。
  - `updateTask` 对未知任务：仅当队列项属于本机 clientId 时构造内存 placeholder，且不持久化空 template；完整记录仍由预写/队列/历史补齐。
  - `TaskRecordEventDispatcher` 增加 `flush()`/`close()`/超时；`TaskRecordEventCoordinator` 抽出可测的 observer/update/remove/replace 协调器，remove/replace 返回前等待内存消费；replace 同 promptId 直接拒绝。
  - `TaskManager` WS 事件路径使用 `updateTaskLocked()`（调用方已持 stateMutex），避免 coordinator 再次加锁导致非重入死锁；新增持锁调用测试。
  - 未知 clientId/队列未到的事件先进入最多 256 条的 pending 缓冲，待 clientId/队列证据到达后重放；不再直接放行外部事件，也不丢本机事件。
  - 服务器切换清理 pending；dispatcher 关闭/超时返回 false，remove/replace 返回明确失败。
  - clientId 缺失/本机 ID 未加载时不再丢本机 WS 事件；只有双方 ID 都已知且不同才过滤。
  - tombstone 语义定为 v1 同 promptId 不允许重建，使用新 attempt ID；tombstone 仅进程内，重启自然清理。
  - **R2B1f 修复**：已确认外部 clientId 事件直接丢弃，不占 pending；remove/replace 清理对应 pending；flushPendingUpdates 检查 tombstone + 30s TTL；unknown pending 上限 256；clientId/队列证据到达后重放。
  - **R2B1g 修复**：pending map 所有增删/遍历统一 `pendingLock` 保护；新增 5s 周期 prune 与手动 `prunePending`；外部事件在 stored 存在时也检查；新增 external+stored、prune、pending 并发/持锁测试。
  - **R2B1f 修复**：`TaskRecordEventDispatcher.closed` 改 `@Volatile`；`TaskManager.shutdown()` 关闭 coordinator/dispatcher；flush 超时返回“磁盘已写、内存同步未完成（可安全重试）”；新增外部事件丢弃、删除后 pending 拒绝、TTL、flush 超时/close 测试。
- **验证**：95 suites / 519 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 9 warnings / 3 hints；`assembleDebug` 成功；`git diff --check` 通过。
- **独立复审**：**APPROVED**（`docs/FIX_REVIEW.md` 第 54 节）；R2B1h-01～R2B1h-04 为非阻塞低优先级残余。
- 第二轮第 1 批 commit 已提交为 `fix: 统一任务状态、持久化与进度`（`4036d60`）。

### 39. 第二轮第 2A 批：工作流批量提交后台化（已复审 APPROVED）

- 新增 `data/operation/OperationCenter`：独立 `operationScope`（不修改共享 appScope）、不可变 template 快照、RUNNING/PARTIAL/SUCCESS/FAILED/CANCELLED、success/failed/skipped/total、duplicate template guard、TTL 清理。
- 批量失败矩阵：400/本地校验/Error 记为 failed 并跳过剩余；成功继续；0 成功为 FAILED；支持 cancel（当前无 UI 入口）。
- `WorkflowDetailViewModel.submit()` 改为 OperationCenter.start + 状态观察；页面销毁不取消提交；历史 terminal 操作不会被新页面误当成本次结果；成功后仍同步执行值。
- `WorkflowsViewModel.quickRun()` 改为 OperationCenter + operationId 观察；离开页面继续；结果通知/摘要/刷新列表。
- `AppContainer` 创建独立 operationScope + operationCenter；Chat confirm、Node install 未迁移（按计划留到第 4/5 批）。
- 测试：`OperationCenterTest` 覆盖部分成功/skipped、0 成功、全成功、重复模板拒绝、lastSuccess。
- **R2A 修复**：非取消异常写入 FAILED；启动后立即应用当前 operation 状态；Validation 的 nodeErrors/fieldErrors 映射并触发兼容性复检；OperationCenter 增加 persistExecutionValues（成功执行值在 operationScope 落盘）；Workflows init 订阅 running/recent 操作并恢复摘要；quickRun 部分成功显示 failed/skipped 与错误详情；start 使用 startLock+LAZY job 原子占位；cancel 仅覆盖 RUNNING。
- **R2A2 修复**：Workflows handled 只在 terminal 标记、RUNNING 不吞终态；Detail 先清 UI 再应用 current state；异常 processed/failed 计数修正、success>0 为 PARTIAL 且持久化执行值；lastValidation 进入终态、PARTIAL 映射 Validation；Workflows 恢复 5 分钟窗口内终态；终态写使用 updateTerminal 防止 cancel 被覆盖；PARTIAL 更新 lastTemplate。
- **R2A3 修复**：QuickSubmitted 增加 failed/skipped 摘要并在 Snackbar 渲染（含 4 参字符串）；PARTIAL 摘要显示成功/失败/跳过；executionValuesPersisted 暴露给 ViewModel，成功/部分成功按落盘结果设置 dirty/saved；异常路径 runSucceeded 防双重计数；新增 cancel 不被终态覆盖、计数守恒测试。R2A3-04“提交后台继续”返回提示明确延后到 2B（不改变数据正确性）。
- 验证：96 suites / 525 tests / 0 failures / 0 errors / 0 skipped；lint 0/9/3；assembleDebug 成功；git diff --check 通过。
- **独立复审**：**APPROVED**（`docs/FIX_REVIEW.md` 第 58 节）；R2A4-01～03（ViewModel 测试/返回提示/cancel 竞争测试）为 2B 必须跟踪的非阻塞残余。
- 第二轮第 2A 批 commit 已提交为 `fix: 后台批量提交工作流`（`e9efdb2`）。

- **说明**：本批不包含 2B 的预写/unknown 提交路径、Chat、Node install；这些按计划后续批次接入。

### 40. 第二轮第 2B 批：提交幂等与本地任务记录（已复审 APPROVED）

- `WorkflowSubmitResult` 新增 `Unknown(promptId, message)`；`Success` 新增 `localRecordWarning`，用于“已提交但本地记录保存/迁移失败”的非阻断提示。
- `WorkflowRepository` 提交前生成稳定 `promptId` 并预写 `submitting` TaskRecord（`requestedAt/requestedPromptId/values/statusSource`）；预写失败直接返回 Error，不发送 POST。
- POST 复用该 promptId；超时/IO/空响应/429/5xx 写 `unknown` 并返回 Unknown。429/5xx 使用同一 ID 退避重试一次；400/401/403/413/422 写 `failed` 并返回 Validation。
- 服务器返回不同 promptId 时写新记录并携带 `requestedPromptId`，删除旧请求 ID；删除失败保留映射并通过 `localRecordWarning` 提示“本地存在一条旧请求记录”，不静默双记录。最终记录写入失败也返回 Success + warning。
- `WorkflowRepository` 增加 `internal WorkflowRepositoryDependencies`/`forTest` JVM seam：可注入临时 `WorkflowTemplateStore`、config/server/clientId provider、TaskRecordRepository、API factory 和 retry delay，不依赖 Android Context。
- 启动/刷新恢复：`PendingSubmissionRecoveryPolicy` 将超过 30s 的 `submitting` 降级为 `unknown`；TaskManager reload 归一化后落盘，队列/历史命中仍由 TaskListSyncPolicy 升级为 queued/running/终态。
- 队列页新增“本地待确认”区：展示 submitting/unknown 记录；unknown 提供“检查队列/历史”和“复用原 ID 重试”，重试走 `WorkflowRepository.retryUnknown`；历史详情再次生成仍使用新 attempt。
- OperationCenter 新增 UNKNOWN 状态和 unknown 计数：success>0 时为 PARTIAL，success=0 且有 unknown 时为 UNKNOWN；Detail/Workflows 展示未知/部分成功摘要与本地记录 warning。
- R2A4 闭环：新增 `WorkflowsViewModelTest`（RUNNING→terminal 摘要）与 `WorkflowDetailOperationPolicyTest`（快速终态、PARTIAL/UNKNOWN/warning）；Detail 顶部返回在后台提交运行中时 Toast 提示“提交将在后台继续”，不阻断返回；`OperationCenterTest` 新增 NonCancellable 终态写入与 cancel 竞争测试。
- **R2B 修复（第 59 节 CONDITIONAL 后）**：
  - R2B-01：Queue 重试 notice 拼接 `localRecordWarning`；Chat 批量重试把 warning 计入独立警告列表；Detail SUCCESS 合并“执行值落盘失败 + localRecordWarning”，不再只显示一项。
  - R2B-02：`retryUnknown` 重新读取磁盘当前记录，只在 UNKNOWN/SUBMITTING 时继续，并检查 `requestedPromptId` 相同但 promptId 不同的新记录；发现迁移接管则拒绝重试。Queue 本地待确认卡片持久显示“已迁移”及新任务 ID，并禁用重试。
  - R2B-03：补删除旧记录失败 warning、迁移后拒绝重试、空响应 unknown、OperationCenter UNKNOWN/partial 计数、reload 归一化落盘、Queue 重试控制器 Success/Unknown/warning/异常/finally、warning+落盘失败组合测试。
  - R2B-04：Queue 页消费 `TaskRecordWriteState.Failed`，常驻显示“任务记录写入失败”横幅。
  - R2B-05：Queue 重试改由独立 `QueuePendingRetryController` 在 operationScope 执行，try/finally 清理重试中状态；repository 重读存储状态降低旧对象竞态。
  - R2B-06：新增 `/history/{promptId}` 定向查询；Queue“检查”按钮调用 `TaskManager.recheckPending`，先查队列再定向查历史，未找到时明确提示可同 ID 重试。
- **R2B2 修复（第 60 节 CONDITIONAL 后）**：
  - R2B2-01：`QueueViewModel.recheckUnknown` 先解包内层 `Result<Boolean>`，新增 `describeRecheckResult`；网络/服务器失败显示“检查失败，请勿据此重试”，只有明确 false 才提示可复用原 ID。
  - R2B2-04：`/history/{promptId}` 改为 `Response<Map>`，新增 `HistoryLookupPolicy`：404/空 map/promptId 不匹配 -> NotFound；非 2xx -> Failed；只接受精确 promptId 命中，不再回退到其他条目。
  - R2B2-03：`TaskRecordRepository.prepareRetry` 在同一把写锁内复核 UNKNOWN/SUBMITTING、supersededBy 和迁移映射后再预写 SUBMITTING；重试不再有“重读后又被升级”的覆盖窗口。
  - R2B2-05：迁移删除失败时把旧记录持久化为 `unknown` + `supersededBy=新 ID`；`QueueMigrationPolicy` 优先使用该字段，Queue 即使新记录尚未进入内存读模型也显示“已迁移”并隐藏重试。
  - R2B2-02：新增 `PendingRecheckPolicy`（队列命中/历史命中/双未命中/查询失败编排）及 5 项测试、`HistoryLookupPolicy` 3 项、`QueueMigrationPolicy` 4 项、`QueueDiagnosticsPolicy` 2 项、`ChatTaskActionBatchRetryTest`、`TaskRecordRepository.prepareRetry` 4 项测试。
- **R2B3 修复（第 61 节 CONDITIONAL 后）**：
  - R2B3-01：服务器返回不同 promptId 且新记录 `upsert(completed)` 失败时，也把旧 prewrite 记录写为 `unknown + supersededBy=新 ID`；Queue 隐藏重试，`prepareRetry` 拒绝。新增“新记录写入失败 + 旧记录仍在”测试。
  - R2B3-02：新增 `recheckNotice` 作为 QueueViewModel 的实际解包接线，并测试内层 `Result.failure`、外层异常、true/false；避免只测描述函数。
  - R2B3-03：迁移扫描增加方向性：只有原始请求记录（promptId == requestedPromptId）才向后寻找新记录；新记录不会反向显示“已迁移到旧 ID”，`prepareRetry(new)` 也不再被旧记录误伤。补反向测试。
  - R2B3-04：记录为已知窄竞态——`prepareRetry` 读取 TaskStore，而 TaskPersistenceQueue 可能尚未落盘内存升级；正常 UI 已因 QUEUED 卡片消失而不可点击，同 ID 也由服务器去重。本批不引入版本号/CAS。
- **R2B4 修复（第 62 节 CONDITIONAL 后）**：
  - R2B4-01：`WorkflowRepository.retryUnknown` 只在 `current.promptId == requestedPromptId`（原始请求记录）时扫描迁移新记录；重试 ID 改为 `current.promptId`，迁移后的新记录用自己的 new ID 重试，不再被旧记录反向阻断，也不会用旧 ID 提交。
  - R2B4-02：补 `retryUnknown(new)` 测试（old+new 共存、new UNKNOWN，POST 使用 new-id 且不被旧记录阻断）与 `retryUnknown(old)` 对照测试（仍被 new 阻断、0 请求）。
- **R2B5 修复（第 63 节 CONDITIONAL 后）**：
  - R2B5-01：`submitSelected` 分离“POST/预写使用的 promptId”和“TaskRecord 保留的 requestedPromptId”；迁移新记录重试使用 new ID 提交，但记录继续保留 old→new 映射。成功 `completed` 不再覆盖 `requestedPromptId`。
  - R2B5-02：`retryUnknownUsesNewIdForMigratedNewRecord` 增加断言 new.requestedPromptId 仍为 old、重试后再次 `retryUnknown(old)` 返回 Error 且请求数仍为 1。
- 测试：新增 `PromptSubmissionPolicyTest`、`PendingSubmissionRecoveryPolicyTest`、`WorkflowSubmitPersistenceTest`（16 项：预写失败不发 POST、成功预写→queued、IO unknown、429 同 ID 重试、5xx 重试后 unknown、400 不重试、响应 ID 迁移、迁移删除失败 warning、迁移后拒绝重试、superseded 标记拒绝重试、新记录落盘失败标记旧记录、迁移新记录用 new ID 重试并保留映射、旧记录被新记录阻断、空响应 unknown、最终落盘失败 warning、unknown 同 ID 重试）、`WorkflowsViewModelTest`、`WorkflowDetailOperationPolicyTest`、`QueuePendingRetryControllerTest`、`PendingRecheckPolicyTest`、`HistoryLookupPolicyTest`、`QueueMigrationPolicyTest`、`QueueDiagnosticsPolicyTest`、`ChatTaskActionBatchRetryTest`；`TaskListSyncPolicyTest` 增加 unknown 在队列/历史命中后升级、reload 超时归一化、superseded 合并（共 9 项）；`TaskRecordRepositoryTest` 增加归一化落盘与原子 `prepareRetry`（含反向迁移）。
- 验证：107 suites / 591 tests / 0 failures / 0 errors / 0 skipped；lint 0/9/3；assembleDebug 成功；`git diff --check` 通过。
- **独立复审**：**APPROVED**（`docs/FIX_REVIEW.md` 第 64 节）；R2B4/R2B5 的迁移 ID 与映射问题已在六轮复审核对闭环。
- 第二轮第 2B 批 commit 已提交为 `fix: 提交幂等与本地任务记录`（`ee106d2`）。
- 未包含：Chat confirm、Node install（按计划留第 4/5 批）；unknown-only 记录在队列页展示，历史页仅在存在匹配 history entry 时显示任务状态。

### 41. 第二轮第 3 批：图片内存与分辨率（已复审 APPROVED）

- **CR-012 蒙版**
  - 新增 `MaskExportPolicy`：最长边 4096、16MP 像素预算、RGB_565、32MB 上限；`targetSize/scale/estimatedBytes` 为纯 JVM 策略。
  - 新增 `MaskStroke` 与 `MaskBitmapRenderer`：Dialog 只收集归一化笔迹；渲染在后台执行，使用 RGB_565、黑底白笔迹，渲染后校验无 alpha 且抽样灰阶；异常路径 recycle。
  - `MaskEditorDialog` 回调改为 `onUpload(strokes, sourceSize)`，点击时不再渲染为主线程工作；`ImageDimensionReader` 预读 PNG/JPEG/WebP 头尺寸。
  - `WorkflowDetailViewModel` 新增 `MaskExportState`（rendering/uploading/error/success），`exportMask` 在 `operationScope` 后台渲染+上传；ViewModel/导出函数是 Bitmap 唯一所有者，`finally` recycle，成功后关闭蒙版对话框并写回候选值。
  - 新增 `ImageHeaderParser` + `ImageDimensionReader`：PNG IHDR/tRNS、JPEG SOF、WebP VP8/VP8L/VP8X；HTTP 使用注入的 image client 且只读 256KB 头，失败返回 Result。
- **CR-013 Chat 图片**
  - `ChatImageEncoder` 不再 `readBytes`：先按 20MB 上限 bounded 复制到 cache，再 `inJustDecodeBounds` + `inSampleSize`（约 2×目标）解码，精确缩放到 1024。
  - 新增 `ChatImageDecodePolicy`（20MB/1024/采样/目标/格式）；照片输出 JPEG，源图 alpha（PNG/tRNS、WebP alpha）输出 PNG；头无法解析时保守按 PNG。
  - 临时文件、decoded/scaled Bitmap 在所有路径 finally 删除/recycle；OOM/解码/编码失败返回 Result.failure。
- **R3 修复（第 65 节 CONDITIONAL 后）**：
  - R3-01：蒙版 Paint 关闭抗锯齿，确保 RGB_565 只有黑白；保留严格灰阶校验。`MaskBitmapRendererInstrumentedTest` 在 Android Canvas 上验证 4000×4000 蒙版、四角黑色、笔迹中心白色、无 alpha。
  - R3-02：`MaskEditorDialog` 分离 `sourceSize`（ImageDimensionReader 头解析，导出用）与 `decodedSize`（Coil 显示用）；Coil 只填充 decoded，源尺寸仅在缺失时兜底，不再覆盖头解析结果。
  - R3-03：`ImageHeaderParser.parseWebP` 对 VP8X/VP8/VP8L 分别做严格长度检查，截断头返回 null；`ChatImageEncoder` 用局部 `runCatching` 解析头，失败按 PNG 保守处理。补截断 VP8X/VP8/VP8L 测试。
  - R3-05：蒙版渲染与 Chat bounded 复制放入 `NonCancellable` 作用域，确保 Bitmap/临时文件在取消窗口中有明确所有者并最终清理。
  - R3-04：新增 7 项 Android instrumentation，并在 `NekoPaint_API_28` 模拟器全部通过：4000×4000 蒙版+擦除（断言角点 (0,0) 黑、笔迹中心白）、4000×3000 JPEG→≤1024、50MP (8160×6120) 照片→≤1024、透明 PNG 保持 PNG、8000×1000 长截图→≤1024、>20MB 文件失败。
- 测试：`MaskExportPolicyTest` 5、`ImageHeaderParserTest` 6（含截断 WebP）、`ChatImageDecodePolicyTest` 4；Android instrumentation 7。
- 验证：110 suites / 606 tests / 0 failures / 0 errors / 0 skipped；lint 0/9/3；assembleDebug 成功；`git diff --check` 通过；`connectedDebugAndroidTest` 7/7 通过。
- **独立复审**：**APPROVED**（`docs/FIX_REVIEW.md` 第 66 节）。
- 第二轮第 3 批 commit 已提交为 `fix: 控制图片内存与分辨率`（`8842943`）。
- 非阻塞残余：R3B-01 `sourceSize` 优先级缺直接 UI/策略断言；R3B-02 NonCancellable 取消窗口缺专门测试；R3B-03 文档“四角黑色”已修正为角点断言。R3B-01/02 留第 4 批跟踪。

### 42. 第二轮第 4 批：会话与参数保存（工作区，待复审）

- **CR-015 会话 revision/tombstone**
  - `StoredSession` 增加 `revision/deleted/deleteGeneration`；`ChatSessionSummary` 暴露 revision；`listSessions` 过滤 deleted，`loadSession` 忽略 tombstone。
  - `ChatHistoryCodec` 新增 `saveSessionWithResult/deleteSessionWithResult/maxRevision`：新 revision 写入、相同 revision 内容一致幂等/不一致拒绝、旧 revision 拒绝、tombstone generation 拒绝在途保存。
  - `ChatHistoryStore.saveSession/deleteSession` 返回 `ChatSessionSaveResult`，并保留旧 `saveSession` 包装给测试/旧调用；`renameSession/setPinned` 递增 revision。
  - `ChatViewModel` 初始化 `nextRevision=maxRevision()+1`；`persistMessages` 捕获 sessionId+revision+snapshot，异步保存后只更新 sessions，不把旧 ID 写回 current；Rejected/Failed 设置 `sessionError` 并 Toast。
  - `newSession/switchSession` 等待当前保存，Failed/Rejected 保持当前；`deleteSession` tombstone + 递增 generation；D2-06：执行中的动作禁止 new/switch/delete。
  - 新增 `ChatActionLocator`（sessionId+proposalId，旧数据 sessionId 为空按当前会话兼容），`confirmAction` 使用定位器。
- **CR-020 参数/元数据 patch**
  - `WorkflowTemplateStore` 新增 `patchPrimaryPrompt/patchPreset/deletePreset/patchActivePreset/patchPromptTagGroups/patchExecutionValues/patchApplyPreset/patchSavePreset`，全部在现有 Mutex/原子写内，只读写对应字段。
  - `patchExecutionValues` 只取快照的 value/selected，不写 label/type/readOnly；提交成功后的执行值落盘改用 `patchExecutionValues`，不再整体 `updateTemplate`。
  - ViewModel：selectPreset 原子写值+active（dirty=false/saved=true）；saveNewPreset 原子写 preset+values；rename/delete preset 与标签组只 patch 元数据，不清 dirty；`setPrimaryPrompt` 只 patch primary。
  - 新增 `WorkflowDirtyPolicy` 纯策略并用于 selectPreset/saveNewPreset。
- **R4 修复（第 67 节 CONDITIONAL 后）**：
  - R4-01：tombstone 后任何保存都拒绝（不再允许高 revision 复活）；空消息保存先做 revision/删除校验并按 tombstone 写入，旧 revision 不能删除较新会话。
  - R4-02：`persistMessages` 捕获 `modeSnapshot`，异步 job 不再读取可变 mode。
  - R4-03：sessionId/revision/pending job 注册使用 `sessionLock` 串行化；save 结果按 revision 取最新，旧 job 不会覆盖新结果。
  - R4-04：动作执行前捕获 sessionId；结果返回时若当前会话已变化，加载原会话、按 proposalId 更新并保存（revision），提示“动作结果已写入原会话”。
  - R4-05：`WorkflowTemplateStore.patch` 复用 `WorkflowCandidateSafety.validate`、primary/preset/active sanitize 和 `repairRequired` 保护，patch 不再绕过候选安全边界。
  - R4-06：新增 `ChatHistoryStoreInstrumentedTest` 在 `NekoPaint_API_28` 真实 DataStore 上验证 revision 递增、tombstone、拒绝复活；`connectedDebugAndroidTest` 8/8 通过。
- **R4B 修复（第 68 节 CONDITIONAL 后）**：
  - R4B-01：`persistMessages` 在 `sessionLock` 内同时读取 snapshot/promptId/mode/title、分配 sessionId/revision，并用 `CoroutineStart.LAZY` 创建 job、锁内登记后再 start；`awaitCurrentSave` 锁内取 currentSessionId+job，避免快照/登记竞态。
  - R4B-03：跨会话动作结果写回原会话并保存 revision；目标会话/动作缺失时通过 `sessionError` 提示，不再静默返回。
  - R4B-04：`WorkflowTemplateStore.patch` 已复用候选 validate/sanitize/repair 保护；补 metadata patch 测试覆盖 executionValues 只写 value/selected、缺失模板失败。
  - R4B-02：DataStore instrumentation 覆盖 revision/tombstone；ChatViewModel 并发保存的 UI 级测试仍未直接构造（依赖较重），记录为残余验收项。
- **R4C 修复（第 69 节 CONDITIONAL 后）**：
  - R4C-01：`newSession`/`switchSession`/`deleteSession` 的 messages/currentSessionId/taskPromptId/mode/nextMessageId 更新全部移入 `sessionLock`；与 `persistMessages` 的快照读取互斥，避免旧 snapshot 配到新 session。
  - R4C-02：ChatViewModel 并发保存/D2-06 UI 测试仍缺（依赖较重），继续记录为残余验收项；DataStore instrumentation 保持 8/8。
- **R4D 修复（第 70 节 CONDITIONAL 后）**：
  - R4D-01：新增 `updateAndPersist`，消息 mutation 与 persist 快照在同一 `sessionLock` 内；发送用户消息、工具消息、视觉消息、finalizeAssistant、appendAssistant、failUserMessage 均已迁移；`switchMode` 的 mode 更新与 persist 也在锁内。
  - R4D-02：ChatViewModel 并发 UI 测试仍缺，保留为残余验收项；DataStore instrumentation 与全量测试保持通过。
- **R4E 自动化验证**：新增 `ChatActionGate`，执行中统一禁止 new/switch/delete 与 switchMode；`ChatActionGateTest` 3 项覆盖 EXECUTING 阻止、PENDING/SUCCESS/FAILED/CANCELLED 放行、sending 阻止。
- 测试：`ChatHistoryCodecRevisionTest` 4、`ChatActionLocatorTest` 2、`ChatActionGateTest` 3、`WorkflowMetadataPatchTest` 6、`WorkflowDirtyPolicyTest` 5；Android instrumentation 8。
- 验证：115 suites / 626 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3（新增一条依赖版本提示，非代码问题）；assembleDebug 成功；`git diff --check` 通过；`connectedDebugAndroidTest` 8/8 通过。
- **R4F 修复（第 72 节 CONDITIONAL 后）**：
  - R4F-01：`newSession`/`switchSession` 在 `awaitCurrentSave` 返回后、`deleteSession` 在加载会话后重新检查 `hasExecutingAction`；`send`/`confirmAction` 在执行中直接拒绝，避免 await 窗口启动新操作。
  - R4F-03：Chat 页新增 `BackHandler`，动作执行中返回被拦截并 Toast“动作执行中，请等待完成”。
  - R4F-04：`updateActionProposal` 已接 `updateAndPersist`；调用点仍会多一次 persist，属低优先级残余。
- 说明：D2-06 的代码门禁已自动化验证；Chat 顶栏在动作执行中已禁用模式切换、新建会话、会话列表、重命名/删除当前会话，返回也已拦截。
- **R4G 修复（第 73 节 CONDITIONAL 后）**：
  - R4G-01：`send`/`runTool`/`runImageTool` 先获取 SEND lease；`confirmAction` 使用 ACTION lease；`newSession`/`switchSession`/`switchMode` 使用 SWITCH lease；`deleteSession` 使用 DELETE lease。lease 在 await/load/store 删除之前获取，apply 前用 token 校验，`finally` 释放；`send`/`confirmAction` 在切换/删除等待期间直接拒绝。
  - R4G-02：新增 `ChatViewModelSessionTest` 5 项，直接构造生产 ChatViewModel，用可挂起 fake store/client/action 覆盖：switch await 中 send 拒绝、保存中 newSession 不恢复旧快照、发送期间切换/删除拒绝且回复留在原会话、动作执行中 new/switch/delete/switchMode/send 全部拒绝、删除 load 中 send/confirm 拒绝。新增 `BackDispatchTest` 2 项覆盖顶栏返回消费/放行。
  - R4G-03：`ChatScreen` 新增 `onBackHandlerChange`，与 `BackHandler` 共用“动作执行中禁止离开”门禁；`NekoPaintNavHost` 传入，`NekoPaintApp` 顶栏返回统一走 `dispatchScreenBack`。
  - R4G-04：移除 `confirmAction`/`cancelAction`/`selectPromptCandidate`/`toggleInstallRestart`/`savePreset` 的重复 persist；动作终态与结果消息一次 `updateAndPersist`，并在释放 registry 前 `awaitCurrentSave()`；同会话保存串行化。
  - R4G-05：`ChatSessionSheet` 新增 `actionExecuting`，新建/切换/菜单在动作执行中禁用。
  - R4G-06：tombstone 单独占用 30 个名额，不再挤掉正常会话；跨会话动作 target 缺失写 `sessionError`；rename/pin 失败提示；补 repairRequired 保留与非法预设条目清理测试。
  - 测试 seam：新增 `ChatSessionStore`/`ChatActionExecutor`/`ChatContextSource`/`ChatToolRuntime` 接口与 `ChatCompletionClient`/`ChatPrivacyGuard`/`ChatImageEncoderApi`/`ChatImageStorage` 契约；`ChatHistoryStore.loadSession` 支持 `activeProposalIds`，后台动作仍活跃时保留 EXECUTING，进程死亡才降级 FAILED。
  - 验证：119 suites / 644 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 8/8。
- **R4H 修复（第 74 节 CONDITIONAL 后）**：
  - R4H-01：`ChatViewModel.init` 加载会话时传入 `actionRegistry.activeProposalIds`；`ChatHistoryCodec.loadSession` 对活跃 proposal 强制恢复 EXECUTING（含存储态仍为 PENDING 的情况）；`send`/`confirmAction`/`switchMode`/`acquireSessionMutation` 统一复核当前/目标会话的 registry 活跃动作。
  - R4H-02：新增 `ChatSendExecutionRegistry`（AppContainer 单例）。send/runTool/runImageTool 登记 session；新 ViewModel 在 send 仍活跃时拒绝切换/删除该会话，并提示“后台操作尚未完成”；发送终态保存完成后才撤销登记。
  - R4H-03：`ChatSessionStore.saveSessionWithNextRevision` 在 DataStore 写锁内分配 revision，后台动作结果写回不再使用旧 ViewModel 计数器；`ChatActionExecutionRegistry` 增加 `activeState` 与 `completions`，新 ViewModel 收到完成事件后刷新原会话。
  - R4H-04：动作执行异常/取消路径会写 FAILED 并释放 registry/lease；不再留下永久 EXECUTING。
  - R4H-05：新增跨实例发送/动作测试、动作异常/取消、保存/删除/加载失败测试；新增 `ChatBackHandlerInstrumentedTest` 验证动作执行中系统返回被消费、顶栏 handler 返回 true；`ChatHistoryStoreInstrumentedTest` 增加 store 分配 revision 用例。
  - 验证：120 suites / 656 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 10/10。
- **R4I 修复（第 75 节 CONDITIONAL 后）**：
  - R4I-01：`ChatActionExecutionRegistry` 增加会话级 `version`；`switchSession` 在 load 前后校验 version，动作在 load 期间完成时丢弃旧快照并重试读取终态；新增 `completionDuringSwitchLoadIsNotLost`。
  - R4I-02：`ChatScreen` 增加 `viewModelOverride` 测试 seam；新增 `ChatScreenBackInstrumentedTest`，用真实 ChatScreen + EXECUTING 状态验证系统返回被消费、顶栏 handler 返回 true；`actionOutcomeWaitsForPendingSaveAndUsesNextRevision` 覆盖 pending save 与 revision 分配。
  - R4I-03：`persistActionOutcomeToSession` 保存前 join 目标会话已有 `pendingSaveJobs`，再由 `saveSessionWithNextRevision` 在 store 写锁内分配 revision；方法改为 internal 供并发测试直接验证。
  - R4I-04：`ChatSendExecutionRegistry` 增加 `completions`；ChatViewModel 在新实例停留在原会话时刷新发送终态。
  - R4I-05：`savePreset` 增加后台操作门禁；rename/pin/clearTaskContext 已在前一轮处理；repairRequired value patch 语义继续作为低优先级残余记录。
  - 验证：120 suites / 659 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- **R4I-06/R4I-07 修复（第 76 节 CONDITIONAL 后）**：
  - R4I-06：`ChatSessionOperationGate` 改为 AppContainer 单例并通过 `ChatViewModel` 构造注入；所有 ChatViewModel 共享 SEND/ACTION/SWITCH/DELETE 许可，跨实例“检查通过后另一实例 begin”被 gate 原子拦截；lease 获取失败时提示后台忙。
  - R4I-07：ChatViewModel 订阅 `ChatActionExecutionRegistry.activeState`，其他实例 begin 时把本地同 session/proposal 的 PENDING 提升为 EXECUTING（只更新 UI，不写盘，避免跨实例 revision 冲突）；completion 后刷新终态。
  - R4I-04：`ChatSendExecutionRegistry.completions` 增加跨实例发送完成刷新测试。
  - 新增测试：`sharedGateBlocksNewInstanceWhileActionRunsThenLoadsResult`、`beginAfterApplyIsObservedByOtherInstance`、`sendCompletionRefreshesOtherInstanceOnSameSession`；`ChatViewModelSessionTest` 16 项。
  - 验证：120 suites / 661 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- **R4I-08/R4I-09 修复（第 77 节 CONDITIONAL 后）**：
  - R4I-08：`ChatActionExecutionRegistry.completions` 不再要求本地已是 EXECUTING；当前会话匹配且不在发送时一律按 store 终态刷新，覆盖快速 begin/finish 被 StateFlow 合并的路径；新增 `fastActionCompletionRefreshesPendingState`。
  - R4I-09：`ChatSessionOperationGate` 改为按 session 的 SEND/ACTION lease + 全局 mutation lease；SWITCH/DELETE 只检查本次涉及的会话（当前+目标），不同会话的发送/切换不再互相阻塞。新增 `differentSessionsCanSendConcurrently`、`unrelatedSessionSwitchIsAllowedWhileActionRuns`、gate 的 per-session 测试。
  - 验证：120 suites / 666 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- **R4I-10/R4I-11 修复（第 78 节 CONDITIONAL 后）**：
  - R4I-10：`refreshCurrentSessionFromStore` 先 `awaitCurrentSave()`，再按 proposalId 合并远程终态/结果，保留本地候选选择、安装重启开关、预设字段；成功结果消息去重追加。动作/发送期间 `cancelAction`/`selectPromptCandidate`/`toggleInstallRestart`/`savePreset` 统一拒绝本地 proposal 编辑。新增 `refreshMergesActionStateWithoutLosingLocalProposalEdit`。
  - R4I-11：`ChatSessionOperationGate` 的 mutation 也按涉及会话分区；不同会话的 switch/delete 可并行，mutation 只阻塞涉及会话的 SEND/ACTION。新增 `mutationOnOneSessionDoesNotBlockSendOnAnother`、`disjointMutationsCanRunConcurrently`，`ChatSessionOperationGateTest` 8 项。
  - 验证：120 suites / 669 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- **R4I-12/R4I-13 修复（第 79 节 CONDITIONAL 后）**：
  - R4I-12：refresh 始终使用合并而不是整份替换；远程 PENDING 不覆盖本地 CANCELLED/SUCCESS/FAILED，本地 EXECUTING 仅在 registry 仍活跃时保留，否则降级 FAILED；发送完成也走同一合并，并追加 store 中缺失的消息（按 ID + role/text 去重）。新增 `refreshKeepsLocalCancelledAndActiveExecutingStates`。
  - R4I-13：新增生产 ChatViewModel 集成测试：`mutationOnOneSessionDoesNotBlockSendOnAnotherAtViewModelLevel`、`sameSessionMutationBlocksSendAtViewModelLevel`、`disjointMutationsCanRunConcurrentlyAtViewModelLevel`；`ChatViewModelSessionTest` 24 项。
  - 验证：120 suites / 673 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- 第二轮第 4 批 commit 已提交为 `fix: 会话与参数保存`（`8b66bda`）。

### 43. 第二轮第 5 批：节点安装绑定与队列归属（工作区，待复审）

- **CR-018 服务器绑定**
  - 新增 `ServerBindingPolicy`：`fromProfile/matches/toConfig`；只接受 serverId + endpointKey + scheme/host/port 完全一致的当前 active profile。
  - `NodeInstallPlan` 增加 `binding: ServerBinding? = null`；`preparePlan` 捕获当前 profile 并写入计划；旧计划 binding=null 拒绝安装，要求重新检查。
  - `ChatActionProposal` 增加 `binding`；`proposeNodeInstall` 从 plan 写入，`installNodes` 从 proposal 回填 plan；旧建议 binding=null 时安装失败并提示重新生成。
  - `recheck(templateId, binding)` 校验绑定；工作流详情复检传递当前 plan.binding；绑定变化返回 ServerChanged，不写模板。
- **CR-018 队列归属**
  - 新增 `NodeInstallQueuePolicy`：baseline READY/BUSY/UNAVAILABLE；active/idle；`activeCoversQueued`（total>=queued）。
  - `install` 在第一个 install 之前检查 baseline：busy -> QueueBusy（零 install、不重启）；status null -> StatusUnavailable（零 install）。
  - startQueue 后要求新鲜 active 且 total>=queued；active total<queued -> Unconfirmed；超时 -> Unconfirmed；均不自动重启。
  - active 回到 idle 才视为 Done；只有 Done 才 reboot；reboot 前/后、复检前后均校验绑定。
  - reboot 后 `reanalyzeAndSave` 失败时新增 `recheckFailed`，不再误报“已补齐”。
- **UI**
  - 工作流安装确认框与 Chat 安装卡片显示目标服务器 `host:port`。
- **测试**
  - `NodeInstallQueuePolicyTest` 5、`ServerBindingPolicyTest` 4、`NodeInstallPlanSerializationTest` 3、`NodeInstallRepositoryTest` 9（MockWebServer：busy 零 install、status 不可用、旧计划拒绝、active->idle、never active 不重启、server change、重启+复检、preparePlan 绑定/切换）、`NodeInstallOutcomeTest` 10；`ChatTaskActionHandlerTest` 增加 binding 传递。
  - 验证：124 suites / 698 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- **R5 修复（第 81 节 CONDITIONAL 后）**：
  - R5-01：`NodeInstallUiState` 新增 `binding`，install 成功清空 plan 时保留 binding；`recheckBinding()` 统一取 binding ?: plan.binding；`NodeInstallRepository.recheck` 对 null 直接失败，不再回退当前服务器。
  - R5-02：`WorkflowRepository.analyzeCompatibility/saveCompatibility/reanalyzeAndSave` 增加可选 `ServerBinding` 并在分析前、写盘前复核 active binding；`NodeInstallRepository` 的 deps lambda 传递 binding，install reboot 后 recheck 也带 binding。
  - R5-03：`reboot()` 异常或空响应标记 `restartRejected=true`，不进入 reconnect；`waitForServer` 只在 `systemStats()` 成功解析（非 2xx 会抛异常）时视为恢复。
  - R5-04：`NodeInstallRepositoryTest` 扩展到 16 项：recheck 无 binding/切服务器/分析期间切换、active total<queued、reboot 非 2xx、recheckFailed、system_stats 非 2xx 不恢复；新增 `NodeInstallRecheckBindingTest` 3 项。
  - 验证：125 suites / 708 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- **R5-06/R5-07 修复（第 82 节 CONDITIONAL 后）**：
  - R5-06：`WorkflowTemplateStore.saveCompatibility/reanalyzeAndSave` 增加 `bindingGuard`，在 store mutex 内、写盘前执行；`WorkflowRepository` 传入 active binding guard，使“检查 + 写盘”在同一锁内完成。新增 `WorkflowBindingGuardTest` 2 项（guard=false 不写 compatibility/缺失节点）。
  - R5-07：`NodeInstallRepository.recheck` 在 `reanalyzeAndSave` 返回 null 时先复核 binding，变化返回 ServerChanged，否则才报“服务器尚未恢复”；新增 `recheckNullResultAfterServerChangeReportsServerChanged`。
  - R5-08（低残余）：`waitForServer` 仍可能在 reboot 后 2s 看到旧进程；记录为低优先级，不影响写模板。
  - 验证：126 suites / 711 tests / 0 failures / 0 errors / 0 skipped；lint 0/10/3；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- 第二轮第 5 批 commit 已提交为 `fix: 绑定安装服务器与队列归属`（`3193e34`）。
- 第二轮第 5 批已复审 **APPROVED**（`docs/FIX_REVIEW.md` 第 83 节），文档提交 `0676d69`。

### 44. 第三轮 3A-1：正确性基线（CR-021/023/026/027）

- **CR-021 LoadImageMask 槽位**
  - `WorkflowCompatibilityAnalyzer` 新增 `isMaskLoader`，`isImageLoader` 排除 `LoadImageMask`；`LoadImageMask` 只生成 `maskSlots`，普通 `LoadImage` 仅按下游 slot 0/1 生成 image/mask。
  - `CURRENT_VERSION` 升至 4，旧模板会自动重算；未接入输出链的 mask loader 仍产生 warning。
  - 新增测试：LoadImageMask 仅 mask slot、slot0 被当 image 消费也不生成 image slot、孤立 mask loader 警告；`WorkflowRepairFlowTest` 增加旧 `analyzerVersion=3` 模板读取后重算为 4 的 store 级回归。
- **CR-023 队列首屏状态**
  - 新增 `QueueLoadState`（7 态）与 `QueueUiStatePolicy`；`QueueViewModel` 注入 `activeServerId`，按配置/加载/刷新/错误/旧数据/空/正常计算状态。
  - `QueueScreen` 不再对 `queue == null` 无限 spinner：未配置显示设置空状态，首次失败显示错误空状态 + 重试，刷新中保留旧内容。
- **CR-026 节点错误精确到输入**
  - `NodeErrorDetailDto` 增加 `input_name`；`NodeErrorInfo` 增加 `inputName/details`；解析保留字段。
  - `WorkflowDetailOperationPolicy` 优先按 `nodeId + inputName` 匹配，节点级错误（inputName=null）才回退到该节点所有输入；提交错误卡片显示输入名。
- **CR-027 历史选择与可见筛选联动**
  - 新增 `HistorySelectionPolicy`；`HistoryScreen` 的 `selectableEntries` 改为基于 `filteredEntries`，切换筛选/主机/数据刷新时 prune 不可见选择；全选只覆盖当前可见可操作条目。
- **测试**
  - 新增 `WorkflowCompatibilityAnalyzerTest` +4、`QueueUiStatePolicyTest` 8、`HistorySelectionPolicyTest` 2、`WorkflowDetailOperationPolicyTest` +2、`WorkflowRepairFlowTest` +1；`WorkflowSubmitPersistenceTest` 增加 `input_name/details` 断言。
  - 验证：**128 suites / 728 tests / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 10 warnings / 3 hints**；assembleDebug 成功；`git diff --check` 通过；API28 connected 仍为 11/11 基线，本轮未新增 instrumentation。
- **复审**：R8-01 已在 `docs/FIX_REVIEW.md` 第 91 节复审闭环；R8-02 已在第 92 节确认，3A-1 获 **APPROVED**。
- **提交**：3A-1 已提交为 `fix: 修正节点槽位、队列状态与历史选择`（`e65ee21`）。

### 45. 第三轮 3A-2：历史筛选与 Chat 权限（CR-022/033/034/046/047）

- **CR-022 temp 筛选与顶部开关**
  - 新增 `HistoryVisibilityPolicy`：TEMP 筛选强制显示 temp 图；ALL/OUTPUTS 仍受全局 `showTempPreviews` 控制；TEMP 下开关禁用且提示“强制显示”，不写回全局设置。
  - `HistoryScreen` 的历史子栏增加“临时图”FilterChip；所有卡片、灯箱、批量导出统一使用 `showTempImages`。
- **CR-033 Chat 工具执行层白名单**
  - `ChatToolPolicy.allowsExecution`：只读工具两模式放行；写工具仅 Agent；未知工具两模式拒绝；`allows` 复用同一白名单。
  - `ChatToolRuntime.execute` 增加 `mode: ChatMode`；`ChatToolExecutionGuard` 在执行入口拒绝未授权工具，不创建 action proposal；ChatViewModel 三个调用点与测试 fake 已更新。
- **CR-034 Chat 任务来源**
  - `ChatActionProposal`/`ChatBatchTarget` 增加 `source: LOCAL/OTHER/UNKNOWN`；`ChatTaskActionDataSource` 增加 `localClientId` 与 `queueClientId`。
  - 来源优先 `TaskRecord.clientId`，缺失时回退 `QueueItem.clientId`；单任务/批量目标均按该规则，空/未知 clientId 归 UNKNOWN；批量任一 OTHER 即 OTHER。
  - 确认卡显示来源；其他客户端任务红色提示，并在中断/删除/批量删除前弹二次确认。
- **CR-046 标签组名称边界**
  - `PromptTagExpander` 改为词边界正则：`#foo` 不匹配 `#foobar`、`##foo`、`#foo_bar` 或 `#foo画质`；长组名优先，未知占位保持原样。
  - `referencedGroups` 复用同一占位正则，避免引用计数误判。
- **CR-047 新会话清理任务上下文**
  - `newSession` 和删除当前会话时清空 `taskPromptId/contextPromptId/taskContext`；`AGENT_SYSTEM_PROMPT` 删除“后续版本接入”过期文案。
- **测试**
  - 新增 `HistoryVisibilityPolicyTest` 6、`ChatToolPolicyTest` 4、`ChatToolExecutionGuardTest` 3、`ChatTaskActionHandlerTest` +8（含 queue-only INTERRUPT/DELETE 与批量来源）、`ChatViewModelSessionTest` +2、`PromptTagExpanderTest` +2、`ChatTaskActionSerializationTest` 1（旧 JSON 缺 source 默认 UNKNOWN）；`ChatScreenBackInstrumentedTest` fake 适配 mode 参数。
  - 验证：**132 suites / 754 tests / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 10 warnings / 3 hints**；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- **复审**：第 93 节 R9-01～R9-04、第 94 节 R9-05/R9-06 均已修复；第 95 节复审 **APPROVED**。
- **提交**：3A-2 已提交为 `fix: 收紧 Chat 工具与历史筛选`（`9059b28`）。

### 46. 第三轮 3B-1：用户文案错误模型（CR-035 上半）

- **核心模型**
  - 新增 `core/common/NekoMessage.kt`：`NekoMessageKey`、`NekoMessage(key, args)`、`NekoException(nekoMessage, cause)`、`Throwable.toNekoMessage(fallback)`。
  - 新增 `feature/common/NekoMessageMapper.kt`：key → `@StringRes` 的唯一映射；`NekoMessage.asString()` 统一在 Compose 层取资源。
  - data/network 层不再拼接中文 UI 文案；技术异常与 `NekoException` 的边界保持分离。
- **队列/历史/缓存**
  - `TaskManager` 的 `queueError/historyError/actionError` 改为 `StateFlow<NekoMessage?>`；队列刷新、历史刷新/加载更多/删除/清空、中断/删除动作、服务器缓存身份/端点变化/配置无效均映射到稳定 key。
  - `recheckPending/deleteHistory/clearHistory` 的早期失败改为 `NekoException`（`SERVER_NOT_CONFIGURED`/`SERVER_ENDPOINT_CHANGED`/`SERVER_SWITCHED`）；`QueuePendingRetryController` 的 recheck/retry 失败改为 `QueueNotice.Message`，不再直接显示 data 层 `throwable.message`。
  - `QueueUiState.error/actionError`、`HistoryUiState.error`、`HistoryEditNotice.Error`、`SettingsUiState.error` 改为 `NekoMessage`；Queue/History/Storage 页面统一通过 `asString()` 显示。
  - 服务器域原本已使用 `ServerError` 枚举 + `@StringRes` 映射，未重复改造；仍保留现有类型化错误。
- **测试**
  - 新增 `NekoMessageMapperTest` 5 项：核心 key 映射、全 key 非零资源、缓存清理专用资源、`NekoException` 保留具体文案、普通异常 fallback。
  - 新增 `TaskErrorPolicyTest` 4 项与 `SettingsErrorPolicyTest` 1 项：生产映射策略覆盖队列/历史刷新、加载更多、服务器 block/config、删除/清空、队列动作、缓存清理；不依赖 UI 文案。
  - `QueueUiStatePolicyTest` 适配 `NekoMessage` 错误类型，8 项保持通过。
  - 验证：**135 suites / 764 tests / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 10 warnings / 3 hints**；assembleDebug 成功；`git diff --check` 通过；API28 `connectedDebugAndroidTest` 11/11。
- **复审**：第 96 节 R10-01、第 97 节 R10-02 均已修复；第 98 节复审 **APPROVED**。

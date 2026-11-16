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

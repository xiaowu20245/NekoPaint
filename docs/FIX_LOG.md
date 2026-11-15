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

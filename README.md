# NekoPaint

通过局域网用 Android 手机远程控制 Windows 上的 ComfyUI。

> 当前阶段：**V0.5c 工作流兼容性分析完成**。工作流详情会显示运行类型（文生图/图生图/蒙版重绘/自动部位重绘）、外部图片/蒙版槽和兼容性问题。
> V0.5b 多服务器、V0.5a 模型下拉、V0.4 图片链路与历史缓存均已验证；真实飞行模式断网测试待补。
> 产品范围与阶段计划见 `PRODUCT.md`、`REQUIREMENTS.md`、`ROADMAP.md`。

---

## 环境要求

| 项目 | 当前值 |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | 37 |
| Build Tools | 36.0.0 |
| Android Gradle Plugin | 9.3.1 |
| Gradle | 9.7.0 |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| minSdk / targetSdk | 26 / 37 |

> Gradle 9.7.0 是 Kotlin 2.4.21 官方支持的上限。不要单独升级到 9.8.x，除非同时升级 Kotlin/AGP 并重新验证。

Android Studio 不是必需，但推荐使用。若用命令行，确保 `JAVA_HOME` 指向 JDK 17，且 SDK 已安装上述组件。

---

## 构建与运行

首次构建会下载 Gradle 与依赖：

```bat
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:lintDebug
```

安装到已连接的模拟器/真机：

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n io.github.xiaowu20245.nekopaint/.MainActivity
```

> `local.properties` 指向本机 SDK，已被 `.gitignore` 忽略。换机器时重新生成或修改该文件。

---

## 连接 ComfyUI

ComfyUI 默认只监听 `127.0.0.1`，手机无法访问。启动时加 `--listen`：

```bat
python main.py --listen 0.0.0.0 --port 8188
```

然后：

1. 确认手机与电脑在同一局域网（或手机热点）。
2. 允许 Windows 防火墙的专用网络入站 TCP 8188。
3. 手机浏览器访问 `http://电脑IP:8188/system_stats`，返回 JSON 即网络通。
4. Android 模拟器可访问 `http://10.0.2.2:8188`；若使用端口转发，也可以先执行 `adb reverse tcp:8188 tcp:8188`，再在 App 中填 `127.0.0.1`。真机必须填电脑的局域网 IP。

ComfyUI 默认无认证、HTTP 明文。本项目仅面向可信局域网，设置页会提示风险。

---

## 当前目录

```text
app/src/main/java/io/github/xiaowu20245/nekopaint/
├─ NekoPaintApplication.kt
├─ MainActivity.kt
├─ di/                    # 手动依赖注入
├─ core/
│  ├─ datastore/          # 服务器配置、固定 client_id
│  ├─ model/              # 领域模型
│  ├─ network/            # ComfyUI API、DTO、WebSocket、事件解析、图片 URL
│  └─ ui/                 # 主题、导航、应用骨架
├─ data/
│  ├─ mapper/             # DTO → 领域模型
│  └─ repository/         # 模板、任务、队列、历史
└─ feature/               # 四个主板块 + 生成/历史子页
```

底部导航固定四项：**服务器、工作流、队列、设置**。生成从工作流详情进入，历史从队列页进入；顶部 App Bar 显示当前板块标题。

队列页从 `/queue` 拉取运行中/等待中任务，并通过 WebSocket 显示节点进度；断线时降级为轮询，运行任务卡会提示“连接已断开，正在重连，进度暂不更新”。历史元数据会缓存到本地，重启或短时离线后仍能查看上次结果；联网成功后以服务器历史覆盖。模型字段通过 `/models/{folder}` 补全下拉选项。任务、历史、模型缓存都放在 `filesDir/servers/<serverId>/` 下，切换服务器不会串数据。导入工作流时会做只读兼容性分析，不修改节点逻辑，只报告缺少的必需输入、孤立 LoadImage、UI 占位值、D站外部提示词接入和运行类型。高级模式支持搜索参数/节点/模型，并按功能类型筛选。历史默认只显示最终 `output` 图片，`temp` 预览图可在设置页全局开启，也可在历史顶部快捷切换；详情页和大图预览会同步过滤。历史详情支持图片网格、全屏缩放预览、保存到相册、系统分享、参数快照、错误信息和“再次生成”。图片加载使用 Coil 3，并复用应用的 OkHttpClient。保存位置为 `Pictures/NekoPaint`，同名文件会自动加序号；分享通过 FileProvider，仅暴露临时文件。设置页可查看并清理图片/分享缓存，内存缓存 25%、磁盘上限 500 MB。

`workflows/` 用于存放参考工作流 JSON，不参与构建。App 只导入 **API 格式**；UI 格式请先在 ComfyUI 中导出 API。

---

## 文档

- `AGENTS.md`：Agent 协作与工程约束
- `PRODUCT.md`：产品定位与边界
- `REQUIREMENTS.md`：功能需求与验收标准
- `ARCHITECTURE.md`：技术架构与分层
- `COMFYUI_API.md`：接口说明与真实验证记录
- `ROADMAP.md`：阶段计划与进度
- `DECISIONS.md`：决策记录（ADR）

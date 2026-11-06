# COMFYUI_API.md

> ComfyUI HTTP / WebSocket 接口说明与验证记录
>
> - 版本：v0.3
> - 更新：2026-11-04
> - 状态：**已在本机 ComfyUI 0.27.0 上验证完整成功任务（提交 → WebSocket 事件 → 历史 → 图片）**
> - 适用：NekoPaint Android 客户端
> - 关联文档：`ARCHITECTURE.md`、`REQUIREMENTS.md`、`ROADMAP.md`

---

## 0. 重要说明

本文档用于指导实现，但 **不是最终事实**。ComfyUI 没有稳定的公开 API 版本契约，不同版本、不同自定义节点都可能改变行为。

标记约定：

| 标记 | 含义 |
| --- | --- |
| ✅ 本机验证 | 已在 2026-11-03 的本机 ComfyUI 0.27.0 上实际请求确认 |
| ✅ 源码确认 | 已在 ComfyUI 公开源码中确认路由/字段 |
| ⚠️ 待本机验证 | 尚未用真实服务抓包确认 |
| ❓ 版本相关 | 不同版本可能不存在或行为不同，必须做能力探测 |

本文档基于 2026-11 抓取的 `ComfyUI` master 分支源码（`server.py`、`execution.py`、`protocol.py`）与官方文档整理，
并在本机 ComfyUI **0.27.0** 上验证了 `/system_stats`、`/queue`、`/history`、`/api/jobs`、`/models`、`/object_info` 等 HTTP 端点。
V0.3 期间又验证了忙碌队列、`/interrupt`（带 `prompt_id`）、`POST /queue` 删除等待任务、`progress_state` / `execution_interrupted` 事件，以及断线后的 WebSocket 自动重连。
V0.4 验收补充验证了 4K `/view`、404 图片、`/upload/image`、真实 `execution_error` 历史记录和 100 条历史。
重连补发 `executing` 仍未验证。

### 0.1 本机环境（2026-11-03）

| 项目 | 值 |
| --- | --- |
| ComfyUI | `0.27.0` |
| 前端 | `1.45.20` |
| Python | `3.13.11` |
| PyTorch | `2.9.1+cu128` |
| GPU | `NVIDIA GeForce RTX 5060 Laptop GPU` |
| 显存 | `8,546,484,224` 字节（约 7.96 GiB） |
| 系统内存 | `16,496,545,792` 字节（约 15.36 GiB） |
| 启动参数 | `--listen --auto-launch --preview-method auto --cuda-malloc` |
| 监听 | `0.0.0.0:8188`（IPv4 + IPv6） |
| 工作流目录 | `ComfyUI/user/default/workflows/`（App 不直接读取） |

实测注意：

- `torch_vram_total` / `torch_vram_free` 在本机返回 `0`；App 必须使用 `vram_total` / `vram_free`。
- `/features` 返回 `supports_preview_metadata=true`、`max_upload_size=104857600`（100 MB）、`assets=false`。
- `/api/jobs`、`/models`、`/history?max_items=` 在本机均可用（HTTP 200）。
- WebSocket 连接后首条 `status` 已实测，`sid` 回显 `clientId`。
- 已用 64×64、1 step 的测试任务验证完整链路，详见 0.2 节。
- 当前历史中已有该测试任务，带输出的 `/history` 结构已确认。

### 0.2 完整链路验证（2026-11-04）

测试条件：

- 工作流：CheckpointLoaderSimple → CLIPTextEncode ×2 → EmptyLatentImage → KSampler → VAEDecode → SaveImage
- 模型：`anything-v5-PrtRE.safetensors`
- 尺寸：`64×64`，`1 step`，`cfg=1.0`，`euler/normal`
- 客户端指定 `prompt_id`：是
- WebSocket 与 `POST /prompt` 使用同一个 `client_id`

结果：

| 项目 | 结果 |
| --- | --- |
| `POST /prompt` | 200，返回客户端指定的 `prompt_id`（复用成功） |
| 首个事件 | `status`，含 `sid` |
| 执行事件 | `execution_start` → `execution_cached` → `progress_state` / `executing` / `progress` → `executed` → `execution_success` |
| 实际耗时 | 约 3.8 秒 |
| `GET /history/{prompt_id}` | 200，含 `prompt` / `outputs` / `meta` / `status` |
| `GET /view` | 200，`image/png`，64×64 RGB |
| `GET /queue` | 任务结束后恢复为空 |
| 二进制帧 | 收到 1 个预览帧（约 26 KB），V0.3 可忽略 |

结论：

- 客户端指定 `prompt_id` 在 0.27.0 可用，可用于幂等重试。
- `client_id` 路由行为符合预期；同一 `client_id` 的 WebSocket 收到了全部执行事件。
- 0.27.0 新增 `progress_state` 事件，必须纳入事件模型（见第 11 节）。
- 任务结束后不会发送 `executing(node=null)`，而是 `execution_success`。

---

## 1. 连接前提

### 1.1 启动 ComfyUI 监听局域网

默认 ComfyUI 只监听 `127.0.0.1`，手机无法访问。需要：

```bat
python main.py --listen 0.0.0.0 --port 8188
```

或只写 `--listen`（不带值时等价于 `0.0.0.0`；本机实测 `argv` 就是 `--listen`）。也可以在启动脚本/配置中等效设置。`--listen` 也可以指定具体网卡地址，更安全。

### 1.2 获取电脑 IP

```bat
ipconfig
```

找到 Wi-Fi/以太网适配器的 IPv4 地址，例如 `192.168.1.23`。

### 1.3 Windows 防火墙

- 允许 Python/ComfyUI 通过“专用网络”。
- 或添加入站规则：TCP 端口 `8188`。
- 不需要对公网开放。

### 1.4 手机侧检查

- 手机与电脑在同一 Wi-Fi/子网。
- 路由器未开启“AP 隔离/客户端隔离”。
- 企业网络可能禁止设备互访。

### 1.5 浏览器自测

在手机浏览器打开：

```text
http://192.168.1.23:8188/system_stats
```

能返回 JSON 才说明网络通。若浏览器能开、App 不能，检查 App 的明文 HTTP 配置。

### 1.6 Android 模拟器

模拟器访问宿主机使用 `10.0.2.2`，不是 `127.0.0.1`：

```text
http://10.0.2.2:8188
```

真机不能用这个地址。

---

## 2. 端点总表

| 方法 | 路径 | 用途 | 版本 | 本机 0.27.0 | NekoPaint |
| --- | --- | --- | --- | --- | --- |
| GET | `/system_stats` | 系统、GPU、显存、版本 | 核心 | ✅ 200 | V0.1 |
| GET | `/prompt` | 队列剩余数（轻量） | 核心 | ✅ 200 | 可选 |
| GET | `/queue` | 运行中 + 等待中队列 | 核心 | ✅ 200（空闲/忙碌） | V0.3 |
| POST | `/queue` | 清空/删除等待任务 | 核心 | ✅ 删除已测 / 清空未测 | V0.3 |
| GET | `/history` | 历史（可限制条数） | 核心 | ✅ 200（含记录） | V0.3 |
| GET | `/history/{prompt_id}` | 单条历史 | 核心 | ✅ 200 | V0.3 |
| POST | `/history` | 清空/删除历史 | 核心 | ⚠️ 未测 | V0.4（可选） |
| POST | `/prompt` | 提交生成任务 | 核心 | ✅ 200 | V0.2 |
| POST | `/interrupt` | 中断当前任务 | 核心 | ✅ 200（带 prompt_id） | V0.3 |
| GET | `/view` | 获取图片 | 核心 | ✅ 200（PNG） | V0.4 |
| POST | `/upload/image` | 上传输入图片 | 核心 | ⚠️ 未测 | V0.5 |
| POST | `/upload/mask` | 上传蒙版 | 核心 | ⚠️ 未测 | V0.5 |
| GET | `/object_info` | 所有节点定义 | 核心 | ⚠️ 未测（单节点已测） | V0.2（可选） |
| GET | `/object_info/{class}` | 单节点定义 | 核心 | ✅ 200 | 可选 |
| GET | `/models` | 模型目录类型 | 较新 | ✅ 200 | V0.5 |
| GET | `/models/{folder}` | 目录下模型文件 | 较新 | ⚠️ 未测 | V0.5 |
| GET | `/embeddings` | Embedding 名称 | 核心 | ⚠️ 未测 | 可选 |
| GET | `/extensions` | 前端扩展列表 | 核心 | ⚠️ 未测 | 不用 |
| GET | `/features` | 服务器能力开关 | 较新 | ✅ 200 | 可选 |
| POST | `/free` | 卸载模型/释放显存 | 核心 | ⚠️ 未测 | P3 |
| GET | `/api/jobs` | 统一任务列表（分页） | 较新 | ✅ 200（空） | 可选 |
| WS | `/ws?clientId=...` | 实时事件 | 核心 | ✅ 成功/进度/中断已测 | V0.3 |

说明：

- 所有路由同时支持 `/api` 前缀（例如 `/api/prompt`），两种都可用。NekoPaint 优先使用无前缀路径以兼容旧版。
- “较新”表示当前 master 存在，但旧版本可能 404。App 必须把 404 当作“不支持”，而不是崩溃。

---

## 3. `GET /system_stats` ✅

### 本机实测（2026-11-03，ComfyUI 0.27.0）

```json
{
  "system": {
    "os": "win32",
    "ram_total": 16496545792,
    "ram_free": 2225713152,
    "comfyui_version": "0.27.0",
    "python_version": "3.13.11 ...",
    "pytorch_version": "2.9.1+cu128",
    "embedded_python": false,
    "argv": [
      "D:\\comfyui\\ComfyUI-aki-v3\\ComfyUI-aki-v3\\ComfyUI\\main.py",
      "--listen",
      "--auto-launch",
      "--preview-method", "auto",
      "--cuda-malloc"
    ]
  },
  "devices": [
    {
      "name": "cuda:0 NVIDIA GeForce RTX 5060 Laptop GPU : cudaMallocAsync",
      "type": "cuda",
      "index": 0,
      "vram_total": 8546484224,
      "vram_free": 7385120768,
      "torch_vram_total": 0,
      "torch_vram_free": 0
    }
  ]
}
```

> `torch_vram_total` / `torch_vram_free` 在本机为 `0`，不要用它计算显存；使用 `vram_total` / `vram_free`。

### 响应示例（通用）

```json
{
  "system": {
    "os": "nt",
    "ram_total": 34359738368,
    "ram_free": 12884901888,
    "comfyui_version": "0.3.x",
    "python_version": "3.12.x ...",
    "pytorch_version": "2.x.x",
    "embedded_python": false,
    "argv": ["main.py", "--listen", "0.0.0.0"]
  },
  "devices": [
    {
      "name": "cuda:0 NVIDIA GeForce RTX 4070 : cudaMallocAsync",
      "type": "cuda",
      "index": 0,
      "vram_total": 12884901888,
      "vram_free": 9663676416,
      "torch_vram_total": 12884901888,
      "torch_vram_free": 9663676416
    }
  ]
}
```

### 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `system.os` | string | `nt` / `posix` |
| `system.ram_total` / `ram_free` | number | 字节 |
| `system.comfyui_version` | string | 版本；旧版可能没有 |
| `system.python_version` | string | 可能带换行/附加信息 |
| `system.pytorch_version` | string | |
| `system.argv` | array | 启动参数；可用于诊断 `--listen` |
| `devices` | array | 可能有多个 GPU |
| `devices[].vram_total` / `vram_free` | number | 字节 |
| `devices[].torch_vram_*` | number | Torch 视角的显存，可能不同 |

### NekoPaint 使用

- 连接测试：请求成功即视为“是 ComfyUI”。
- 状态页：GPU 名称、显存总量/可用/已用/百分比、内存、版本。
- **不要**假设有 GPU 利用率、温度、功耗；核心接口不提供。
- `vram_total` 必须按字节处理，展示时换算 GB。
- 多 GPU 时默认展示 `devices[0]`，其余折叠。

### 验证要点

- 确认单位是字节还是 MiB（源码为字节，需实测）。
- 确认多 GPU 顺序与 `devices[0]` 是否为主设备。
- 确认旧版本是否缺少 `comfyui_version`。

---

## 4. `GET /queue` ✅

### 本机实测（空闲）

```json
{ "queue_running": [], "queue_pending": [] }
```

### 响应示例（有任务）

```json
{
  "queue_running": [
    [
      1,
      "3f2c1a9e-7b1d-4c2a-9f0e-5a6b7c8d9e0f",
      {
        "3": {
          "class_type": "KSampler",
          "inputs": { "seed": 123, "steps": 20 }
        }
      },
      { "client_id": "app-uuid" },
      ["9"]
    ]
  ],
  "queue_pending": []
}
```

### 结构

每个队列项是数组，API 返回时通常为 5 个元素：

| 下标 | 含义 |
| --- | --- |
| 0 | 队列序号 `number` |
| 1 | `prompt_id` |
| 2 | 完整 prompt（节点图） |
| 3 | `extra_data`（可能含 `client_id`、`create_time` 等） |
| 4 | `outputs_to_execute`（输出节点 ID 数组） |

> 当前 master 内部队列项有第 6 个敏感字段，但 `/queue` 返回前会裁掉。解析时只取前 5 个，忽略多余元素。

### 使用

- 队列页展示运行中/等待中。
- 用 `extra_data.client_id` 判断是否为本机任务（需与本地 clientId 比较）。
- 不要依赖数组长度固定；做容错解析。
- `queue_running` 可能包含多个并行任务（取决于服务器实现）。

### V0.3 本机实测

- [x] 1 个运行 + 1 个等待：`queue_running` / `queue_pending` 结构、序号、`client_id` 均符合预期。
- [x] 运行中任务能通过 WebSocket `progress` / `progress_state` 更新节点与 `value/max`。
- [x] `POST /queue` `{"delete":["<prompt_id>"]}` 返回 200，等待任务从队列消失。
- [ ] 清空队列 `{"clear": true}` 未测试。

### 注意

- 队列项包含完整 prompt，可能很大；不要整页打印日志。
- 旧版本字段可能略有差异，解析时允许 `prompt` 为对象或缺失。

---

## 5. `POST /prompt` ✅

### 本机验证（2026-11-04）

- 提交 7 节点文生图工作流，HTTP 200。
- 响应：

```json
{
  "prompt_id": "32dede36-9b13-43d4-9d0a-6778a53d1c5d",
  "number": 0,
  "node_errors": {}
}
```

- 请求中的客户端 `prompt_id` 被原样复用（`prompt_id_reused=true`）。
- `number` 从 0 开始，是队列序号，不是任务数量。

### 请求

```json
{
  "prompt": {
    "3": {
      "class_type": "KSampler",
      "inputs": {
        "seed": 123456,
        "steps": 20,
        "cfg": 7.5,
        "sampler_name": "euler",
        "scheduler": "normal",
        "denoise": 1.0,
        "model": ["4", 0],
        "positive": ["6", 0],
        "negative": ["7", 0],
        "latent_image": ["5", 0]
      },
      "_meta": { "title": "KSampler" }
    },
    "4": {
      "class_type": "CheckpointLoaderSimple",
      "inputs": { "ckpt_name": "v1-5-pruned-emaonly.safetensors" }
    },
    "5": {
      "class_type": "EmptyLatentImage",
      "inputs": { "width": 512, "height": 512, "batch_size": 1 }
    },
    "6": {
      "class_type": "CLIPTextEncode",
      "inputs": { "text": "a cat", "clip": ["4", 1] },
      "_meta": { "title": "Positive" }
    },
    "7": {
      "class_type": "CLIPTextEncode",
      "inputs": { "text": "", "clip": ["4", 1] },
      "_meta": { "title": "Negative" }
    },
    "9": {
      "class_type": "SaveImage",
      "inputs": { "filename_prefix": "ComfyUI", "images": ["8", 0] }
    }
  },
  "client_id": "0f8fad5b-d9cb-469f-a165-70867728950e",
  "prompt_id": "可选：客户端生成的 UUID"
}
```

### 字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `prompt` | 是 | API 格式节点图，键是节点 ID 字符串 |
| `client_id` | 强烈建议 | 用于把执行事件路由到对应 WebSocket |
| `prompt_id` | 否 | 客户端指定 UUID；0.27.0 已验证可复用，可实现幂等重试 |
| `number` | 否 | 指定队列序号；一般不用 |
| `front` | 否 | 是否插队；一般不用 |
| `extra_data` | 否 | 额外元数据；App 暂不依赖 |
| `extra_data.extra_pnginfo.workflow` | 否 | 把工作流写入 PNG 元数据；可选 |

### 响应成功

```json
{
  "prompt_id": "3f2c1a9e-7b1d-4c2a-9f0e-5a6b7c8d9e0f",
  "number": 12,
  "node_errors": {}
}
```

### 响应失败（HTTP 400）

```json
{
  "error": {
    "type": "prompt_outputs_failed_validation",
    "message": "Prompt outputs failed validation",
    "details": "",
    "extra_info": {}
  },
  "node_errors": {
    "4": {
      "errors": [
        {
          "type": "value_not_in_list",
          "message": "Value not in list",
          "details": "ckpt_name: 'not-exist.safetensors' not in [...]",
          "extra_info": { "input_name": "ckpt_name" }
        }
      ],
      "class_type": "CheckpointLoaderSimple",
      "dependent_outputs": ["9"]
    }
  }
}
```

### 常见错误类型

| type | 含义 | 处理 |
| --- | --- | --- |
| `prompt_outputs_failed_validation` | 节点图校验失败 | 展示 `node_errors` |
| `no_prompt` | 请求缺 `prompt` | 客户端 bug |
| `invalid_prompt_id` | `prompt_id` 不是合法 UUID | 不传或生成合法 UUID |
| `required_input_missing` | 必填输入缺失 | 定位字段 |
| `value_not_in_list` | 枚举值不存在（模型/采样器） | 提示重新选择 |
| `value_smaller_than_min` / `value_bigger_than_max` | 超范围 | 表单校验 |
| `return_type_mismatch` | 节点连线类型不匹配 | 工作流问题 |
| `invalid_input_type` | 类型转换失败 | 工作流/参数问题 |

### 关键行为：事件路由

当前源码中，`execution_start`、`execution_cached`、`executing`、`progress`、`executed`、`execution_success`、`execution_error` 都只发送给 **提交该 prompt 的 `client_id` 对应的 WebSocket**；`execution_interrupted` 与 `status` 会广播。

因此：

1. App 必须为一次会话生成稳定的 `client_id`（UUID）。
2. WebSocket 连接必须使用同一个 `clientId`。
3. 如果 WebSocket 在任务中途断开，重连时仍使用同一 `clientId`，服务端会补发当前 `executing`（若当前执行者是自己）。
4. 如果 `client_id` 不一致，App 提交成功但收不到进度，只能靠轮询。

### 幂等提交（0.27.0 已验证）

0.27.0 允许客户端在顶层传 `prompt_id`（必须是规范 UUID）；本机实测响应中的 `prompt_id` 与请求一致。用途：

- 网络超时后重试，避免重复排队。
- 先落库再提交，保证本地与服务端 ID 一致。

旧版本可能忽略该字段并自行生成 ID。**App 仍应以响应中的 `prompt_id` 为准，同时可比较是否复用。**

---

## 6. `POST /interrupt` ✅（0.27.0 带 prompt_id 实测）

> 2026-11-04 实测：运行中任务 + 等待中任务同时在队列时，带 `prompt_id` 调用返回 200。
> 由于确认弹窗期间前一个任务恰好完成、下一个任务开始，服务端最终中断了“调用时正在运行”的任务；
> 因此本次只能确认“接口可用 + 中断事件可收敛”，**尚不能单独证明服务端严格按 `prompt_id` 过滤**。

### 请求

全局中断（旧版行为）：

```http
POST /interrupt
```

定向中断（较新）：

```json
{ "prompt_id": "3f2c1a9e-..." }
```

### 行为

- 无 body 或解析失败：中断当前正在执行的任务。
- 带 `prompt_id`：仅当该任务正在运行时中断；不是当前任务则忽略（仍返回 200）。
- 返回 `200`，无 body。

### 使用

- 中断按钮默认只中断“本机当前运行任务”。
- 优先带 `prompt_id`，避免误伤其他客户端。
- 中断后状态由 `execution_interrupted` 事件或队列变化确认。
- 不把“HTTP 200”当作“已中断成功”。

---

## 7. `GET /history` / `GET /history/{prompt_id}` ✅

### 本机实测（空）

```json
{}
```

`GET /history?max_items=1` 返回 HTTP 200。

### 本机实测（成功任务，2026-11-04）

```json
{
  "32dede36-9b13-43d4-9d0a-6778a53d1c5d": {
    "prompt": [
      0,
      "32dede36-9b13-43d4-9d0a-6778a53d1c5d",
      { "1": { "class_type": "CheckpointLoaderSimple", "inputs": {} } },
      { "client_id": "ce2d51b6-3c93-4ad5-8d96-1a4dfeccf197" },
      ["7"]
    ],
    "outputs": {
      "7": {
        "images": [
          {
            "filename": "nekopaint_verify_00001_.png",
            "subfolder": "",
            "type": "output"
          }
        ]
      }
    },
    "meta": {
      "7": {
        "node_id": "7",
        "display_node": "7",
        "parent_node": null,
        "real_node_id": "7"
      }
    },
    "status": {
      "status_str": "success",
      "completed": true,
      "messages": [
        ["execution_start", { "prompt_id": "32dede36-...", "timestamp": 1793762353188 }],
        ["execution_cached", { "nodes": [], "prompt_id": "32dede36-...", "timestamp": 1793762353191 }],
        ["execution_success", { "prompt_id": "32dede36-...", "timestamp": 1793762357025 }]
      ]
    }
  }
}
```

实测结论：

- `prompt` 是 5 元素数组，`prompt[0]` 是 `number`，`prompt[1]` 是 `prompt_id`。
- `meta` 每项含 `node_id` / `display_node` / `parent_node` / `real_node_id`。
- `status.messages` 是 `[event, data]` 数组，带 `timestamp`。
- 成功任务 `completed=true`、`status_str="success"`。
- 中断任务（V0.3 实测）会出现 `status_str="error"` / `completed=false`，但 `status.messages` 含 `execution_interrupted`。
  App 必须先检查 `execution_interrupted`，再判断失败，否则会把“已中断”误报为“失败”。
- 失败任务从 `execution_error` 的 `node_id` / `exception_message` 提取失败节点与原因。
- 真实失败样本（损坏图片触发）：`status_str="error"`、`completed=false`，`execution_error.node_id="1"`，
  `exception_type="PIL.UnidentifiedImageError"`，`exception_message` 含服务器本地 input 路径。
  App 历史页显示“失败”、失败节点 1 和原因；不要把完整路径写入日志或提交到仓库。

### 响应示例

```json
{
  "3f2c1a9e-7b1d-4c2a-9f0e-5a6b7c8d9e0f": {
    "prompt": [
      12,
      "3f2c1a9e-7b1d-4c2a-9f0e-5a6b7c8d9e0f",
      { "3": { "class_type": "KSampler", "inputs": {} } },
      { "client_id": "app-uuid" },
      ["9"]
    ],
    "outputs": {
      "9": {
        "images": [
          {
            "filename": "ComfyUI_00012_.png",
            "subfolder": "",
            "type": "output"
          }
        ]
      }
    },
    "meta": {
      "9": { "node_id": "9", "display_node": "9" }
    },
    "status": {
      "status_str": "success",
      "completed": true,
      "messages": [
        ["execution_start", { "prompt_id": "3f2c1a9e-..." }],
        ["execution_success", { "prompt_id": "3f2c1a9e-..." }]
      ]
    }
  }
}
```

### 查询参数

| 参数 | 说明 | 版本 |
| --- | --- | --- |
| `max_items` | 最多返回多少条 | 较新；旧版忽略 |
| `offset` | 跳过多少条 | 较新；旧版忽略 |

V0.3 建议：先请求 `/history?max_items=50`，失败则请求 `/history` 并本地截取。

### 结构

| 字段 | 说明 |
| --- | --- |
| `prompt` | 数组：`[number, prompt_id, prompt, extra_data, outputs_to_execute, ...]`；可能包含敏感 extra_data，不要原样展示 |
| `outputs` | 节点 ID → UI 输出；图片在 `images[]` |
| `meta` | 节点元信息（较新） |
| `status.status_str` | `success` / `error` |
| `status.completed` | 是否完成 |
| `status.messages` | 执行消息数组，可能含错误详情 |
| `status` 可能为 `null` | 任务尚未写入完成状态时 |

### 图片项

```json
{
  "filename": "ComfyUI_00012_.png",
  "subfolder": "",
  "type": "output"
}
```

- `type`：`output`（SaveImage）、`temp`（PreviewImage）、`input`。
- `subfolder` 可能为空字符串。
- 打开图片用 `/view`。

### 使用

- 历史列表只取需要的字段，避免把完整 prompt 存入 UI 状态。
- 失败任务从 `status.messages` 或输出中提取错误；不同版本结构可能不同，需容错。
- `/history/{prompt_id}` 返回同样是 map（只含一条或空对象）。
- 历史是服务器端最终事实；App 重连后用它校准。

### `POST /history`

删除单条：

```json
{ "delete": ["3f2c1a9e-..."] }
```

清空：

```json
{ "clear": true }
```

影响所有客户端，必须二次确认。

---

## 8. `GET /view` ✅

### 本机验证（2026-11-04）

```http
GET /view?filename=nekopaint_verify_00001_.png&subfolder=&type=output
```

- HTTP 200
- `Content-Type: image/png`
- 图片：PNG，64×64，RGB

### V0.4a 本机验证（2026-11-04）

- 真实历史图片：中文文件名 + `subfolder=Anima` + `type=output` 返回 200（约 1.27 MB）。
- `type=temp` 的预览图同样返回 200（约 58 KB）。
- 使用 OkHttp `HttpUrl` 编码后，中文文件名以 UTF-8 百分号形式发送，服务器正确识别。
- 4K 输出 `nekopaint_4k_test_00001_.png`（3840×2160，583 KB）返回 200；App 网格/预览/缩放无 OOM。
- 临时把该文件改名为 `.bak` 后，`/view` 返回 404；App 显示失败占位，恢复文件后重试成功。
- `preview=webp;80` 尚未启用/验证。

### 请求

```http
GET /view?filename=ComfyUI_00012_.png&subfolder=&type=output
```

可选参数：

| 参数 | 说明 |
| --- | --- |
| `preview` | 生成缩略图，格式 `webp;80` / `jpeg;90`（较新） |
| `channel` | `rgb` / `a`，用于取 RGB 或 alpha |

### 响应

- 成功：图片字节，`Content-Type` 为图片类型。
- 失败：`404`（文件不存在）、`400`（文件名非法）、`403`（路径越界）。

### 使用

- 缩略图请求加 `preview=webp;80` 可显著省流量（需验证版本支持；不支持时忽略该参数）。
- 文件名与 `subfolder` 必须 URL 编码。
- 不要自行拼接服务器文件路径；只传服务器返回的值。
- 保存时下载原始字节，不要保存缩略图。

---

## 9. `GET /object_info` ✅（单节点已验证）

### 响应

巨大的 map：`class_type -> 节点定义`。

```json
{
  "KSampler": {
    "input": {
      "required": {
        "model": ["MODEL"],
        "seed": ["INT", { "default": 0, "min": 0, "max": 18446744073709551615 }],
        "steps": ["INT", { "default": 20, "min": 1, "max": 10000 }],
        "cfg": ["FLOAT", { "default": 8.0, "min": 0.0, "max": 100.0 }],
        "sampler_name": [["euler", "euler_ancestral", "..."]],
        "scheduler": [["normal", "karras", "..."]],
        "positive": ["CONDITIONING"],
        "negative": ["CONDITIONING"],
        "latent_image": ["LATENT"],
        "denoise": ["FLOAT", { "default": 1.0, "min": 0.0, "max": 1.0 }]
      }
    },
    "input_order": { "required": ["model", "seed", "..."] },
    "output": ["LATENT"],
    "output_name": ["LATENT"],
    "name": "KSampler",
    "display_name": "KSampler",
    "category": "sampling"
  }
}
```

### 使用

- 参数表单的枚举候选（模型、采样器）优先来自这里。
- 提交前检查工作流所需 `class_type` 是否存在。
- 响应可能几 MB，缓存到本地（带版本/时间），不要每次打开页面都拉。
- 单节点查询 `/object_info/{class}` 更轻量。
- 解析要容忍字段缺失；不同版本结构略有差异。

---

## 10. 其他端点

### `GET /prompt`

返回：

```json
{ "exec_info": { "queue_remaining": 2 } }
```

轻量，可替代 `/queue` 做剩余数展示。

### `GET /models` ✅ / `GET /models/{folder}` ✅（0.27.0 实测）

```http
GET /models
→ ["checkpoints", "configs", "loras", "vae", "text_encoders", ...]
```

本机 0.27.0 实测 `/models` 返回 200，目录包含 `checkpoints`、`loras`、`vae`、`controlnet`、`upscale_models` 等 55+ 项。

`/models/{folder}` 实测：
- `vae` / `loras` 等可能直接返回**裸字符串数组**；
- 也可能返回 `{"value":[...],"Count":n}`；
- App 已用 `ModelListParser` 同时兼容两种格式。

- 目录类型由 `folder_paths` 决定，不同安装不同。
- 不存在时 404。
- 只读；下载/删除不在核心 API 中。
- 模型输入在 `/object_info` 中可能是普通枚举数组或 `{value:[...],Count:n}`，两者都要兼容。

### `POST /upload/image` ✅（0.27.0 实测）

> 实测：上传 3840×2160 PNG 成功，返回 `{"name":"nekopaint_4k_test.png","subfolder":"","type":"input"}`。
> 也上传过内容不是图片的 `nekopaint_bad.png`（文本内容），服务器同样接受并保存；随后 `LoadImage` 执行时报 `PIL.UnidentifiedImageError`。
> 因此上传成功只代表文件已落地，不代表图片可解码。

`multipart/form-data`：

| 字段 | 说明 |
| --- | --- |
| `image` | 文件 |
| `type` | `input`（默认）/ `temp` / `output` |
| `subfolder` | 子目录，可空 |
| `overwrite` | `true`/`1` 时覆盖同名 |

响应：

```json
{ "name": "cat.png", "subfolder": "", "type": "input" }
```

使用时把 `name` 写入 `LoadImage.image`。

### `POST /upload/mask` ⚠️

与 `/upload/image` 类似，额外：

- `original_ref`：JSON 字符串，指向原图 `{filename, subfolder, type}`。
- 服务器会把蒙版 alpha 合成到原图并保存。

App 局部重绘可优先使用 `LoadImage` + 独立蒙版文件；是否需要 `/upload/mask` 取决于所选 inpaint 工作流。

### `POST /free` ❓

```json
{ "unload_models": true, "free_memory": true }
```

释放显存。V1 不主动调用，避免影响其他任务；最多放在高级设置并二次确认。

### `GET /api/jobs` ✅（本机 0.27.0 可用）

```http
GET /api/jobs?status=completed&limit=20&offset=0
```

本机实测（无任务时）：

```json
{
  "jobs": [],
  "pagination": { "offset": 0, "limit": 2, "total": 0, "has_more": false }
}
```

返回统一任务列表与分页信息，可同时覆盖队列与历史。若目标服务器支持，可简化 V0.3 实现；但为了兼容旧版，仍需保留 `/queue` + `/history` 路径。

### `POST /api/jobs/{job_id}/cancel` ❓（较新）

可取消运行中或等待中的任务。比 `/interrupt` + `/queue delete` 更直接。能力探测后使用。

---

## 11. WebSocket `/ws` ✅（成功任务全流程已验证）

### 连接

```text
ws://192.168.1.23:8188/ws?clientId=0f8fad5b-d9cb-469f-a165-70867728950e
```

- HTTPS 时用 `wss://`。
- `clientId` 建议使用安装级稳定 UUID；至少在一次任务生命周期内不变。
- 无认证；局域网内任何人可连接。

### 服务端连接后首条消息

本机实测（`clientId=nekopaint-verify-...`）：

```json
{
  "type": "status",
  "data": {
    "status": { "exec_info": { "queue_remaining": 0 } },
    "sid": "nekopaint-verify-9dc7873c266e48339a61e5443b1e8f28"
  }
}
```

说明：`sid` 会回显传入的 `clientId`，后续执行事件按这个 `sid` 路由。

### 事件类型

| type | data | 说明 |
| --- | --- | --- |
| `status` | `status.exec_info.queue_remaining`, `sid`（仅首条） | 队列变化/连接建立 |
| `execution_start` | `prompt_id`, `timestamp` | 任务开始 |
| `execution_cached` | `prompt_id`, `nodes` | 命中缓存的节点 |
| `executing` | `node`, `display_node`, `prompt_id` | 当前节点；0.27.0 不再用 `node=null` 表示结束 |
| `progress` | `node`, `prompt_id`, `value`, `max` | 节点内进度；只有实现钩子的节点才有 |
| `progress_state` | `prompt_id`, `nodes{}` | **0.27.0 新增**：每个节点的 `value/max/state` 快照 |
| `executed` | `node`, `display_node`, `output`, `prompt_id` | 节点产出（如 `output.images`） |
| `execution_success` | `prompt_id`, `timestamp` | 全部成功 |
| `execution_error` | `prompt_id`, `node_id`, `node_type`, `executed`, `exception_message`, `exception_type`, `traceback`, `current_inputs`, `current_outputs` | 执行失败 |
| `execution_interrupted` | `prompt_id`, `node_id`, `node_type`, `executed` | 被中断 |
| `feature_flags` | 能力开关 | 较新；可忽略 |

`progress_state` 示例（本机实测，已删减其他节点）：

```json
{
  "type": "progress_state",
  "data": {
    "prompt_id": "32dede36-9b13-43d4-9d0a-6778a53d1c5d",
    "nodes": {
      "5": {
        "value": 1,
        "max": 1,
        "state": "finished",
        "node_id": "5",
        "prompt_id": "32dede36-9b13-43d4-9d0a-6778a53d1c5d",
        "display_node_id": "5",
        "parent_node_id": null,
        "real_node_id": "5"
      },
      "6": {
        "value": 0.0,
        "max": 1.0,
        "state": "running",
        "node_id": "6",
        "display_node_id": "6",
        "parent_node_id": null,
        "real_node_id": "6"
      }
    }
  }
}
```

`state` 观测值：`running`、`finished`。

事件示例：

```json
{"type":"execution_start","data":{"prompt_id":"3f2c...","timestamp":1730000000000}}
{"type":"executing","data":{"node":"3","display_node":"3","prompt_id":"3f2c..."}}
{"type":"progress","data":{"value":5,"max":20,"prompt_id":"3f2c...","node":"3"}}
{"type":"executed","data":{"node":"9","display_node":"9","prompt_id":"3f2c...","output":{"images":[{"filename":"ComfyUI_00012_.png","subfolder":"","type":"output"}]}}}
{"type":"execution_success","data":{"prompt_id":"3f2c...","timestamp":1730000000000}}
```

### 事件顺序（本机实测，0.27.0）

```text
status (sid, queue_remaining=0)
status (queue_remaining=1)
status (queue_remaining=1)        # 可能重复
execution_start
execution_cached
progress_state (node 1 running)
executing (node 1)
progress_state (node 1 finished, node 4 running)
executing (node 4)
...
progress_state / executing (node 5)
progress (value=1, max=1, node=5)
progress_state (node 6)
executing (node 6)
progress_state (node 7)
executing (node 7)
executed (node 7, output.images)
progress_state (all finished)
execution_success
```

注意：

- **0.27.0 同时发送 `progress_state`、`executing`、`progress`**。App 应以 `progress_state` 作为节点状态主来源，`progress` 作为细粒度补充，`executing` 用于兼容旧版。
- `progress_state.nodes` 是累计快照，可能包含多个节点；每次只更新相关节点，不要重建整个页面。
- `status` 可能重复，且只有首条带 `sid`；App 取最新 `queue_remaining` 即可。
- `progress` 只在该节点实现进度钩子时发送；本机测试只有 KSampler 有。
- `executed` 只在该节点返回 UI 输出时发送，不等同于“节点完成”。
- `execution_success` 不一定在旧版本出现；旧版可能用 `executing(node=null)` 表示结束。
- `execution_error` 只发给提交者；`execution_interrupted` 会广播。
- 事件可能因断线丢失，最终以 `/history/{prompt_id}` 为准。

### 二进制帧

格式：`4 字节大端事件类型 + 数据`。

| 事件类型 | 含义 | 数据 |
| --- | --- | --- |
| 1 | 预览图 | `4 字节图片类型（1=JPEG, 2=PNG）+ 图片字节` |
| 2 | 未编码预览图 | 内部使用，忽略 |
| 3 | 文本进度 | `4 字节 nodeId 长度 + nodeId + 文本` |
| 4 | 带元数据预览 | `4 字节元数据长度 + JSON + 图片` |

本机测试收到 1 个预览帧（约 26 KB），但未解析其内容。V0.3 可忽略所有二进制帧；V0.5 可选择性展示实时预览。解析前必须确认长度边界，避免越界。

### 重连策略

- 使用同一 `clientId` 重连。
- 重连后可能收到 `status`，若当前执行者是自己，还会收到 `executing`。
- 重连后主动拉取 `/queue` 与 `/history`，补齐丢失事件。
- 不要假设事件按顺序完整到达。

### 心跳

- OkHttp 设置 `pingInterval(20s)`。
- 连续无任何消息且心跳失败时，主动断开并重连。
- 页面不可见时可暂停重连，回到前台恢复。

---

## 12. 工作流 JSON 格式

### 12.1 API 格式（V0.2 只支持这个）

```json
{
  "3": {
    "class_type": "KSampler",
    "inputs": {
      "seed": 123,
      "steps": 20,
      "cfg": 7.5,
      "sampler_name": "euler",
      "scheduler": "normal",
      "denoise": 1.0,
      "model": ["4", 0],
      "positive": ["6", 0],
      "negative": ["7", 0],
      "latent_image": ["5", 0]
    },
    "_meta": { "title": "KSampler" }
  },
  "4": {
    "class_type": "CheckpointLoaderSimple",
    "inputs": { "ckpt_name": "model.safetensors" }
  }
}
```

特征：

- 顶层是对象，不是数组。
- 键是节点 ID（字符串）。
- 每个值含 `class_type`、`inputs`。
- 连线值是 `[源节点ID, 输出索引]`。
- `_meta.title` 可选，用于展示。

获取方式：ComfyUI 设置中开启开发者模式选项，使用 **Save (API Format)** 导出。

### 12.2 UI 格式（V0.2 拒绝）

```json
{
  "last_node_id": 9,
  "last_link_id": 12,
  "nodes": [
    {
      "id": 3,
      "type": "KSampler",
      "widgets_values": [123, "randomize", 20, 7.5, "euler", "normal", 1.0],
      "inputs": [],
      "outputs": []
    }
  ],
  "links": [[1, 4, 0, 3, 0, "MODEL"]],
  "version": 0.4
}
```

特征：

- `nodes` 数组 + `links` 数组。
- 控件值在 `widgets_values` 中按位置排列，没有字段名。
- 不能直接提交给 `/prompt`。

转换 UI → API 需要理解每个节点的控件顺序、连线、组、子图、动态输入，工作量大且易错。**V0.2 不做**；后续如需要，单独立项。

### 12.3 参数候选

| class_type | inputs | 展示 | 类型 |
| --- | --- | --- | --- |
| `CLIPTextEncode` | `text` | 提示词 | 多行文本 |
| `KSampler` | `seed` | 种子 | INT + 随机 |
| `KSampler` | `steps` | 步数 | INT |
| `KSampler` | `cfg` | CFG | FLOAT |
| `KSampler` | `sampler_name` | 采样器 | ENUM |
| `KSampler` | `scheduler` | 调度器 | ENUM |
| `KSampler` | `denoise` | 重绘幅度 | FLOAT |
| `KSamplerAdvanced` | 同名 | 同上 | 同上 |
| `EmptyLatentImage` | `width` / `height` / `batch_size` | 尺寸/批次 | INT |
| `CheckpointLoaderSimple` | `ckpt_name` | 模型 | ENUM |
| `LoraLoader` | `lora_name` / `strength_model` / `strength_clip` | LoRA | ENUM/FLOAT |
| `VAELoader` | `vae_name` | VAE | ENUM |
| `LoadImage` | `image` | 输入图 | IMAGE |
| `SaveImage` | `filename_prefix` | 文件名前缀 | TEXT（可选） |

注意：

- `seed` 的 `control_after_generate` 是 UI 概念，API JSON 中不存在；App 自己实现“提交时随机”。
- 正向/反向提示词都是 `CLIPTextEncode`，需要根据标题或连接关系区分；无法判断时让用户手动标记。
- 自定义节点的输入名可能不同；自动识别失败不报错。
- 枚举候选优先 `/object_info`；拿不到时用 JSON 中当前值作为唯一选项。

### 12.4 真实案例：ANIMA_1.0 的 API 导出

2026-11-04 分析了 `ANIMA_1.0` 的 API 格式导出：

- 文件大小约 14 KB，顶层是节点 ID，没有 `nodes` / `links`，格式正确。
- 共 **31 个节点、22 种 class_type**。
- UI 格式的 95 个节点中，旁路节点已被移除，`SetNode/GetNode` 已被解析。
- 仍保留少量 UI/预览类节点：`Image Comparer (rgthree)` ×3、`GroupIgnoreManager` ×2、`PreviewImage`、`PreviewAny`、`DanbooruGalleryNode`。App 应忽略它们，只把 `SaveImage` 作为主输出。

建议绑定位置：

| 手机字段 | 节点 / 输入 | 说明 |
| --- | --- | --- |
| 主体提示词 | `WeiLinPromptUI(310).positive` | 直接字符串；`temp_str` 是复杂 token 数据，不要暴露 |
| 质量词 | `CR Text(103).text` | 简单字符串 |
| 画师词 | `CR Text(100).text` | 简单字符串 |
| 一采步数 / CFG / 种子 | `KSampler(63).steps/cfg/seed` | 第一段采样 |
| 二采步数 / CFG / 重绘幅度 / 种子 | `KSampler(111).steps/cfg/denoise/seed` | 第二段采样 |
| 宽 / 高 | `INTConstant(97/98).value` | `EmptyLatentImage(28)` 的输入是连线，不能直接改 |
| 输入图片 | `LoadImage(405).image` | 需要上传或选择服务器文件 |
| 最终边长 | `ImageResizeKJv2(115).height` | 当前 1280 |
| 放大倍数 | `ImageScaleBy(108).scale_by` | 当前 1.33 |
| 输出前缀 | `SaveImage(404).filename_prefix` | 可选 |

不要绑定：

- `CLIPTextEncode(11).text`：它由 `PreviewAny(92) → JoinStringMulti(89) → WeiLinPromptUI(310) / CR Text` 提供，直接改会被上游覆盖。
- `WeiLinPromptUI.temp_str`：结构化 token JSON，App 不应编辑。
- `Image Comparer` 的临时图片 URL：只用于 PC 端对比预览。
- `GroupIgnoreManager` / `DanbooruGalleryNode`：没有生成输出，忽略即可。

这验证了两个设计点：

1. 绑定不能只看 `class_type`，必须允许**上游绑定**（例如宽高实际在 `INTConstant`，不在 `EmptyLatentImage`）。
2. 复杂工作流应允许把某个节点标为“只读/由电脑端管理”，而不是强迫手机编辑所有字段。

### 12.5 复杂 UI 工作流案例：IL_Controlnet_V2.6

`workflows/IL_Controlnet_V2.6.json` 是一个更极端的参考：

- **UI 格式**：197 个节点、224 条连线、17 个分组。
- **41 个旁路节点**，启用 156 个。
- **48 种 class_type**，包含大量自定义节点：
  - `GetNode` 33、`SetNode` 18、`Reroute` 11
  - `WeiLinPromptUIWithoutLora` 11
  - `DetailerForEach` 6、`SAM3_Detect` 5、`MaskToSEGS` 7、`INPAINT_ExpandMask` 5
  - `ControlNetApplyAdvanced` 3、`ControlNetLoader` 2
  - `SDPose`、`YOLO`、`DepthAnythingV2`、`PreviewBridge`、`Image Comparer` 等
- 分组覆盖：一采、二采高清修复、Depth + SDpose、部位优化 1–4、Danbooru、最终图、模型及接口、重绘提示词、部位选择、手动/半自动修复等。

处理建议：

1. 在 ComfyUI 中只启用当前要用的路径，再导出 **API 格式**。
2. 把不同使用模式导出成不同变体：
   - 文生图（一采 + 二采）
   - 图生图 / ControlNet
   - 局部修复（部位优化）
3. 手机端只绑定少量参数：
   - 主提示词、反向提示词
   - 宽 / 高
   - 一采步数 / CFG / 种子
   - 二采重绘幅度
   - ControlNet 强度（若保留）
   - 输入图片
4. 不要把 11 个 `WeiLinPromptUIWithoutLora` 全部暴露；选择其中真正控制主提示词的一个，其余标为只读。
5. 旁路分支（部位优化 1–4）不能通过 API JSON 开关；需要单独导出变体。

---

## 13. 版本兼容与能力探测

### 13.1 版本信息

- 较新版本 `/system_stats` 返回 `system.comfyui_version`。
- 本机 0.27.0 返回 `comfyui_version: "0.27.0"`。
- 旧版本可能没有；不要依赖版本号做硬判断。
- `/features` 可能返回服务器能力；可选。本机返回 `supports_preview_metadata=true`、`max_upload_size=104857600`、`assets=false`。

### 13.2 推荐探测方式

| 能力 | 探测 |
| --- | --- |
| 基础连接 | `GET /system_stats` 200 |
| 历史分页 | `GET /history?max_items=1` 200 |
| 模型列表 | `GET /models` 200 |
| 节点信息 | `GET /object_info` 200 |
| 客户端 prompt_id | 提交时带 UUID，检查响应是否一致 |
| 定向中断 | 带 `prompt_id` 调用，观察是否只影响目标任务 |
| 统一任务接口 | `GET /api/jobs` 200 |

原则：**调用成功才启用功能；404/解析失败一律降级，不阻塞主流程。**

### 13.3 已知差异

| 项目 | 旧版 | 新版（0.27.0） |
| --- | --- | --- |
| 执行结束事件 | `executing(node=null)` | `execution_success` |
| 节点状态事件 | 只有 `executing` / `progress` | 新增 `progress_state`（累计节点快照） |
| 历史分页 | 无 `max_items` | 支持 |
| 客户端 `prompt_id` | 可能忽略 | 支持 UUID，实测复用 |
| `/api/jobs` | 无 | 有 |
| `/models` | 无或不同 | 有 |
| 预览缩略图 | 无 `preview` 参数 | 支持 |
| `status.messages` | 结构可能不同 | `[event, data]`，带 `timestamp` |

App 解析必须同时兼容两套。

---

## 14. 安全与网络

- ComfyUI 默认 **无认证、无授权**。任何能访问 `IP:8188` 的人都可以提交任务、读取历史、查看输出图片。
- 不要把端口映射到公网；不要在不可信 Wi-Fi 使用。
- Windows 防火墙只对“专用网络”放行。
- 若使用 `--listen 0.0.0.0`，同一局域网内所有设备都可访问；可用具体网卡 IP 限制。
- ComfyUI 支持 `--tls-keyfile` / `--tls-certfile` 启用 HTTPS；App 的 HTTPS 支持与自签名证书策略待定。
- App 不发送凭据；若未来加 Token，必须使用加密存储，不写入日志。
- 历史与队列响应包含完整提示词，属于隐私数据；不要在日志/崩溃报告中输出。

---

## 15. 错误处理约定（App 侧）

| 场景 | 判定 | 用户提示方向 |
| --- | --- | --- |
| 连接被拒绝 | `ConnectException` | 服务是否启动、端口是否正确 |
| 超时 | `SocketTimeoutException` | 网络/防火墙/服务卡顿 |
| 未知主机 | `UnknownHostException` | IP/主机名是否正确 |
| HTTP 400 + `node_errors` | 校验失败 | 定位节点与输入 |
| HTTP 404 | 端点/图片不存在 | 版本不支持或文件已删除 |
| HTTP 5xx | 服务器异常 | 查看 ComfyUI 控制台 |
| WS 关闭 | `onClosed/onFailure` | 自动重连，提示实时进度中断 |
| 图片解码失败 | 非图片响应 | 检查 `/view` 参数与服务器日志 |
| 历史缺失 | 任务已完成但未写入 | 稍后重试，不判定失败 |

---

## 16. V0.0 真实服务验证清单

> 每项验证后，把实际响应脱敏保存到 `app/src/test/resources/fixtures/`，并在本文档标注“已验证 + 日期 + ComfyUI 版本”。
> 本机已验证项标注 `[x]`，日期 2026-11-04，版本 0.27.0。第一轮（2026-11-03）验证了主要 HTTP 端点；第二轮验证了完整成功任务。

### 环境

- [x] 确认 ComfyUI 版本（`/system_stats` 返回 `0.27.0`）
- [x] 确认启动参数包含 `--listen`（`argv` 中有 `--listen`）
- [x] 手机浏览器能访问 `/system_stats`（用户确认局域网已通）
- [ ] Android 模拟器/native 能访问（真机优先）

### HTTP

- [x] `GET /system_stats`：确认显存单位、GPU 数量、版本字段
- [x] `POST /prompt`：提交最小文生图工作流，记录成功响应
- [ ] `POST /prompt`：故意使用不存在的模型，记录 400 与 `node_errors`
- [x] `GET /queue`：空闲时返回 `{"queue_running":[],"queue_pending":[]}`
- [x] `GET /queue`：1 个运行 + 1 个等待时抓取，字段符合预期
- [x] `GET /history?max_items=10`：返回 200，已包含测试任务
- [x] `GET /history?max_items=100`：100+ 条记录返回正常，App 列表滚动无 OOM
- [x] `GET /history/{prompt_id}`：记录单条结构
- [x] `GET /view`：原图返回 200 PNG；4K 图片 200；不存在文件 404
- [ ] `GET /view`：`preview=webp;80` 待测
- [x] `POST /interrupt`：带 `prompt_id` 返回 200，任务以 interrupted 收敛
- [ ] `POST /interrupt`：全局（无 body）与“非当前任务”行为待补测
- [x] `POST /queue`：删除等待任务成功
- [ ] `POST /queue`：清空队列待测
- [x] `GET /object_info/KSampler`：返回 200，确认 seed/steps/cfg 等字段
- [ ] `GET /object_info`：全量响应
- [x] `GET /models`：返回 200，确认目录列表
- [x] `GET /models/{folder}`：checkpoints/loras/vae 返回 200；裸数组与 `{value,Count}` 两种格式已兼容
- [x] `POST /upload/image`：4K PNG 上传成功；非图片文件也会被接受，执行时才失败
- [x] 客户端指定 `prompt_id`：确认响应复用该 ID
- [x] `GET /api/jobs`：返回 200，确认分页结构（当前已含测试任务）
- [x] `GET /features`：返回 200，确认 `max_upload_size` 与能力开关

### WebSocket

- [x] 使用与提交一致的 `client_id` 连接，记录首条 `status`
- [x] 确认 `sid` 回显 `clientId`
- [x] 记录一次完整成功任务的全部文本事件与顺序
- [ ] 记录一次 WebSocket 实时 `execution_error`（已通过 `/history` 验证失败字段，但该任务不是 App client_id 提交，未捕获实时帧）
- [x] 记录一次中断的 `execution_interrupted`（App 状态显示“已中断”，历史状态正确）
- [x] 记录忙碌队列下的 `progress` / `progress_state`（节点名与 value/max 正常）
- [x] 记录二进制预览帧存在（约 26 KB，内容未解析）
- [x] 断开重连：移除并恢复端口转发后，WebSocket 能自动重连成功
- [ ] 重连后是否补发 `executing`、后续任务事件是否完整（待有运行中任务时补测）
- [ ] 使用不同 `client_id` 连接：确认是否收不到执行事件（验证路由行为）
- [x] 记录 `execution_success` 是否包含 `timestamp`，旧版是否用 `executing:null`

### 工作流

- [ ] 导出一份 API 格式文生图 JSON（脱敏）
- [ ] 导出一份 API 格式图生图/局部重绘 JSON（脱敏）
- [ ] 确认 UI 格式导出与 API 格式导出的差异
- [ ] 确认 `_meta.title` 是否存在，节点 ID 是否稳定
- [ ] 确认自定义节点是否影响参数识别

### 兼容

- [ ] 在目标最低/最高 ComfyUI 版本上重复关键项
- [ ] 记录哪些端点 404、哪些字段缺失
- [ ] 确认无认证前提下，局域网其他设备可访问（安全提示依据）

---

## 17. 参考

- ComfyUI Server Routes（官方文档）
  <https://docs.comfy.org/development/comfyui-server/comms_routes>
- ComfyUI Server Messages（官方文档）
  <https://docs.comfy.org/development/comfyui-server/comms_messages>
- ComfyUI 源码 `server.py`
  <https://github.com/comfyanonymous/ComfyUI/blob/master/server.py>
- ComfyUI 源码 `execution.py`
  <https://github.com/comfyanonymous/ComfyUI/blob/master/execution.py>
- ComfyUI 源码 `protocol.py`
  <https://github.com/comfyanonymous/ComfyUI/blob/master/protocol.py>

---

## 18. 待确认

1. **已确认**：ComfyUI `0.27.0`，启动参数 `--listen --auto-launch --preview-method auto --cuda-malloc`。
2. **待确认**：安装了哪些自定义节点？常用哪些？（影响工作流兼容与参数识别）
3. **已确认**：V0.2 只支持 API 格式 JSON。
4. **待提供**：脱敏的 API 格式文生图/局部重绘 JSON；一次成功任务的事件序列。
5. **待确认**：是否需要 HTTPS/自签名证书？
6. **已确认**：V1 不做后台保活，回前台恢复状态。
7. **已确认**：V1 不提供服务器历史清空。

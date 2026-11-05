# 鲸聊（WhaleChat）

微信 AI 回复助手：读取微信聊天界面 → 按选定人格生成候选回复 → 点一条发送。  
支持三种人格（Jev / 狗头军师 / 恋爱大师）和两种后端（直连 DeepSeek / 接「鲸聊桥」服务）。

> 个人自用工具，不能上架应用商店（无障碍权限敏感）。自动聊天有平台风控与封号风险，建议先用草稿模式。

## 功能

- 只监听微信（`com.tencent.mm`），读取当前私聊界面的最近消息。
- 无障碍读屏 + 悬浮窗选回复；默认手动点发，自动发送需单独打开。
- 三种模式：
  - `Jev`：一条自然的短句（去 AI 味，≤40 字）
  - `狗头军师`：局势判断 + 1~3 条
  - `恋爱大师`：按阶段给 3 条 + 预测
- 两种后端：
  - **直连**：App 里填 DeepSeek API Key，直接调 `api.deepseek.com`。
  - **桥服务**：调自建的「鲸聊桥」（`bridge/`），技能/提示词/Key 都在服务端，App 只发上下文；桥挂了会自动回退到直连（如果填了 Key）。

## 构建 APK（GitHub Actions，免费）

1. 新建一个 GitHub 仓库（Private 也可以）。
2. 把本目录**解压后的全部内容**上传到仓库根目录（包含 `.github/`，否则不会触发构建）。
3. 打开仓库的 **Actions** 页，等 `build-apk` 跑完（约 3–5 分钟）。
4. 在该次运行底部下载 `whalechat-debug-apk` 压缩包，里面是 `app-debug.apk`，传到手机安装（允许「安装未知来源应用」）。

改代码后 push 一次就会自动重新打包。

本地构建（有 Android Studio / JDK 17 的电脑）：

```bash
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 手机上的首次设置

1. 安装 APK，打开「鲸聊」。
2. 填 **DeepSeek API Key**（直连模式），或把「回复来源」切到 **接入我（DSH 桥）** 并填桥地址。
3. 点「① 打开无障碍设置」→ 开启「鲸聊屏幕助手」。
4. 点「② 授予悬浮窗权限」→ 允许。
5. 打开微信，进入一个**私聊**界面。她发新消息后，悬浮窗会弹出候选回复。
6. 调试：在微信聊天界面点 App 里的「③ 复制当前屏幕结构」，把内容发出来即可调解析规则。

## 鲸聊桥（可选后端）

```bash
cd bridge
# 联调（不调模型）
MOCK=1 sh run.sh

# 正常
DEEPSEEK_API_KEY=sk-xxx sh run.sh

# 部署到服务器（改成对外监听 + token）
HOST=0.0.0.0 WHALECHAT_TOKEN=换一个随机串 DEEPSEEK_API_KEY=sk-xxx sh run.sh
```

接口：

- `GET /whalechat/api/ping` → `{"ok":true,...}`
- `POST /whalechat/api/reply`
  - Header：`X-Whalechat-Token: <token>`（服务端设置了才校验）
  - Body：`{"mode":"jev|goutou|love","stage":1|2|3,"contact":"","notes":"","messages":[{"fromMe":false,"text":"..."}]}`
  - 返回：`{"analysis":"...","options":[{"tone":"","text":"","prediction":""}]}`

服务器上建议用 systemd/supervisor 守护，只监听内网或加 token。

## 已知限制

- 只支持微信**私聊**；群聊会把所有人当成「她」。
- 微信界面结构变了可能需要改 `WeChatParser.kt`（用调试按钮导出的节点树对照改）。
- 读不到文字时需要退化到截图 + 视觉模型（未实现）。
- 自动发送建议只在小号上试。

## 隐私

聊天内容会发送到你选择的模型服务（DeepSeek 或自建桥）。不要在这台手机上处理敏感对话。

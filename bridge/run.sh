#!/system/bin/sh
# 鲸聊桥：默认只监听本机 3220，给 App 做回复后端。
# 用法：
#   MOCK=1 sh run.sh            # 不调模型，返回假回复（联调用）
#   DEEPSEEK_API_KEY=sk-xx sh run.sh
# 服务器上：HOST=0.0.0.0 WHALECHAT_TOKEN=换成随机串 sh run.sh
cd "$(dirname "$0")" || exit 1
exec node server.mjs

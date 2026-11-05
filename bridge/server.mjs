import http from 'node:http'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const SKILLS = join(__dirname, '..', 'app', 'src', 'main', 'assets', 'skills')

const PORT = parseInt(process.env.PORT || '3220', 10)
const HOST = process.env.HOST || '127.0.0.1'
const TOKEN = process.env.WHALECHAT_TOKEN || ''
const API_KEY = process.env.DEEPSEEK_API_KEY || ''
const BASE = (process.env.DEEPSEEK_BASE || 'https://api.deepseek.com').replace(/\/+$/, '')
const MODEL = process.env.DEEPSEEK_MODEL || 'deepseek-chat'
const MOCK = process.env.MOCK === '1'

const cache = new Map()
async function asset(name) {
  if (!cache.has(name)) {
    cache.set(name, await readFile(join(SKILLS, name), 'utf8'))
  }
  return cache.get(name)
}

function stageText(stage) {
  if (Number(stage) === 1) return '刚加上'
  if (Number(stage) === 3) return '有好感/暧昧'
  return '聊过几次'
}

async function buildPrompt(body) {
  const mode = body.mode || 'jev'
  let modeText
  if (mode === 'goutou') {
    modeText = await asset('goutou_skill.md')
  } else if (mode === 'love') {
    modeText = (await asset('love_skill.md')) + '\n\n' + (await asset('love_playbook.md'))
  } else {
    modeText = (await asset('jev_skill.md')) + '\n\n' + (await asset('jev_draft.md'))
  }

  const system = `你是「鲸聊」——装在手机上的微信回复助手，替用户起草回复。下面是你必须遵守的工作方法（来自技能库）：

${modeText}

输出要求（必须严格遵守）：
- 只输出一个 JSON 对象，不要 markdown 代码块，不要任何解释文字。
- 结构：{"analysis":"一句话判断","options":[{"tone":"稳健|轻松|直接","text":"回复正文","prediction":"她可能怎么回"}]}
- options 给 2~3 条，text 每条不超过 40 个汉字，像真人微信，别堆表情、别堆排比、别写成小作文。
- 不编造事实、记忆、共同经历；不确定就承接或轻轻提问。`

  const messages = Array.isArray(body.messages) ? body.messages.slice(-20) : []
  const history = messages.map(m => (m.fromMe ? '我' : '她') + '：' + (m.text || '')).join('\n')
  const user = `对方：${body.contact || '当前聊天对象'}（阶段：${stageText(body.stage)}）
备注：${body.notes || '无'}
最近对话：
${history}

请针对她最新的一条，给出回复建议。`

  return { system, user }
}

function extractJson(text) {
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start < 0 || end <= start) throw new Error('模型没有返回 JSON')
  return JSON.parse(text.slice(start, end + 1))
}

async function callModel(system, user) {
  if (MOCK) {
    return JSON.stringify({
      analysis: 'mock 模式，未调用模型',
      options: [
        { tone: '稳健', text: '刚看到，怎么啦？', prediction: '' },
        { tone: '轻松', text: '这句话我记下了，回头找你算账。', prediction: '' },
        { tone: '直接', text: '在忙，晚点回你。', prediction: '' }
      ]
    })
  }
  if (!API_KEY) throw new Error('没有配置 DEEPSEEK_API_KEY')

  const res = await fetch(`${BASE}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${API_KEY}`
    },
    body: JSON.stringify({
      model: MODEL,
      temperature: 0.8,
      max_tokens: 600,
      stream: false,
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: user }
      ]
    })
  })

  if (!res.ok) {
    const detail = await res.text()
    throw new Error(`模型 HTTP ${res.status}: ${detail.slice(0, 160)}`)
  }
  const data = await res.json()
  const content = data?.choices?.[0]?.message?.content
  if (!content) throw new Error('模型返回为空')
  return content
}

function send(res, code, obj) {
  const body = JSON.stringify(obj)
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' })
  res.end(body)
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && (req.url === '/whalechat/api/ping' || req.url === '/')) {
      return send(res, 200, { ok: true, service: 'whalechat-bridge', mock: MOCK })
    }
    if (req.method !== 'POST' || !req.url.startsWith('/whalechat/api/reply')) {
      return send(res, 404, { error: 'not found' })
    }
    if (TOKEN && req.headers['x-whalechat-token'] !== TOKEN) {
      return send(res, 401, { error: 'bad token' })
    }

    let raw = ''
    for await (const chunk of req) raw += chunk
    const body = JSON.parse(raw || '{}')

    const { system, user } = await buildPrompt(body)
    const content = await callModel(system, user)
    const result = extractJson(content)
    if (!Array.isArray(result.options) || result.options.length === 0) {
      throw new Error('模型没有给出 options')
    }
    return send(res, 200, result)
  } catch (error) {
    console.error('[whalechat-bridge]', error.message)
    return send(res, 500, { error: error.message })
  }
})

server.listen(PORT, HOST, () => {
  console.log(`[whalechat-bridge] listening on http://${HOST}:${PORT} mock=${MOCK}`)
})

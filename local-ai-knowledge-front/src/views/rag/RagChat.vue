<template>
  <div class="rag-chat-container">
    <div class="chat-wrapper">
      <!-- 左侧历史会话 -->
      <div class="history-panel" :class="{ 'is-collapsed': isHistoryCollapsed }">
        <div class="history-header">
          <div class="header-left">
            <el-button text @click="toggleHistory" class="close-btn">
              <el-icon><Fold /></el-icon>
            </el-button>
            <h3>历史会话</h3>
          </div>
          <el-button text @click="createNewSession">
            <el-icon><Plus /></el-icon>
            新对话
          </el-button>
        </div>
        <div class="history-list">
          <!-- 新对话（仅当用户主动新建且未发消息时高亮） -->
          <div
            v-if="!currentSessionId"
            class="history-item current-chat active"
          >
            <el-icon><ChatLineSquare /></el-icon>
            <span class="session-text">新对话</span>
            <el-tag size="small" type="primary">编辑中</el-tag>
          </div>

          <!-- 历史会话列表 -->
          <div
            v-for="session in sessions"
            :key="session.sessionId"
            class="history-item"
            :class="{ active: currentSessionId === session.sessionId }"
            @click="selectSession(session.sessionId)"
          >
            <el-icon><ChatLineSquare /></el-icon>
            <span class="session-text">{{ session.title || formatSessionId(session.sessionId) }}</span>
            <el-icon
              class="action-btn"
              @click.stop="openRenameDialog(session)"
            ><Edit /></el-icon>
            <el-icon
              class="action-btn delete-btn"
              @click.stop="deleteSession(session.sessionId)"
            ><Delete /></el-icon>
          </div>
          <el-empty v-if="sessions.length === 0 && currentSessionId" description="暂无历史会话" :image-size="60" />
        </div>
      </div>

      <!-- 移动端遮罩层 -->
      <div 
        v-if="!isHistoryCollapsed && isMobile" 
        class="mobile-overlay" 
        @click="isHistoryCollapsed = true"
      ></div>

      <!-- 右侧聊天区域 -->
      <div class="chat-panel">
        <!-- 聊天头部 -->
        <div class="chat-header">
          <el-button text @click="toggleHistory">
            <el-icon><Expand v-if="isHistoryCollapsed" /><Fold v-else /></el-icon>
            历史会话
          </el-button>
          <h2>智能问答</h2>
          <el-button text @click="clearChat">
            <el-icon><Delete /></el-icon>
            清空
          </el-button>
        </div>

        <!-- 消息列表 -->
        <div class="message-list" ref="messageListRef">
          <div class="welcome-message">
            <el-icon class="welcome-icon" :size="48"><ChatDotRound /></el-icon>
            <h3>欢迎使用智能问答</h3>
            <p>我可以帮您基于知识库回答问题，支持网络搜索降级</p>
          </div>

          <template v-for="(msg, index) in messages" :key="index">
            <!-- system 提示（如模式切换通知）：灰色居中，不带头像 -->
            <div v-if="msg.role === 'system'" class="message-system">
              {{ msg.content }}
            </div>
            <div v-else class="message-item" :class="msg.role">
            <div class="message-avatar">
              <el-avatar v-if="msg.role === 'user'" :icon="UserFilled" />
              <el-avatar v-else :icon="ChatDotRound" class="ai-avatar" />
            </div>
            <div class="message-content">
              <div class="message-text" v-html="formatMessage(msg.content)"></div>
              <!-- 回答来源标签 -->
              <div v-if="msg.role === 'assistant' && msg.meta" class="message-meta">
                <el-tag v-if="msg.meta.source === 'knowledge_base'" type="success" size="small" effect="plain">
                  知识库回答
                </el-tag>
                <el-tag v-else-if="msg.meta.source === 'web_search'" type="warning" size="small" effect="plain">
                  网络搜索回答
                </el-tag>
                <el-tag v-else-if="msg.meta.source === 'llm_direct'" type="info" size="small" effect="plain">
                  AI 通用知识 · 仅供参考
                </el-tag>
              </div>
              <!-- 引用来源卡片 -->
              <div v-if="msg.role === 'assistant' && msg.meta?.references?.length" class="references-section">
                <div class="refs-header" @click="msg.showRefs = !msg.showRefs">
                  <el-icon><Document /></el-icon>
                  <span>查看 {{ msg.meta.references.length }} 个来源</span>
                  <el-icon class="refs-arrow" :class="{ expanded: msg.showRefs }"><ArrowDown /></el-icon>
                </div>
                <transition name="refs-fade">
                  <div v-if="msg.showRefs" class="refs-list">
                    <div v-for="(ref, idx) in msg.meta.references" :key="idx" class="ref-card">
                      <div class="ref-source">来源：{{ ref.source }}</div>
                      <div class="ref-content">{{ ref.content }}</div>
                    </div>
                  </div>
                </transition>
              </div>
              <div class="message-time" v-if="msg.timestamp">
                {{ formatTime(msg.timestamp) }}
              </div>
              <!-- assistant 消息的操作按钮：复制 / 重新生成 / 👍 / 👎
                   流式生成中（最后一条且 isStreaming）暂不显示，避免误操作 -->
              <div
                v-if="msg.role === 'assistant'
                  && !(isStreaming && index === messages.length - 1)"
                class="message-actions"
              >
                <el-tooltip content="复制" placement="top">
                  <el-button text size="small" class="msg-action-btn" @click="copyMessage(msg)">
                    <el-icon><DocumentCopy /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="重新生成" placement="top">
                  <el-button
                    text
                    size="small"
                    class="msg-action-btn"
                    :disabled="isStreaming"
                    @click="handleRegenerate(index)"
                  >
                    <el-icon><Refresh /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip :content="msg.feedback === 1 ? '已点赞' : '点赞'" placement="top">
                  <el-button
                    text
                    size="small"
                    class="msg-action-btn"
                    :class="{ active: msg.feedback === 1 }"
                    :disabled="!msg.id"
                    @click="handleFeedback(msg, 1)"
                  >
                    <el-icon><CaretTop /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip :content="msg.feedback === -1 ? '已点踩' : '点踩'" placement="top">
                  <el-button
                    text
                    size="small"
                    class="msg-action-btn"
                    :class="{ active: msg.feedback === -1 }"
                    :disabled="!msg.id"
                    @click="handleFeedback(msg, -1)"
                  >
                    <el-icon><CaretBottom /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
            </div>
            </div>
          </template>

          <!-- 流式输出指示器 -->
          <div v-if="isStreaming" class="message-item assistant streaming">
            <div class="message-avatar">
              <el-avatar :icon="ChatDotRound" class="ai-avatar" />
            </div>
            <div class="message-content">
              <div class="message-text">
                <span class="typing-indicator">
                  <span></span><span></span><span></span>
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-area">
          <div class="mode-selector">
            <el-radio-group v-model="chatMode" size="small">
              <el-radio-button value="KNOWLEDGE">
                <el-icon><Collection /></el-icon>
                知识库模式
              </el-radio-button>
              <el-radio-button value="LLM">
                <el-icon><ChatDotRound /></el-icon>
                LLM 直答
              </el-radio-button>
            </el-radio-group>
            <el-tooltip
              v-if="chatMode === 'LLM'"
              content="LLM直答模式不经过知识库，回答基于AI通用知识，仅供参考"
              placement="top"
            >
              <el-tag type="warning" size="small" class="mode-tip">可能有幻觉</el-tag>
            </el-tooltip>
            <el-tooltip
              :content="thinkingMode ? '深度推理：让模型充分思考再回答（30秒+，但答案更稳）' : '快速模式：跳过思考直接回答（5-15秒）'"
              placement="top"
            >
              <el-switch
                v-model="thinkingMode"
                size="small"
                active-text="思考"
                inactive-text="快速"
                inline-prompt
                class="thinking-switch"
              />
            </el-tooltip>
          </div>
          <el-input
            v-model="question"
            type="textarea"
            :rows="3"
            :placeholder="chatMode === 'KNOWLEDGE' ? '请输入您的问题，将基于知识库回答' : '请输入您的问题，将直接由AI回答'"
            resize="none"
            @keydown.enter.exact.prevent="handleSend"
          />
          <div class="input-actions">
            <!-- 生成中：显示停止按钮（红色），用户随时打断；
                 空闲：显示发送按钮 -->
            <el-button
              v-if="isStreaming"
              type="danger"
              @click="handleStop"
            >
              <el-icon><CircleClose /></el-icon>
              停止生成
            </el-button>
            <el-button
              v-else
              type="primary"
              :disabled="!question.trim()"
              @click="handleSend"
            >
              <el-icon><Promotion /></el-icon>
              发送
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 重命名对话框 -->
    <el-dialog v-model="renameDialogVisible" title="重命名会话" width="400px">
      <el-input v-model="renameTitle" placeholder="请输入新标题" maxlength="50" show-word-limit
        @keydown.enter.prevent="handleRename" />
      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!renameTitle.trim()" @click="handleRename">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getSessions, getHistory, deleteSession as deleteSessionApi, renameSession as renameSessionApi, submitFeedback } from '@/api/rag'
import type { Session, ChatMode } from '@/types'
import { requestStream } from '@/utils/request'

interface StreamChatMessage {
  /** 后端 chat_conversation.id；流式生成中尚未持久化的消息无 id */
  id?: number
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp?: string
  meta?: {
    source?: string
    hitCount?: number
    references?: { source: string; content: string }[]
    disclaimer?: string
  }
  showRefs?: boolean
  /** 用户对该 assistant 消息的本地反馈状态：1=赞 / -1=踩；用于按钮高亮 */
  feedback?: 1 | -1
}
import {
  Plus,
  ChatLineSquare,
  Delete,
  Edit,
  Expand,
  Fold,
  UserFilled,
  ChatDotRound,
  Promotion,
  DocumentCopy,
  Collection,
  Document,
  ArrowDown,
  CircleClose,
  Refresh,
  CaretTop,
  CaretBottom
} from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'

// Markdown 渲染器：开启 linkify（自动识别裸链接）+ breaks（单换行也分行，更接近聊天直觉）
const md = new MarkdownIt({
  html: false,        // 禁止内嵌 HTML，防 XSS（模型输出不可信）
  linkify: true,
  breaks: true,
  typographer: false
})

// 移动端默认折叠历史面板
const isHistoryCollapsed = ref(window.innerWidth <= 768)
const sessions = ref<Session[]>([])
const currentSessionId = ref<string>('')
const messages = ref<StreamChatMessage[]>([])
const question = ref('')
const isStreaming = ref(false)
const messageListRef = ref<HTMLElement>()
const currentAnswer = ref('')
const lastAnswer = ref('')
// 当前流式句柄：用户点击"停止"时通过 .cancel() 触发 AbortController.abort()，
// 后端 Reactor 链同时收到 CANCEL 信号停止 LLM 调用，避免继续烧 token。
const currentStream = ref<{ cancel: () => void } | null>(null)
const chatMode = ref<ChatMode>('KNOWLEDGE')

// 切换模式时插入一条灰色 system 提示，明确告知用户：知识库历史与 LLM 直答历史互不影响。
// 仅当当前会话已有消息时才提示（空对话切来切去没意义，避免噪音）。
watch(chatMode, (next, prev) => {
  if (next === prev || messages.value.length === 0) return
  const tip =
    next === 'LLM'
      ? '⚠️ 已切换到「LLM 直答模式」：本模式独立于知识库历史，回答仅基于 AI 通用知识。'
      : '✅ 已切换回「知识库模式」：将基于知识库 + 工具调用回答。'
  messages.value.push({
    role: 'system',
    content: tip,
    timestamp: new Date().toISOString()
  })
  scrollToBottom()
})
// 思考模式：默认关闭（快速模式）；开启后让模型先深度推理再回答，速度变慢但答案更稳
const thinkingMode = ref(false)
const renameDialogVisible = ref(false)
const renameTitle = ref('')
const renameSessionId = ref('')

// 切换历史面板（移动端点击遮罩关闭）
const toggleHistory = () => {
  isHistoryCollapsed.value = !isHistoryCollapsed.value
}

// 判断是否为移动端
const isMobile = computed(() => window.innerWidth <= 768)

// 加载历史会话
const loadSessions = async (autoSelect = false) => {
  try {
    sessions.value = await getSessions()
    // 首次加载时自动选中最新会话
    if (autoSelect && sessions.value.length > 0 && !currentSessionId.value) {
      await selectSession(sessions.value[0].sessionId)
    }
  } catch (error) {
    console.error('加载会话列表失败:', error)
  }
}

// 选择会话
const selectSession = async (sessionId: string) => {
  currentSessionId.value = sessionId
  try {
    const history = await getHistory(sessionId)
    messages.value = normalizeHistory(history)
    scrollToBottom()
  } catch (error) {
    console.error('加载会话历史失败:', error)
  }
}

// 创建新会话
const createNewSession = () => {
  currentSessionId.value = ''
  messages.value = []
}

// 删除会话
const deleteSession = async (sessionId: string) => {
  try {
    await deleteSessionApi(sessionId)
    sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
    if (currentSessionId.value === sessionId) {
      createNewSession()
    }
    ElMessage.success('会话已删除')
  } catch (error) {
    console.error('删除会话失败:', error)
  }
}

// 打开重命名对话框
const openRenameDialog = (session: Session) => {
  renameSessionId.value = session.sessionId
  renameTitle.value = session.title || ''
  renameDialogVisible.value = true
}

// 执行重命名
const handleRename = async () => {
  if (!renameTitle.value.trim()) return
  try {
    await renameSessionApi(renameSessionId.value, renameTitle.value.trim())
    const session = sessions.value.find(s => s.sessionId === renameSessionId.value)
    if (session) {
      session.title = renameTitle.value.trim()
    }
    renameDialogVisible.value = false
    ElMessage.success('重命名成功')
  } catch (error) {
    console.error('重命名失败:', error)
  }
}

// 清空聊天
const clearChat = () => {
  messages.value = []
  currentSessionId.value = ''
  lastAnswer.value = ''
}

// 发送消息
const handleSend = async () => {
  if (!question.value.trim() || isStreaming.value) return

  const q = question.value.trim()
  question.value = ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: q,
    timestamp: new Date().toISOString()
  })
  scrollToBottom()

  await runStream(q)
}

// 真正发起流式请求（用户消息已在 messages 数组末尾或不需要重复入栈，由调用方控制）。
// 拆出来给 handleSend / handleRegenerate 复用。
const runStream = async (q: string) => {
  currentAnswer.value = ''
  isStreaming.value = true
  let fullAnswer = ''
  let streamMeta: StreamChatMessage['meta'] = undefined

  currentStream.value = requestStream(
    '/api/rag/chat/stream',
    {
      question: q,
      sessionId: currentSessionId.value || undefined,
      chatMode: chatMode.value,
      thinking: thinkingMode.value
    },
    (text) => {
      // 解析 [META]...[/META] 元数据（引用来源、回答类型）
      const metaMatch = text.match(/\[META\](.*?)\[\/META\]/s)
      if (metaMatch) {
        try {
          streamMeta = JSON.parse(metaMatch[1])
        } catch (e) {
          console.warn('解析元数据失败:', e)
        }
        // 去掉元数据标记，只保留正文
        text = text.replace(/\[META\].*?\[\/META\]/s, '')
        if (!text.trim()) return
      }

      fullAnswer += text
      currentAnswer.value = fullAnswer
      
      // 更新或添加AI回复
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg?.role === 'user') {
        messages.value.push({
          role: 'assistant',
          content: fullAnswer,
          timestamp: new Date().toISOString(),
          meta: streamMeta,
          showRefs: false
        })
      } else {
        lastMsg.content = fullAnswer
        if (streamMeta && !lastMsg.meta) {
          lastMsg.meta = streamMeta
        }
      }
      scrollToBottom()
    },
    (error) => {
      isStreaming.value = false
      currentStream.value = null
      ElMessage.error('请求失败: ' + error.message)
    },
    async () => {
      isStreaming.value = false
      currentStream.value = null
      lastAnswer.value = fullAnswer
      // 刷新会话列表；新对话首次发消息后自动绑定 sessionId
      const wasNew = !currentSessionId.value
      await loadSessions()
      if (wasNew && sessions.value.length > 0) {
        currentSessionId.value = sessions.value[0].sessionId
      }
      // 流式结束后从后端拉一次最新历史：拿到 cleanAnswer 处理过的最终版本，
      // 用整段 Markdown 重渲染替代流式的增量片段，确保排版/列表/标题等完整呈现
      if (currentSessionId.value) {
        try {
          const history = await getHistory(currentSessionId.value)
          messages.value = normalizeHistory(history)
          scrollToBottom()
        } catch (e) {
          console.warn('刷新历史失败:', e)
        }
      }
    }
  )
}

// 后端 chat_conversation 返回的是字符串 metadata；前端模板用 msg.meta 对象。
// 这里统一把每条消息的 metadata JSON 解析到 meta，并保留 id 用于反馈接口。
const normalizeHistory = (history: any[]): StreamChatMessage[] => {
  return (history || []).map((m) => {
    const out: StreamChatMessage = {
      id: m.id,
      role: m.role,
      content: m.content,
      timestamp: m.timestamp ?? m.createdAt
    }
    if (m.metadata) {
      try {
        out.meta = typeof m.metadata === 'string' ? JSON.parse(m.metadata) : m.metadata
      } catch {
        // metadata 解析失败不影响消息展示
      }
    } else if (m.meta) {
      out.meta = m.meta
    }
    return out
  })
}

// 停止生成：取消前端 fetch + 触发后端 Reactor CANCEL，
// 同时给当前 assistant 消息打上"已中断"标记，让用户清楚区分。
const handleStop = () => {
  if (!currentStream.value) return
  currentStream.value.cancel()
  currentStream.value = null
  isStreaming.value = false
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg?.role === 'assistant') {
    lastMsg.content = (lastMsg.content || '') + '\n\n_[已中断]_'
  }
  ElMessage.info('已停止生成')
}

// 复制单条 assistant 消息内容到剪贴板（兼容非 https 场景的退化方案）
const copyMessage = async (msg: StreamChatMessage) => {
  const text = msg.content || ''
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    console.error('复制失败:', e)
    ElMessage.error('复制失败')
  }
}

// 重新生成：定位到该 assistant 消息上一条 user 消息，删除从该 assistant 起的所有后续消息，
// 然后用同一问题重新发起流式请求；不会重复 push user 消息，避免历史重复。
const handleRegenerate = async (index: number) => {
  if (isStreaming.value) {
    ElMessage.warning('正在生成中，请先停止')
    return
  }
  // 向上找最近的 user 消息
  let userIdx = index - 1
  while (userIdx >= 0 && messages.value[userIdx].role !== 'user') userIdx--
  if (userIdx < 0) {
    ElMessage.warning('未找到对应的提问')
    return
  }
  const q = messages.value[userIdx].content
  // 截断到 user 消息结尾（移除原 assistant 回答与之后所有内容）
  messages.value = messages.value.slice(0, userIdx + 1)
  scrollToBottom()
  await runStream(q)
}

// 提交反馈：rating 1=赞 / -1=踩；同一条消息再次点同样按钮 = 取消（本地侧），
// 但后端只做"覆盖式"upsert，不支持删除；这里点同值则不再请求，避免无效写。
const handleFeedback = async (msg: StreamChatMessage, rating: 1 | -1) => {
  if (!msg.id) {
    ElMessage.warning('消息尚未持久化，请稍后再试')
    return
  }
  if (!currentSessionId.value) return
  if (msg.feedback === rating) return
  try {
    await submitFeedback({
      messageId: msg.id,
      rating,
      sessionId: currentSessionId.value
    })
    msg.feedback = rating
    ElMessage.success(rating === 1 ? '感谢您的认可' : '已记录，会持续改进')
  } catch (e) {
    console.error('提交反馈失败:', e)
  }
}

// HTML转义（防止XSS）
const escapeHtml = (text: string) => {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }
  return text.replace(/[&<>"']/g, (m) => map[m])
}

// 格式化消息：用 markdown-it 渲染完整 Markdown（列表、标题、表格、链接、代码块等全支持）。
// html: false 已防 XSS；用户消息也走渲染器以保持一致体验（用户输入若含 < 会被自动转义）。
const formatMessage = (content: string) => md.render(content || '')

// 格式化会话ID
const formatSessionId = (id: string) => {
  if (id.length > 12) {
    return id.substring(0, 12) + '...'
  }
  return id
}

// 格式化时间
const formatTime = (time: string) => {
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  loadSessions(true)
})
</script>

<style scoped>
.rag-chat-container {
  height: calc(100vh - 100px);
  padding: 0;
}

.chat-wrapper {
  display: flex;
  height: 100%;
  gap: 0;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.history-panel {
  width: 260px;
  background: #fafafa;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
  transition: width 0.3s, overflow 0.3s;
  overflow: hidden;
}

.history-panel.is-collapsed {
  width: 0;
  border-right: none;
}

.history-header {
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #eee;
}

.history-header .header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-header .close-btn {
  padding: 4px;
}

.history-header h3 {
  margin: 0;
  font-size: 16px;
  color: #333;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.history-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.2s;
}

.history-item:hover {
  background: #e8e8e8;
}

.history-item.active {
  background: #409eff;
  color: #fff;
}

.history-item.current-chat {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-weight: 500;
}

.history-item.current-chat .el-icon {
  color: #fff;
}

.history-item .session-text {
  flex: 1;
  margin-left: 8px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-btn {
  opacity: 0;
  transition: opacity 0.2s;
  flex-shrink: 0;
}

.history-item:hover .action-btn {
  opacity: 1;
}

.delete-btn:hover {
  color: #f56c6c;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  padding: 12px 20px;
  border-bottom: 1px solid #eee;
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-header h2 {
  flex: 1;
  margin: 0;
  font-size: 18px;
  color: #333;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.welcome-message {
  text-align: center;
  padding: 60px 20px;
  color: #999;
}

.welcome-icon {
  color: #409eff;
  margin-bottom: 16px;
}

.welcome-message h3 {
  margin: 0 0 8px;
  color: #333;
}

.welcome-message p {
  margin: 0;
  font-size: 14px;
}

.message-item {
  display: flex;
  margin-bottom: 20px;
}

/* 系统提示（如模式切换通知）：灰色居中，与用户/AI 气泡明显区分 */
.message-system {
  margin: 12px auto;
  padding: 6px 14px;
  max-width: 80%;
  text-align: center;
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 12px;
  line-height: 1.5;
}

.message-item.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.ai-avatar {
  background: #409eff;
}

.message-content {
  max-width: 80%;
  margin: 0 12px;
}

.message-item.user .message-content {
  align-items: flex-end;
}

.message-text {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 14px;
  word-break: break-word;
}

.message-item.user .message-text {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message-item.assistant .message-text {
  background: #f4f4f5;
  color: #333;
  border-bottom-left-radius: 4px;
}

/* Markdown 渲染样式：覆盖 markdown-it 默认输出，让聊天气泡内排版紧凑舒适 */
.message-text :deep(p) {
  margin: 4px 0;
}

.message-text :deep(p:first-child) {
  margin-top: 0;
}

.message-text :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text :deep(ul),
.message-text :deep(ol) {
  margin: 6px 0;
  padding-left: 24px;
}

.message-text :deep(li) {
  margin: 2px 0;
}

.message-text :deep(h1),
.message-text :deep(h2),
.message-text :deep(h3),
.message-text :deep(h4) {
  margin: 12px 0 6px;
  font-weight: 600;
  line-height: 1.3;
}

.message-text :deep(h1) { font-size: 1.3em; }
.message-text :deep(h2) { font-size: 1.2em; }
.message-text :deep(h3) { font-size: 1.1em; }
.message-text :deep(h4) { font-size: 1em; }

.message-text :deep(pre) {
  background: #f6f8fa;
  border-radius: 6px;
  padding: 12px;
  margin: 8px 0;
  overflow-x: auto;
  font-size: 0.9em;
}

.message-text :deep(pre code) {
  background: transparent;
  padding: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

.message-text :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 0.9em;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

.message-text :deep(blockquote) {
  margin: 8px 0;
  padding: 4px 12px;
  border-left: 3px solid #dcdfe6;
  color: #666;
  background: rgba(0, 0, 0, 0.02);
}

.message-text :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 0.95em;
}

.message-text :deep(th),
.message-text :deep(td) {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
  text-align: left;
}

.message-text :deep(th) {
  background: #fafafa;
  font-weight: 600;
}

.message-text :deep(a) {
  color: #409eff;
  text-decoration: none;
}

.message-text :deep(a:hover) {
  text-decoration: underline;
}

.message-text :deep(hr) {
  border: none;
  border-top: 1px solid #dcdfe6;
  margin: 12px 0;
}

/* 用户消息气泡是深蓝底，code/链接需要反色才看得清 */
.message-item.user .message-text :deep(code) {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.message-item.user .message-text :deep(a) {
  color: #c6e2ff;
}

.message-meta {
  margin-top: 6px;
  display: flex;
  gap: 6px;
}

.references-section {
  margin-top: 8px;
  border-radius: 8px;
  overflow: hidden;
}

.refs-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: #f0f2f5;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  user-select: none;
  transition: background 0.2s;
}

.refs-header:hover {
  background: #e4e7ed;
}

.refs-arrow {
  margin-left: auto;
  transition: transform 0.2s;
}

.refs-arrow.expanded {
  transform: rotate(180deg);
}

.refs-list {
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ref-card {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 13px;
}

.ref-source {
  color: #409eff;
  font-weight: 500;
  margin-bottom: 4px;
  font-size: 12px;
}

.ref-content {
  color: #606266;
  line-height: 1.5;
  word-break: break-word;
}

.refs-fade-enter-active, .refs-fade-leave-active {
  transition: opacity 0.2s, max-height 0.3s;
}

.refs-fade-enter-from, .refs-fade-leave-to {
  opacity: 0;
}

.message-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

/* assistant 消息下方的操作按钮行：复制 / 重新生成 / 👍 / 👎 */
.message-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 4px;
  opacity: 0.55;
  transition: opacity 0.2s;
}

.message-item.assistant:hover .message-actions {
  opacity: 1;
}

.msg-action-btn {
  padding: 4px 6px;
  height: auto;
  min-height: 0;
  color: #909399;
}

.msg-action-btn:hover {
  color: #409eff;
  background: rgba(64, 158, 255, 0.08);
}

.msg-action-btn.active {
  color: #409eff;
}

.msg-action-btn.is-disabled {
  color: #c0c4cc;
}

.typing-indicator {
  display: inline-flex;
  gap: 4px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 100% { opacity: 0.4; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1); }
}

.input-area {
  padding: 16px 20px;
  border-top: 1px solid #eee;
  background: #fafafa;
}

.mode-selector {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.mode-tip {
  margin-left: 4px;
}

/* 思考模式开关：靠右显示，与模式按钮拉开间距 */
.thinking-switch {
  margin-left: auto;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}

  /* 移动端适配 */
@media screen and (max-width: 768px) {
  .rag-chat-container {
    height: calc(100vh - 60px);
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  .chat-wrapper {
    border-radius: 0;
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  /* 移动端：聊天面板独占宽度 */
  .chat-panel {
    width: 100%;
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  /* 历史面板：和菜单一样，固定定位从左侧滑入 */
  .history-panel {
    position: fixed;
    top: 0;
    left: 0;
    width: 80vw !important;
    max-width: 300px;
    height: 100vh;
    z-index: 1001;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    background: #fafafa;
    border-right: 1px solid #eee;
  }

  /* 展开状态：滑入显示 */
  .history-panel:not(.is-collapsed) {
    transform: translateX(0);
  }

  /* 折叠状态：完全隐藏 */
  .history-panel.is-collapsed {
    transform: translateX(-100%) !important;
    width: 0 !important;
    overflow: hidden;
  }

  /* 移动端遮罩层：z-index 要低于历史面板 */
  .mobile-overlay {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 1000;
  }

  .history-header {
    padding: 12px 10px;
  }

  .history-header h3 {
    font-size: 15px;
  }

  .chat-header {
    padding: 12px 10px;
    gap: 8px;
  }

  .chat-header h2 {
    font-size: 16px;
  }

  .message-list {
    padding: 10px;
    flex: 1;
    overflow-y: auto;
    height: 0; /* 让flex生效 */
  }

  .message-content {
    max-width: 85%;
    margin: 0 8px;
  }

  .message-text {
    padding: 10px 12px;
    font-size: 14px;
  }

  .input-area {
    padding: 10px;
  }

  .mode-selector {
    flex-wrap: wrap;
    gap: 5px;
  }

  .input-actions {
    flex-direction: row;
    gap: 10px;
  }

  .input-actions .el-button {
    flex: 1;
  }
}

@media screen and (max-width: 480px) {
  .message-content {
    max-width: 90%;
  }

  .welcome-message {
    padding: 30px 15px;
  }

  .welcome-message h3 {
    font-size: 18px;
  }
}
</style>

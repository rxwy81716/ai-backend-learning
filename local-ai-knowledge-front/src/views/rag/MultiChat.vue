<template>
  <div class="multi-chat-container">
    <div class="chat-wrapper">
      <!-- 左侧会话列表 -->
      <div class="session-panel" :class="{ 'is-collapsed': isSessionCollapsed }">
        <div class="panel-header">
          <h3>对话列表</h3>
          <el-button text @click="handleNewChat">
            <el-icon><Plus /></el-icon>
            新建
          </el-button>
        </div>
        <div class="session-list">
          <div
            v-for="sessionId in sessions"
            :key="sessionId"
            class="session-item"
            :class="{ active: currentSessionId === sessionId }"
            @click="loadSession(sessionId)"
          >
            <el-icon><ChatLineRound /></el-icon>
            <span class="session-title">{{ getSessionTitle(sessionId) }}</span>
            <div class="session-actions">
              <el-icon @click.stop="deleteSession(sessionId)"><Delete /></el-icon>
            </div>
          </div>
          <el-empty v-if="sessions.length === 0" description="暂无对话" :image-size="50" />
        </div>
      </div>

      <!-- 聊天区域 -->
      <div class="chat-area">
        <div class="chat-header">
          <el-button text @click="isSessionCollapsed = !isSessionCollapsed">
            <el-icon><Expand v-if="isSessionCollapsed" /><Fold v-else /></el-icon>
          </el-button>
          <h2>多轮对话</h2>
          <span class="session-indicator" v-if="currentSessionId">
            会话ID: {{ currentSessionId.substring(0, 8) }}...
          </span>
        </div>

        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesRef">
          <div class="welcome-card">
            <el-icon :size="40"><ChatSquare /></el-icon>
            <h3>多轮对话模式</h3>
            <p>系统会记住当前对话的上下文，支持连续追问</p>
          </div>

          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-wrapper"
            :class="msg.role"
          >
            <div class="message-avatar">
              <el-avatar v-if="msg.role === 'user'" size="small">
                {{ userStore.username.charAt(0).toUpperCase() }}
              </el-avatar>
              <el-avatar v-else size="small" type="primary">
                <el-icon><MagicStick /></el-icon>
              </el-avatar>
            </div>
            <div class="message-body">
              <div class="message-bubble" v-html="renderMarkdown(msg.content)"></div>
              <div class="message-meta">
                <span class="time">{{ formatTime(msg.timestamp) }}</span>
              </div>
            </div>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="message-wrapper assistant">
            <div class="message-avatar">
              <el-avatar size="small" type="primary">
                <el-icon><MagicStick /></el-icon>
              </el-avatar>
            </div>
            <div class="message-body">
              <div class="message-bubble loading">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-wrapper">
          <div class="input-container">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              placeholder="输入问题，使用 Shift+Enter 换行"
              @keydown.enter.exact.prevent="handleSend"
            />
            <div class="input-footer">
              <el-button
                type="primary"
                :loading="loading"
                :disabled="!inputText.trim() || loading"
                @click="handleSend"
              >
                <el-icon><Promotion /></el-icon>
                发送
              </el-button>
              <el-button @click="clearMessages" :disabled="messages.length === 0">
                <el-icon><RefreshLeft /></el-icon>
                清空
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getSessions, getHistory, deleteSession, multiChat } from '@/api/rag'
import { requestStream } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import type { ChatMessage } from '@/types'
import {
  Plus,
  ChatLineRound,
  Delete,
  Expand,
  Fold,
  ChatSquare,
  MagicStick,
  Promotion,
  RefreshLeft
} from '@element-plus/icons-vue'

const userStore = useUserStore()

const isSessionCollapsed = ref(false)
const sessions = ref<string[]>([])
const currentSessionId = ref('')
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const messagesRef = ref<HTMLElement>()

// 加载会话列表
const loadSessionList = async () => {
  try {
    sessions.value = await getSessions()
  } catch (error) {
    console.error('加载会话列表失败:', error)
  }
}

// 获取会话标题（使用第一条消息的前20个字符）
const getSessionTitle = (sessionId: string) => {
  return `对话 ${sessionId.substring(0, 8)}`
}

// 加载指定会话
const loadSession = async (sessionId: string) => {
  currentSessionId.value = sessionId
  try {
    const history = await getHistory(sessionId)
    messages.value = history
    scrollToBottom()
  } catch (error) {
    ElMessage.error('加载会话失败')
  }
}

// 新建对话
const handleNewChat = () => {
  currentSessionId.value = ''
  messages.value = []
}

// 删除会话
const deleteSession = async (sessionId: string) => {
  try {
    await deleteSession(sessionId)
    sessions.value = sessions.value.filter(s => s !== sessionId)
    if (currentSessionId.value === sessionId) {
      handleNewChat()
    }
    ElMessage.success('已删除')
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// 清空消息
const clearMessages = () => {
  messages.value = []
  currentSessionId.value = ''
}

// 发送消息
const handleSend = async () => {
  if (!inputText.value.trim() || loading.value) return

  const question = inputText.value.trim()
  inputText.value = ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: question,
    timestamp: new Date().toISOString()
  })
  scrollToBottom()

  loading.value = true
  let fullAnswer = ''

  requestStream(
    '/api/rag/multi-chat/stream',
    {
      question,
      sessionId: currentSessionId.value || undefined
    },
    (text) => {
      fullAnswer += text
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg?.role === 'user') {
        messages.value.push({
          role: 'assistant',
          content: fullAnswer,
          timestamp: new Date().toISOString()
        })
      } else {
        lastMsg.content = fullAnswer
      }
      scrollToBottom()
    },
    (error) => {
      ElMessage.error('请求失败: ' + error.message)
      loading.value = false
    },
    () => {
      loading.value = false
      loadSessionList()
      // 更新当前会话ID（如果是新建的）
      if (!currentSessionId.value && messages.value.length > 0) {
        const lastMsg = messages.value[messages.value.length - 1]
        // 从响应中获取sessionId（如果有）
      }
    }
  )
}

// 渲染Markdown（简化版）
const renderMarkdown = (content: string) => {
  return content
    .replace(/\n/g, '<br>')
    .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
}

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return ''
  return new Date(time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  loadSessionList()
})
</script>

<style scoped>
.multi-chat-container {
  height: calc(100vh - 100px);
}

.chat-wrapper {
  display: flex;
  height: 100%;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.session-panel {
  width: 240px;
  background: #fafafa;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
}

.session-panel.is-collapsed {
  width: 0;
  border-right: none;
}

.panel-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #eee;
}

.panel-header h3 {
  margin: 0;
  font-size: 15px;
  color: #333;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: all 0.2s;
}

.session-item:hover {
  background: #e8e8e8;
}

.session-item.active {
  background: #409eff;
  color: #fff;
}

.session-title {
  flex: 1;
  margin-left: 8px;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-actions {
  opacity: 0;
  transition: opacity 0.2s;
}

.session-item:hover .session-actions {
  opacity: 1;
}

.chat-area {
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

.session-indicator {
  font-size: 12px;
  color: #999;
  background: #f5f7fa;
  padding: 4px 8px;
  border-radius: 4px;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.welcome-card {
  text-align: center;
  padding: 60px 20px;
  color: #909399;
}

.welcome-card h3 {
  margin: 16px 0 8px;
  color: #303133;
}

.welcome-card p {
  margin: 0;
  font-size: 14px;
}

.message-wrapper {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.message-wrapper.user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
  margin: 0 10px;
}

.message-body {
  max-width: 75%;
}

.message-bubble {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  font-size: 14px;
}

.message-wrapper.user .message-bubble {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message-wrapper.assistant .message-bubble {
  background: #f4f4f5;
  color: #333;
  border-bottom-left-radius: 4px;
}

.message-bubble pre {
  background: #282c34;
  color: #abb2bf;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}

.message-bubble code {
  background: rgba(0, 0, 0, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
}

.message-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}

.message-meta .time {
  margin-left: 4px;
}

.message-bubble.loading {
  display: flex;
  gap: 4px;
  padding: 16px;
}

.message-bubble.loading .dot {
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.message-bubble.loading .dot:nth-child(1) { animation-delay: -0.32s; }
.message-bubble.loading .dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.input-wrapper {
  padding: 16px 20px;
  background: #fafafa;
  border-top: 1px solid #eee;
}

.input-container {
  max-width: 800px;
  margin: 0 auto;
}

.input-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}
</style>

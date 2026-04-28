<template>
  <div class="rag-chat-container">
    <div class="chat-wrapper">
      <!-- 左侧历史会话 -->
      <div class="history-panel" :class="{ 'is-collapsed': isHistoryCollapsed }">
        <div class="history-header">
          <h3>历史会话</h3>
          <el-button text @click="createNewSession">
            <el-icon><Plus /></el-icon>
            新对话
          </el-button>
        </div>
        <div class="history-list">
          <!-- 当前对话（默认） -->
          <div
            class="history-item current-chat"
            :class="{ active: !currentSessionId }"
            @click="createNewSession"
          >
            <el-icon><ChatLineSquare /></el-icon>
            <span class="session-text">当前对话</span>
            <el-tag v-if="!currentSessionId" size="small" type="primary">进行中</el-tag>
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
              class="delete-btn"
              @click.stop="deleteSession(session.sessionId)"
            ><Delete /></el-icon>
          </div>
          <el-empty v-if="sessions.length === 0" description="暂无历史会话" :image-size="60" />
        </div>
      </div>

      <!-- 右侧聊天区域 -->
      <div class="chat-panel">
        <!-- 聊天头部 -->
        <div class="chat-header">
          <el-button text @click="isHistoryCollapsed = !isHistoryCollapsed">
            <el-icon><Expand v-if="isHistoryCollapsed" /><Fold v-else /></el-icon>
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

          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="message-item"
            :class="msg.role"
          >
            <div class="message-avatar">
              <el-avatar v-if="msg.role === 'user'" :icon="UserFilled" />
              <el-avatar v-else :icon="ChatDotRound" class="ai-avatar" />
            </div>
            <div class="message-content">
              <div class="message-text" v-html="formatMessage(msg.content)"></div>
              <div class="message-time" v-if="msg.timestamp">
                {{ formatTime(msg.timestamp) }}
              </div>
            </div>
          </div>

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
            <el-button
              type="primary"
              :loading="isStreaming"
              :disabled="!question.trim() || isStreaming"
              @click="handleSend"
            >
              <el-icon v-if="!isStreaming"><Promotion /></el-icon>
              {{ isStreaming ? '生成中...' : '发送' }}
            </el-button>
            <el-button @click="copyAnswer" :disabled="!lastAnswer">
              <el-icon><DocumentCopy /></el-icon>
              复制答案
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getSessions, getHistory, deleteSession as deleteSessionApi } from '@/api/rag'
import type { Session, ChatMode } from '@/types'
import { requestStream } from '@/utils/request'
import type { ChatMessage } from '@/types'
import {
  Plus,
  ChatLineSquare,
  Delete,
  Expand,
  Fold,
  UserFilled,
  ChatDotRound,
  Promotion,
  DocumentCopy,
  Collection
} from '@element-plus/icons-vue'

const isHistoryCollapsed = ref(false)
const sessions = ref<Session[]>([])
const currentSessionId = ref<string>('')
const messages = ref<ChatMessage[]>([])
const question = ref('')
const isStreaming = ref(false)
const messageListRef = ref<HTMLElement>()
const currentAnswer = ref('')
const lastAnswer = ref('')
const chatMode = ref<ChatMode>('KNOWLEDGE')

// 加载历史会话
const loadSessions = async () => {
  try {
    sessions.value = await getSessions()
  } catch (error) {
    console.error('加载会话列表失败:', error)
  }
}

// 选择会话
const selectSession = async (sessionId: string) => {
  currentSessionId.value = sessionId
  try {
    const history = await getHistory(sessionId)
    messages.value = history
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
  currentAnswer.value = ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: q,
    timestamp: new Date().toISOString()
  })
  scrollToBottom()

  // 流式请求
  isStreaming.value = true
  let fullAnswer = ''

  requestStream(
    '/api/rag/chat/stream',
    {
      question: q,
      sessionId: currentSessionId.value || undefined,
      chatMode: chatMode.value
    },
    (text) => {
      fullAnswer += text
      currentAnswer.value = fullAnswer
      
      // 更新或添加AI回复
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
    },
    () => {
      isStreaming.value = false
      lastAnswer.value = fullAnswer
      loadSessions() // 刷新会话列表
    }
  )
}

// 复制答案
const copyAnswer = () => {
  if (!lastAnswer.value) return
  navigator.clipboard.writeText(lastAnswer.value)
  ElMessage.success('已复制到剪贴板')
}

// 格式化消息（支持简单的markdown）
const formatMessage = (content: string) => {
  return content
    .replace(/\n/g, '<br>')
    .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
}

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
  loadSessions()
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
  transition: width 0.3s;
}

.history-panel.is-collapsed {
  width: 0;
  border-right: none;
  overflow: hidden;
}

.history-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #eee;
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

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.history-item:hover .delete-btn {
  opacity: 1;
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

.message-time {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
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

.input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}
</style>

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
              class="action-btn"
              @click.stop="openRenameDialog(session)"
            ><Edit /></el-icon>
            <el-icon
              class="action-btn delete-btn"
              @click.stop="deleteSession(session.sessionId)"
            ><Delete /></el-icon>
          </div>
          <el-empty v-if="sessions.length === 0" description="暂无历史会话" :image-size="60" />
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
              <!-- 回答来源标签 -->
              <div v-if="msg.role === 'assistant' && msg.meta" class="message-meta">
                <el-tag v-if="msg.meta.source === 'knowledge_base'" type="success" size="small" effect="plain">
                  知识库回答 · 命中 {{ msg.meta.hitCount }} 条
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
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getSessions, getHistory, deleteSession as deleteSessionApi, renameSession as renameSessionApi } from '@/api/rag'
import type { Session, ChatMode } from '@/types'
import { requestStream } from '@/utils/request'

interface StreamChatMessage {
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
  ArrowDown
} from '@element-plus/icons-vue'

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
const chatMode = ref<ChatMode>('KNOWLEDGE')
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
  let streamMeta: StreamChatMessage['meta'] = undefined

  requestStream(
    '/api/rag/chat/stream',
    {
      question: q,
      sessionId: currentSessionId.value || undefined,
      chatMode: chatMode.value
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

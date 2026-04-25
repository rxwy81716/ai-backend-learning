<script setup lang="ts">
import { nextTick, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Position, Delete, Loading } from '@element-plus/icons-vue'
import { ragStreamUrl } from '@/api/rag'
import { streamSSE } from '@/utils/sse'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'

interface Message {
  id: number
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
}

const messages = ref<Message[]>([])
const input = ref('')
const loading = ref(false)
const topK = ref(5)
const threshold = ref(0.7)
const messageListRef = ref<HTMLElement | null>(null)

let abortController: AbortController | null = null
let msgId = 0

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  // 添加用户消息
  messages.value.push({ id: ++msgId, role: 'user', content: text })
  // 添加 AI 消息占位（loading 状态）
  const aiMsg: Message = { id: ++msgId, role: 'assistant', content: '', loading: true }
  messages.value.push(aiMsg)

  input.value = ''
  loading.value = true
  await scrollToBottom()

  // 流式接收
  abortController = new AbortController()
  await streamSSE({
    url: ragStreamUrl(text, topK.value, threshold.value),
    method: 'GET',
    signal: abortController.signal,
    onMessage: (chunk) => {
      aiMsg.loading = false
      aiMsg.content += chunk
      scrollToBottom()
    },
    onDone: () => {
      aiMsg.loading = false
      loading.value = false
    },
    onError: (err) => {
      aiMsg.loading = false
      aiMsg.content = aiMsg.content || `❌ 请求失败：${err.message}`
      loading.value = false
    }
  })
}

function stop() {
  abortController?.abort()
  loading.value = false
  ElMessage.info('已停止生成')
}

function clear() {
  if (loading.value) {
    ElMessage.warning('请先停止当前生成')
    return
  }
  messages.value = []
}

async function scrollToBottom() {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

function handleKeydown(e: KeyboardEvent) {
  // Enter 发送，Shift+Enter 换行
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

onUnmounted(() => {
  abortController?.abort()
})
</script>

<template>
  <div class="chat-container">
    <!-- 顶部参数条 -->
    <div class="param-bar">
      <span class="param-label">TopK：</span>
      <el-input-number v-model="topK" :min="1" :max="20" size="small" />
      <span class="param-label" style="margin-left: 16px">相似度阈值：</span>
      <el-input-number v-model="threshold" :min="0" :max="1" :step="0.05" :precision="2" size="small" />
      <el-button
        type="danger"
        plain
        size="small"
        :icon="Delete"
        @click="clear"
        style="margin-left: auto"
      >
        清空对话
      </el-button>
    </div>

    <!-- 消息列表 -->
    <div ref="messageListRef" class="message-list">
      <div v-if="messages.length === 0" class="empty">
        <div class="empty-icon">💬</div>
        <div class="empty-title">开始你的第一个问题吧</div>
        <div class="empty-tip">基于知识库的智能问答，答案带来源标注</div>
      </div>

      <div
        v-for="msg in messages"
        :key="msg.id"
        class="message-row"
        :class="msg.role"
      >
        <div class="avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
        <div class="bubble">
          <div v-if="msg.loading && !msg.content" class="loading-dots">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>思考中...</span>
          </div>
          <MarkdownRenderer v-else :content="msg.content" />
        </div>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="input-area">
      <el-input
        v-model="input"
        type="textarea"
        :rows="3"
        placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
        resize="none"
        @keydown="handleKeydown"
      />
      <div class="input-actions">
        <el-button v-if="loading" type="warning" @click="stop">停止</el-button>
        <el-button
          type="primary"
          :icon="Position"
          :disabled="!input.trim() || loading"
          :loading="loading"
          @click="send"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-page);
}

.param-bar {
  background: #fff;
  padding: 12px 24px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  font-size: 13px;

  .param-label { color: #606266; }
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.empty {
  text-align: center;
  margin-top: 100px;
  color: #909399;

  .empty-icon { font-size: 60px; margin-bottom: 12px; }
  .empty-title { font-size: 18px; color: #303133; margin-bottom: 8px; }
  .empty-tip { font-size: 14px; }
}

.message-row {
  display: flex;
  margin-bottom: 24px;
  gap: 12px;

  &.user {
    flex-direction: row-reverse;
    .bubble { background: var(--bg-chat-user); }
  }

  .avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    flex-shrink: 0;
    border: 1px solid #ebeef5;
  }

  .bubble {
    background: var(--bg-chat-ai);
    border-radius: 8px;
    padding: 12px 16px;
    max-width: 75%;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    border: 1px solid #ebeef5;
  }
}

.loading-dots {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 14px;
}

.input-area {
  background: #fff;
  border-top: 1px solid #ebeef5;
  padding: 16px 24px;

  .input-actions {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}
</style>

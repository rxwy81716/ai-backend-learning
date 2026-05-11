<template>
  <div class="finetune-page">
    <div class="page-header">
      <h2>微调 vs RAG 对比</h2>
      <p class="subtitle">了解两种方案的差异，从知识库生成微调训练数据</p>
    </div>

    <!-- 当前方案标识 -->
    <el-alert type="success" :closable="false" style="margin-bottom: 24px;">
      <template #title>
        <strong>本项目采用 RAG（检索增强生成）方案</strong> —— 不改模型本身，每次提问时动态检索知识库
      </template>
    </el-alert>

    <!-- 对比表 -->
    <el-card shadow="never" class="compare-card" v-loading="loadingCompare">
      <template #header>
        <h3>方案对比</h3>
      </template>
      <el-table :data="comparison" stripe>
        <el-table-column prop="dimension" label="维度" width="140" />
        <el-table-column label="RAG（本项目）" min-width="200">
          <template #default="{ row }">
            <span v-html="row.rag"></span>
          </template>
        </el-table-column>
        <el-table-column label="微调 Fine-tuning" min-width="200">
          <template #default="{ row }">
            <span v-html="row.finetune"></span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="recommendation" class="recommendation">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ recommendation }}</span>
      </div>
    </el-card>

    <!-- 训练数据生成 -->
    <el-card shadow="never" class="generate-card">
      <template #header>
        <div class="gen-header">
          <h3>训练数据生成</h3>
          <el-tag type="warning">实验性功能</el-tag>
        </div>
      </template>
      <p class="gen-desc">从知识库已有文档生成微调训练数据（OpenAI JSONL 格式），可用于对比 RAG 和微调的效果差异。</p>

      <!-- 文档选择 -->
      <el-form label-width="100px" style="max-width: 600px;">
        <el-form-item label="选择文档">
          <el-select v-model="selectedTaskId" placeholder="选择一个已解析的文档" filterable
                     @change="onTaskSelect" style="width: 100%;">
            <el-option v-for="task in tasks" :key="task.taskId" :label="task.fileName"
                       :value="task.taskId" :disabled="task.status !== 'DONE'">
              <span>{{ task.fileName }}</span>
              <el-tag size="small" :type="task.status === 'DONE' ? 'success' : 'info'" style="margin-left: 8px;">
                {{ task.status }}
              </el-tag>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 估算结果 -->
      <div v-if="estimate && !estimate.error" class="estimate-panel">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="文件名">{{ estimate.fileName }}</el-descriptions-item>
          <el-descriptions-item label="总分片数">{{ estimate.totalChunks }}</el-descriptions-item>
          <el-descriptions-item label="有效分片">{{ estimate.validChunks }}</el-descriptions-item>
          <el-descriptions-item label="预估 QA 对">
            <strong style="color: #409eff;">{{ estimate.estimatedQAPairs }}</strong>
          </el-descriptions-item>
          <el-descriptions-item label="预估消耗" :span="2">{{ estimate.estimatedCost }}</el-descriptions-item>
        </el-descriptions>

        <div class="gen-actions">
          <el-button type="primary" @click="handleDownload" :loading="generating">
            <el-icon><Download /></el-icon> 生成并下载 JSONL
          </el-button>
          <el-text type="info" size="small">
            耗时较长（每个分片需调用一次 LLM），请耐心等待
          </el-text>
        </div>
      </div>
    </el-card>

    <!-- JSONL 格式说明 -->
    <el-card shadow="never" class="format-card">
      <template #header>
        <h3>训练数据格式说明</h3>
      </template>
      <p>输出为 <strong>OpenAI Fine-tuning JSONL</strong> 格式，每行一个 JSON 对象：</p>
      <pre class="format-example">{{ formatExample }}</pre>
      <h4>使用流程</h4>
      <el-steps :active="4" align-center style="margin-top: 16px;">
        <el-step title="上传文档" description="通过文档管理上传" />
        <el-step title="选择文档" description="选择已解析完成的文档" />
        <el-step title="生成数据" description="LLM 自动生成 QA 对" />
        <el-step title="下载 JSONL" description="上传到训练平台微调" />
        <el-step title="对比效果" description="RAG vs 微调回答质量" />
      </el-steps>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { InfoFilled, Download } from '@element-plus/icons-vue'
import { getComparison, estimateTrainingData, downloadTrainingData, type ComparisonItem, type EstimateResult } from '@/api/finetune'
import { getAllTasks } from '@/api/document'
import { ElMessage } from 'element-plus'

const loadingCompare = ref(false)
const comparison = ref<ComparisonItem[]>([])
const recommendation = ref('')

const tasks = ref<any[]>([])
const selectedTaskId = ref('')
const estimate = ref<EstimateResult | null>(null)
const generating = ref(false)

const formatExample = `{"messages":[
  {"role":"system","content":"你是一个企业知识库问答助手..."},
  {"role":"user","content":"Spring AI 如何配置 Embedding？"},
  {"role":"assistant","content":"Spring AI 配置 Embedding 需要在 application.yml 中..."}
]}`

async function loadComparison() {
  loadingCompare.value = true
  try {
    const data = await getComparison()
    comparison.value = data.comparison || []
    recommendation.value = data.recommendation || ''
  } catch {
    ElMessage.error('加载对比信息失败')
  } finally {
    loadingCompare.value = false
  }
}

async function loadTasks() {
  try {
    tasks.value = await getAllTasks()
  } catch {
    // ignore
  }
}

async function onTaskSelect(taskId: string) {
  if (!taskId) return
  try {
    estimate.value = await estimateTrainingData(taskId)
  } catch {
    ElMessage.error('估算失败')
  }
}

async function handleDownload() {
  if (!selectedTaskId.value) return
  generating.value = true
  ElMessage.info('正在生成训练数据，请稍候...')

  try {
    const blob = await downloadTrainingData(selectedTaskId.value)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `training_data_${selectedTaskId.value}.jsonl`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('下载成功')
  } catch (e: any) {
    ElMessage.error('生成失败: ' + (e.message || '未知错误'))
  } finally {
    generating.value = false
  }
}

onMounted(() => {
  loadComparison()
  loadTasks()
})
</script>

<style scoped>
.finetune-page {
  padding: 24px;
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 24px;
}

.subtitle {
  color: #909399;
  margin: 0;
}

.compare-card, .generate-card, .format-card {
  margin-bottom: 20px;
}

.compare-card h3, .generate-card h3, .format-card h3 {
  margin: 0;
  font-size: 16px;
}

.recommendation {
  margin-top: 16px;
  padding: 12px 16px;
  background: #f0f9eb;
  border-radius: 6px;
  color: #67c23a;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  line-height: 1.6;
}

.gen-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.gen-desc {
  color: #606266;
  margin: 0 0 20px 0;
}

.estimate-panel {
  margin-top: 20px;
}

.gen-actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.format-example {
  padding: 16px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
}
</style>

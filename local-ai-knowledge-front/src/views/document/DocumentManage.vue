<template>
  <div class="document-manage-container">
    <div class="page-header">
      <h2>文档管理</h2>
      <p class="subtitle">上传和管理知识库文档，支持 PDF、TXT、DOCX 格式</p>
    </div>

    <!-- 上传区域 -->
    <div class="upload-section">
      <div
        class="upload-area"
        :class="{ 'is-dragover': isDragover }"
        @dragover.prevent="isDragover = true"
        @dragleave="isDragover = false"
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
      >
        <input
          ref="fileInputRef"
          type="file"
          accept=".pdf,.txt,.docx"
          style="display: none"
          @change="handleFileChange"
        />
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">
          <span class="main-text">拖拽文件到此处，或 <em>点击上传</em></span>
          <span class="sub-text">支持 PDF、TXT、DOCX 格式，单文件不超过 50MB</span>
        </div>
      </div>

      <!-- 上传进度 -->
      <div v-if="uploading" class="upload-progress">
        <el-progress :percentage="uploadProgress" :status="uploadProgress === 100 ? 'success' : undefined" />
        <span class="progress-text">正在上传: {{ uploadingFileName }}</span>
      </div>
    </div>

    <!-- 文档列表 -->
    <div class="document-list">
      <div class="list-header">
        <h3>文档列表</h3>
        <el-button text @click="loadDocuments">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <el-table :data="documents" v-loading="loading" stripe>
        <el-table-column prop="fileName" label="文件名" min-width="200">
          <template #default="{ row }">
            <div class="file-name-cell">
              <el-icon><Document /></el-icon>
              <span>{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scope" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.scope === 'PRIVATE' ? 'warning' : 'info'" size="small">
              {{ row.scope === 'PRIVATE' ? '私有' : '公共' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunksIndexed" label="索引块" width="100">
          <template #default="{ row }">
            {{ row.chunksIndexed || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewLogs(row.taskId)">
              <el-icon><View /></el-icon>
              日志
            </el-button>
            <el-button link type="danger" size="small" @click="deleteDocument(row.taskId)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && documents.length === 0" description="暂无文档，请先上传" />
    </div>

    <!-- 日志对话框 -->
    <el-dialog v-model="logDialogVisible" title="任务日志" width="700px">
      <div class="log-container">
        <div v-if="currentTask" class="task-info">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="任务ID">{{ currentTask.taskId }}</el-descriptions-item>
            <el-descriptions-item label="文件名">{{ currentTask.fileName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentTask.status)" size="small">
                {{ getStatusText(currentTask.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="索引块数">{{ currentTask.chunksIndexed || 0 }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <el-divider />
        <div class="log-content">
          <div v-for="(log, index) in taskLogs" :key="index" class="log-item">
            <span class="log-index">{{ index + 1 }}.</span>
            <span class="log-text">{{ log }}</span>
          </div>
          <el-empty v-if="taskLogs.length === 0" description="暂无日志" :image-size="40" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAllTasks, getTaskLogs, getTaskStatus, uploadDocument } from '@/api/document'
import type { DocumentTask } from '@/types'
import {
  UploadFilled,
  Document,
  Refresh,
  View,
  Delete
} from '@element-plus/icons-vue'

const fileInputRef = ref<HTMLInputElement>()
const documents = ref<DocumentTask[]>([])
const loading = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadingFileName = ref('')
const isDragover = ref(false)

const logDialogVisible = ref(false)
const currentTask = ref<DocumentTask | null>(null)
const taskLogs = ref<string[]>([])

// 加载文档列表
const loadDocuments = async () => {
  loading.value = true
  try {
    documents.value = await getAllTasks()
  } catch (error) {
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

// 触发文件选择
const triggerFileInput = () => {
  fileInputRef.value?.click()
}

// 处理文件选择
const handleFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    uploadFile(input.files[0])
    input.value = ''
  }
}

// 处理拖拽
const handleDrop = (event: DragEvent) => {
  isDragover.value = false
  const files = event.dataTransfer?.files
  if (files && files.length > 0) {
    uploadFile(files[0])
  }
}

// 上传文件
const uploadFile = async (file: File) => {
  const allowedTypes = ['.pdf', '.txt', '.docx']
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  
  if (!allowedTypes.includes(ext)) {
    ElMessage.error('只支持 PDF、TXT、DOCX 格式')
    return
  }
  
  const maxSize = 50 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 50MB')
    return
  }
  
  uploadingFileName.value = file.name
  uploading.value = true
  uploadProgress.value = 0
  
  try {
    const response = await uploadDocument(file)
    ElMessage.success('上传成功，正在解析...')
    pollTaskStatus(response.taskId)
    loadDocuments()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.error || '上传失败')
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

// 轮询任务状态
const pollTaskStatus = async (taskId: string) => {
  const interval = setInterval(async () => {
    try {
      const task = await getTaskStatus(taskId)
      const index = documents.value.findIndex(d => d.taskId === taskId)
      if (index !== -1) {
        documents.value[index] = task
      }
      
      if (task.status === 'COMPLETED' || task.status === 'FAILED') {
        clearInterval(interval)
        if (task.status === 'COMPLETED') {
          ElMessage.success('文档解析完成')
        } else {
          ElMessage.error('文档解析失败: ' + task.errorMessage)
        }
      }
    } catch {
      clearInterval(interval)
    }
  }, 2000)
}

// 查看日志
const viewLogs = async (taskId: string) => {
  try {
    const data = await getTaskLogs(taskId)
    currentTask.value = data.task
    taskLogs.value = data.logs || []
    logDialogVisible.value = true
  } catch {
    ElMessage.error('加载日志失败')
  }
}

// 删除文档
const deleteDocument = (taskId: string) => {
  ElMessageBox.confirm('确定要删除这个文档吗？删除后无法恢复。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    documents.value = documents.value.filter(d => d.taskId !== taskId)
    ElMessage.success('已删除')
  }).catch(() => {})
}

// 格式化文件大小
const formatFileSize = (bytes: number) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

// 获取状态类型
const getStatusType = (status: string) => {
  const types: Record<string, string> = {
    PENDING: 'info',
    PROCESSING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger'
  }
  return types[status] || 'info'
}

// 获取状态文本
const getStatusText = (status: string) => {
  const texts: Record<string, string> = {
    PENDING: '等待中',
    PROCESSING: '处理中',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return texts[status] || status
}

onMounted(() => {
  loadDocuments()
})
</script>

<style scoped>
.document-manage-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #303133;
}

.subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.upload-section {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.upload-area {
  width: 100%;
  padding: 40px;
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  background: #fafafa;
  transition: all 0.3s;
  cursor: pointer;
  text-align: center;
}

.upload-area:hover,
.upload-area.is-dragover {
  border-color: #409eff;
  background: #f0f9ff;
}

.upload-icon {
  font-size: 48px;
  color: #909399;
  margin-bottom: 16px;
}

.upload-text {
  text-align: center;
}

.main-text {
  display: block;
  font-size: 16px;
  color: #606266;
  margin-bottom: 8px;
}

.main-text em {
  color: #409eff;
  font-style: normal;
}

.sub-text {
  font-size: 12px;
  color: #909399;
}

.upload-progress {
  margin-top: 16px;
}

.progress-text {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.document-list {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.list-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-container {
  max-height: 500px;
  overflow-y: auto;
}

.task-info {
  margin-bottom: 16px;
}

.log-content {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.8;
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  max-height: 300px;
  overflow-y: auto;
}

.log-item {
  margin-bottom: 4px;
}

.log-index {
  color: #909399;
  margin-right: 8px;
}

.log-text {
  color: #606266;
}
</style>

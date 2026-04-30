<template>
  <div class="document-manage-container">
    <div class="page-header">
      <h2>文档管理</h2>
      <p class="subtitle">上传文档到知识库，查看解析状态和日志</p>
    </div>

    <div class="document-list-card">
      <div class="card-header">
        <h3>文档列表</h3>
        <div class="header-actions">
          <el-button type="primary" @click="uploadDialogVisible = true">
            <el-icon><Upload /></el-icon>
            上传文档
          </el-button>
          <el-button text @click="loadTasks">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>

      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="fileName" label="文件名" min-width="200">
          <template #default="{ row }">
            <div class="file-cell">
              <el-icon><Document /></el-icon>
              <span>{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="dark">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="docScope" label="范围" width="90">
          <template #default="{ row }">
            <el-tag :type="row.docScope === 'PRIVATE' ? 'warning' : 'success'" size="small">
              {{ row.docScope === 'PRIVATE' ? '私有' : '公开' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="importedChunks" label="切片进度" width="140">
          <template #default="{ row }">
            <template v-if="row.status === 'DONE'">
              <el-tag type="success" size="small">{{ row.totalChunks }} 段</el-tag>
            </template>
            <template v-else-if="row.status === 'IMPORTING'">
              <el-progress
                :percentage="row.totalChunks ? Math.round(row.importedChunks / row.totalChunks * 100) : 0"
                :stroke-width="14"
                :text-inside="true"
                size="small"
              />
            </template>
            <template v-else-if="row.status === 'FAILED'">
              <span class="text-muted">-</span>
            </template>
            <template v-else>
              <span class="text-muted">等待中</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, row)">
              <el-button link type="primary" size="small">
                <el-icon><More /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logs" :icon="View">查看日志</el-dropdown-item>
                  <el-dropdown-item command="download" :icon="Download">下载文档</el-dropdown-item>
                  <el-dropdown-item command="reparse" :icon="RefreshRight">重新解析</el-dropdown-item>
                  <el-dropdown-item command="delete" :icon="Delete" divided>
                    <span style="color: var(--el-color-danger)">删除文档</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && tasks.length === 0" description="暂无文档" />
    </div>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传文档" width="500px" @closed="resetUploadForm">
      <el-form label-position="top">
        <el-form-item label="选择文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="fileList"
            drag
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持常见文档格式，文件大小不超过 50MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="文档范围">
          <el-radio-group v-model="uploadDocScope">
            <el-radio value="PRIVATE">
              <el-tag type="warning" size="small">私有</el-tag>
              <span class="scope-desc">仅自己可见</span>
            </el-radio>
            <el-radio value="PUBLIC">
              <el-tag type="success" size="small">公开</el-tag>
              <span class="scope-desc">所有人可见</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadLoading" :disabled="!selectedFile" @click="handleUpload">
          上传
        </el-button>
      </template>
    </el-dialog>

    <!-- 日志对话框 -->
    <el-dialog v-model="logDialogVisible" title="任务日志" width="700px">
      <div v-if="logData" class="log-content">
        <div class="log-task-info">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="文件名">{{ logData.task.fileName }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusTagType(logData.task.status)" size="small">
                {{ statusLabel(logData.task.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="大小">{{ formatFileSize(logData.task.fileSize) }}</el-descriptions-item>
            <el-descriptions-item label="范围">
              {{ logData.task.docScope === 'PRIVATE' ? '私有' : '公开' }}
            </el-descriptions-item>
            <el-descriptions-item label="切片进度">
              {{ logData.task.importedChunks }} / {{ logData.task.totalChunks }}
            </el-descriptions-item>
            <el-descriptions-item label="上传时间">{{ formatDate(logData.task.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间">{{ logData.task.finishedAt ? formatDate(logData.task.finishedAt) : '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="logData.task.errorMsg" label="错误信息">
              <span class="error-text">{{ logData.task.errorMsg }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
        <el-divider content-position="left">操作日志</el-divider>
        <div v-if="logData.logs && logData.logs.length > 0" class="log-list">
          <div v-for="(log, index) in logData.logs" :key="index" class="log-item">
            {{ log }}
          </div>
        </div>
        <el-empty v-else description="暂无日志" :image-size="60" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadInstance, UploadFile } from 'element-plus'
import { uploadDocument, getAllTasks, getTaskStatus, getTaskLogs, deleteDocument, reparseDocument, getDownloadUrl } from '@/api/document'
import type { DocumentTask, DocumentTaskLog, TaskStatus, DocScope } from '@/types'
import {
  Upload,
  Refresh,
  RefreshRight,
  Document,
  View,
  Download,
  Delete,
  More
} from '@element-plus/icons-vue'

const tasks = ref<DocumentTask[]>([])
const loading = ref(false)
const uploadDialogVisible = ref(false)
const uploadLoading = ref(false)
const logDialogVisible = ref(false)
const logData = ref<DocumentTaskLog | null>(null)
const selectedFile = ref<File | null>(null)
const fileList = ref<UploadFile[]>([])
const uploadRef = ref<UploadInstance>()
const uploadDocScope = ref<DocScope>('PRIVATE')
let pollTimer: ReturnType<typeof setInterval> | null = null

// 状态标签映射
const statusLabel = (status: TaskStatus) => {
  const map: Record<TaskStatus, string> = {
    UPLOADED: '已上传',
    PARSING: '解析中',
    IMPORTING: '入库中',
    DONE: '完成',
    FAILED: '失败'
  }
  return map[status] || status
}

const statusTagType = (status: TaskStatus) => {
  const map: Record<TaskStatus, string> = {
    UPLOADED: 'info',
    PARSING: 'warning',
    IMPORTING: '',
    DONE: 'success',
    FAILED: 'danger'
  }
  return map[status] || 'info'
}

// 加载任务列表
const loadTasks = async () => {
  loading.value = true
  try {
    tasks.value = await getAllTasks()
  } catch (error) {
    ElMessage.error('加载文档列表失败')
  } finally {
    loading.value = false
  }
}

// 文件选择
const handleFileChange = (file: UploadFile) => {
  selectedFile.value = file.raw || null
}

const handleFileRemove = () => {
  selectedFile.value = null
}

// 上传文件
const handleUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择文件')
    return
  }

  uploadLoading.value = true
  try {
    const res = await uploadDocument(selectedFile.value, uploadDocScope.value)
    ElMessage.success(`文件上传成功，任务ID: ${res.taskId}`)
    uploadDialogVisible.value = false
    loadTasks()
    startPolling()
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败')
  } finally {
    uploadLoading.value = false
  }
}

// 自动轮询解析状态（上传后自动开始，全部完成后自动停止）
const startPolling = () => {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    await loadTasks()
    const hasPending = tasks.value.some(t => 
      t.status === 'UPLOADED' || t.status === 'PARSING' || t.status === 'IMPORTING'
    )
    if (!hasPending) {
      stopPolling()
    }
  }, 3000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// 重置上传表单
const resetUploadForm = () => {
  selectedFile.value = null
  fileList.value = []
  uploadDocScope.value = 'PRIVATE'
}

// 操作菜单命令分发
const handleCommand = (command: string, row: DocumentTask) => {
  switch (command) {
    case 'logs':
      handleViewLogs(row)
      break
    case 'download':
      handleDownload(row)
      break
    case 'reparse':
      handleReparse(row)
      break
    case 'delete':
      handleDelete(row)
      break
  }
}

// 重新解析文档
const handleReparse = async (row: DocumentTask) => {
  try {
    await ElMessageBox.confirm(
      `确定要重新解析文档「${row.fileName}」吗？将清除已有切片数据并重新读取、切片、入库。`,
      '确认重新解析',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await reparseDocument(row.taskId)
    ElMessage.success('重新解析已触发')
    loadTasks()
    startPolling()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '重新解析失败')
    }
  }
}

// 下载文档
const handleDownload = (row: DocumentTask) => {
  const url = getDownloadUrl(row.taskId)
  const token = localStorage.getItem('token')
  // 使用隐藏a标签下载，带token认证
  const link = document.createElement('a')
  link.href = `${url}?token=${token}`
  link.target = '_blank'
  link.click()
}

// 删除文档
const handleDelete = async (row: DocumentTask) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文档「${row.fileName}」吗？删除后将同时清除知识库中的向量数据，不可恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteDocument(row.taskId)
    ElMessage.success('删除成功')
    loadTasks()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 查看日志
const handleViewLogs = async (row: DocumentTask) => {
  try {
    logData.value = await getTaskLogs(row.taskId)
    logDialogVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '获取日志失败')
  }
}

// 刷新单个任务状态
const handleRefreshStatus = async (row: DocumentTask) => {
  try {
    const updated = await getTaskStatus(row.taskId)
    const index = tasks.value.findIndex(t => t.taskId === row.taskId)
    if (index !== -1) {
      tasks.value[index] = updated
    }
    ElMessage.success('状态已刷新')
  } catch (error: any) {
    ElMessage.error(error.message || '刷新失败')
  }
}

// 格式化文件大小
const formatFileSize = (bytes: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  // 加载任务列表，如果有未完成的任务自动开始轮询
  loadTasks().then(() => {
    const hasPending = tasks.value.some(t =>
      t.status === 'UPLOADED' || t.status === 'PARSING' || t.status === 'IMPORTING'
    )
    if (hasPending) startPolling()
  })
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
.document-manage-container {
  max-width: 1400px;
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

.document-list-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.file-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scope-desc {
  margin-left: 4px;
  color: #909399;
  font-size: 12px;
}

.log-content {
  max-height: 500px;
  overflow-y: auto;
}

.log-task-info {
  margin-bottom: 8px;
}

.log-list {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  max-height: 300px;
  overflow-y: auto;
}

.log-item {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
  padding: 4px 0;
}

.log-item:last-child {
  border-bottom: none;
}

.error-text {
  color: #f56c6c;
  font-size: 13px;
}

.text-muted {
  color: #c0c4cc;
}
</style>

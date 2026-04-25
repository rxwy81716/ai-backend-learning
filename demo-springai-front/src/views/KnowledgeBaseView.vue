<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Delete, Upload } from '@element-plus/icons-vue'
import {
  type DocumentInfo,
  listDocs,
  uploadDoc,
  deleteDoc,
  batchDelete
} from '@/api/kb'

const docs = ref<DocumentInfo[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const uploading = ref(false)
const selectedRows = ref<DocumentInfo[]>([])

const form = ref({
  source: '',
  title: '',
  content: ''
})

async function loadDocs() {
  loading.value = true
  try {
    docs.value = await listDocs()
  } finally {
    loading.value = false
  }
}

function openUploadDialog() {
  form.value = { source: '', title: '', content: '' }
  dialogVisible.value = true
}

async function handleUpload() {
  if (!form.value.source.trim() || !form.value.title.trim() || !form.value.content.trim()) {
    ElMessage.warning('请填写完整信息')
    return
  }
  uploading.value = true
  try {
    const result = await uploadDoc(form.value)
    ElMessage.success(`上传成功！共生成 ${result.chunks} 个片段`)
    dialogVisible.value = false
    await loadDocs()
  } finally {
    uploading.value = false
  }
}

/** 读取本地文件填充到 content */
function handleFileChange(file: any) {
  const reader = new FileReader()
  reader.onload = (e) => {
    form.value.content = String(e.target?.result || '')
    if (!form.value.source) form.value.source = file.name
    if (!form.value.title) form.value.title = file.name.replace(/\.[^.]+$/, '')
  }
  reader.readAsText(file.raw)
  return false  // 阻止 el-upload 自动上传
}

async function handleDelete(row: DocumentInfo) {
  await ElMessageBox.confirm(`确定删除文档「${row.title}」吗？该操作不可恢复。`, '删除确认', {
    type: 'warning'
  })
  await deleteDoc(row.source)
  ElMessage.success('删除成功')
  await loadDocs()
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要删除的文档')
    return
  }
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedRows.value.length} 个文档吗？`,
    '批量删除确认',
    { type: 'warning' }
  )
  await batchDelete(selectedRows.value.map((r) => r.source))
  ElMessage.success('批量删除成功')
  await loadDocs()
}

function formatTime(ts: number) {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(() => {
  loadDocs()
})
</script>

<template>
  <div class="kb-container">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-button type="primary" :icon="Plus" @click="openUploadDialog">上传文档</el-button>
      <el-button :icon="Refresh" @click="loadDocs">刷新</el-button>
      <el-button
        type="danger"
        :icon="Delete"
        :disabled="selectedRows.length === 0"
        @click="handleBatchDelete"
      >
        批量删除
      </el-button>
      <span class="total">共 {{ docs.length }} 个文档</span>
    </div>

    <!-- 文档列表 -->
    <el-table
      v-loading="loading"
      :data="docs"
      stripe
      class="doc-table"
      @selection-change="selectedRows = $event"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column prop="source" label="文档标识" min-width="200" show-overflow-tooltip />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="totalChunks" label="片段数" width="100" align="center">
        <template #default="{ row }">
          <el-tag>{{ row.totalChunks }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="上传时间" width="180">
        <template #default="{ row }">{{ formatTime(row.uploadTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无文档，点击上方上传按钮添加" />
      </template>
    </el-table>

    <!-- 上传对话框 -->
    <el-dialog v-model="dialogVisible" title="上传文档" width="700px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <el-form-item label="文档标识" required>
          <el-input
            v-model="form.source"
            placeholder="唯一标识，建议用文件名或业务ID（如 java-intro.md）"
          />
        </el-form-item>
        <el-form-item label="文档标题" required>
          <el-input v-model="form.title" placeholder="人类可读的标题" />
        </el-form-item>
        <el-form-item label="读取本地文件">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleFileChange"
            accept=".txt,.md,.json"
          >
            <el-button :icon="Upload">选择文件（.txt / .md / .json）</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="文档内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="12"
            placeholder="粘贴文档内容，或上方选择本地文件自动填充"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">
          {{ uploading ? '入库中...' : '确定上传' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.kb-container {
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;

  .total {
    margin-left: auto;
    color: #909399;
    font-size: 13px;
  }
}

.doc-table {
  flex: 1;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
</style>

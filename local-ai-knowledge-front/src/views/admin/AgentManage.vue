<template>
  <div class="agent-manage-container">
    <div class="page-header">
      <h2>智能体管理</h2>
      <p class="subtitle">配置 System Prompt，支持多套 Prompt 动态切换</p>
    </div>

    <!-- 智能体列表 -->
    <div class="agent-list-card">
      <div class="card-header">
        <h3>Prompt 列表</h3>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新建智能体
        </el-button>
      </div>

      <el-table :data="agents" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="150">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.name }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="250">
          <template #default="{ row }">
            <span v-if="row.description">{{ row.description }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="isDefault" label="默认" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success" size="small" effect="dark">默认</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-space>
              <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-dropdown trigger="click">
                <el-button size="small">
                  更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="!row.isDefault" @click="handleSetDefault(row)">设为默认</el-dropdown-item>
                    <el-dropdown-item @click="handlePreview(row)">预览</el-dropdown-item>
                    <el-dropdown-item 
                      divided 
                      @click="handleDelete(row)" 
                      :disabled="row.isDefault"
                      style="color: #f56c6c"
                    >
                      删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </el-space>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="isEdit ? '编辑智能体' : '新建智能体'" 
      width="700px"
      @closed="resetForm"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如: rag_default, rag_creative" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简要描述此 Prompt 的用途" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
        <el-form-item label="Prompt 内容" prop="content">
          <el-input 
            v-model="form.content" 
            type="textarea" 
            :rows="10"
            placeholder="请输入 System Prompt，支持 {context} 占位符"
          />
        </el-form-item>
        <el-alert
          title="提示"
          type="info"
          :closable="false"
          style="margin-top: 8px"
        >
          使用 <code>{context}</code> 作为检索结果占位符，系统会自动替换为知识库检索内容。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog v-model="previewVisible" title="Prompt 预览" width="700px">
      <div class="preview-content">
        <p><strong>名称：</strong>{{ previewData.name }}</p>
        <p><strong>描述：</strong>{{ previewData.description }}</p>
        <p><strong>是否为默认：</strong>{{ previewData.isDefault ? '是' : '否' }}</p>
        <el-divider />
        <pre>{{ previewData.content }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, ArrowDown } from '@element-plus/icons-vue'
import { 
  getAgents, 
  createAgent, 
  updateAgent, 
  setDefaultAgent, 
  deleteAgent,
  type SystemPrompt 
} from '@/api/admin'

const agents = ref<SystemPrompt[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const previewVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const previewData = ref<SystemPrompt>({
  id: 0,
  name: '',
  content: '',
  description: '',
  isDefault: false,
  createdAt: '',
  updatedAt: ''
})

const form = reactive({
  name: '',
  content: '',
  description: '',
  isDefault: false
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { pattern: /^[a-z_]+$/, message: '名称只能包含小写字母和下划线', trigger: 'blur' }
  ],
  content: [
    { required: true, message: 'Prompt 内容不能为空', trigger: 'blur' }
  ]
}

// 加载列表
const loadAgents = async () => {
  loading.value = true
  try {
    agents.value = await getAgents()
  } catch (error) {
    console.error('加载失败:', error)
    ElMessage.error('加载智能体列表失败')
  } finally {
    loading.value = false
  }
}

// 打开创建对话框
const openCreateDialog = () => {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    name: '',
    content: '',
    description: '',
    isDefault: false
  })
  dialogVisible.value = true
}

// 打开编辑对话框
const openEditDialog = (row: SystemPrompt) => {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    content: row.content,
    description: row.description,
    isDefault: row.isDefault
  })
  dialogVisible.value = true
}

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields()
}

// 提交
const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    submitLoading.value = true
    try {
      if (isEdit.value && editingId.value) {
        await updateAgent(editingId.value, form)
        ElMessage.success('更新成功')
      } else {
        await createAgent(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadAgents()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 设为默认
const handleSetDefault = async (row: SystemPrompt) => {
  try {
    await setDefaultAgent(row.id)
    ElMessage.success('已设为默认')
    loadAgents()
  } catch (error: any) {
    ElMessage.error(error.message || '设置失败')
  }
}

// 预览
const handlePreview = (row: SystemPrompt) => {
  previewData.value = row
  previewVisible.value = true
}

// 删除
const handleDelete = async (row: SystemPrompt) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除智能体「${row.name}」吗？`,
      '确认删除',
      { type: 'warning' }
    )
    await deleteAgent(row.id)
    ElMessage.success('已删除')
    loadAgents()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  loadAgents()
})
</script>

<style scoped>
.agent-manage-container {
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

.agent-list-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.card-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.preview-content {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
}

.preview-content p {
  margin: 0 0 8px;
}

.preview-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}

.text-muted {
  color: #c0c4cc;
}
</style>

<template>
  <div class="page-wrap">
    <div class="page-header">
      <h2>对话模型配置</h2>
      <p class="subtitle">
        配置自备的 OpenAI 兼容接口（baseUrl、API Key、模型名等）。保存后在
        <router-link to="/rag">智能问答</router-link>
        中选择对应模型，请求体将携带 <code>model=user:别名</code>。
      </p>
    </div>

    <el-card shadow="never" class="toolbar-card">
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建配置
      </el-button>
      <el-button @click="loadList" :loading="loading">刷新</el-button>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无自定义模型">
        <el-table-column prop="label" label="显示名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="alias" label="别名" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.alias }}</el-tag>
            <div class="hint">model=user:{{ row.alias }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="Base URL" min-width="200" show-overflow-tooltip />
        <el-table-column prop="model" label="模型" width="160" show-overflow-tooltip />
        <el-table-column label="Key" width="120">
          <template #default="{ row }">
            <span>{{ row.apiKeyHint || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="trySavedRow(row)" :loading="tryingId === row.id">
              测试
            </el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑对话模型' : '新建对话模型'"
      width="560px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item v-if="!isEdit" label="别名" prop="alias">
          <el-input v-model="form.alias" placeholder="2~64 字母数字下划线中划线，如 myds" />
        </el-form-item>
        <el-form-item v-else label="别名">
          <el-input :model-value="form.alias" disabled />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.label" placeholder="可选" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.example.com" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password :placeholder="apiKeyPlaceholder" />
        </el-form-item>
        <el-form-item label="Completions 路径">
          <el-input v-model="form.completionsPath" placeholder="可选，如 /v1/chat/completions" />
        </el-form-item>
        <el-form-item label="模型名" prop="model">
          <el-input v-model="form.model" placeholder="如 deepseek-chat" />
        </el-form-item>
        <el-form-item label="temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="max_tokens">
          <el-input-number v-model="form.maxTokens" :min="1" :max="128000" :step="256" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button @click="tryInline" :loading="tryingInline">测试连接</el-button>
        <el-button type="primary" @click="submitForm" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 用户自备对话模型配置页：对接 /api/user/chat-models，保存后在「智能问答」中选 user:{alias}。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  listUserChatModels,
  saveUserChatModel,
  deleteUserChatModel,
  tryUserChatModelInline,
  tryUserChatModelSaved,
  type UserChatModelVo,
  type UserChatModelSavePayload
} from '@/api/userChatModel'

const loading = ref(false)
const rows = ref<UserChatModelVo[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const tryingId = ref<number | null>(null)
const tryingInline = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined as number | undefined,
  alias: '',
  label: '',
  baseUrl: '',
  apiKey: '',
  completionsPath: '',
  model: '',
  temperature: 0.3 as number | undefined,
  maxTokens: 2048 as number | undefined
})

const apiKeyPlaceholder = computed(() =>
  isEdit.value ? '留空表示不修改已保存的 Key' : '必填'
)

const rules: FormRules = {
  alias: [{ required: true, message: '请输入别名', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  model: [{ required: true, message: '请输入模型名', trigger: 'blur' }],
  apiKey: [
    {
      validator: (_r, v, cb) => {
        if (!isEdit.value && (!v || !String(v).trim())) {
          cb(new Error('新建时必须填写 API Key'))
        } else {
          cb()
        }
      },
      trigger: 'blur'
    }
  ]
}

const loadList = async () => {
  loading.value = true
  try {
    rows.value = await listUserChatModels()
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  form.id = undefined
  form.alias = ''
  form.label = ''
  form.baseUrl = ''
  form.apiKey = ''
  form.completionsPath = ''
  form.model = ''
  form.temperature = 0.3
  form.maxTokens = 2048
  formRef.value?.clearValidate()
}

const openCreate = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: UserChatModelVo) => {
  isEdit.value = true
  form.id = row.id
  form.alias = row.alias
  form.label = row.label || ''
  form.baseUrl = row.baseUrl
  form.apiKey = ''
  form.completionsPath = row.completionsPath || ''
  form.model = row.model
  form.temperature = row.temperature ?? 0.3
  form.maxTokens = row.maxTokens ?? 2048
  dialogVisible.value = true
}

const tryInline = async () => {
  const baseUrl = form.baseUrl?.trim()
  const apiKey = form.apiKey?.trim()
  const model = form.model?.trim()
  if (!baseUrl || !model) {
    ElMessage.warning('请先填写 Base URL 与模型名')
    return
  }
  if (!isEdit.value && !apiKey) {
    ElMessage.warning('新建时请先填写 API Key 再测试')
    return
  }
  tryingInline.value = true
  try {
    if (isEdit.value && !apiKey) {
      if (!form.id) return
      const r = await tryUserChatModelSaved(form.id)
      ElMessage.success(`连通 OK，预览：${r.preview?.slice(0, 80) ?? ''}`)
    } else {
      if (!apiKey) {
        ElMessage.warning('请填写 API Key')
        return
      }
      const r = await tryUserChatModelInline({
        baseUrl,
        apiKey,
        completionsPath: form.completionsPath?.trim() || undefined,
        model,
        temperature: form.temperature,
        maxTokens: form.maxTokens
      })
      ElMessage.success(`连通 OK，预览：${r.preview?.slice(0, 80) ?? ''}`)
    }
  } catch {
    /* 全局提示 */
  } finally {
    tryingInline.value = false
  }
}

const trySavedRow = async (row: UserChatModelVo) => {
  tryingId.value = row.id
  try {
    const r = await tryUserChatModelSaved(row.id)
    ElMessage.success(`「${row.alias}」测试成功：${r.preview?.slice(0, 60) ?? ''}`)
  } catch {
    /* */
  } finally {
    tryingId.value = null
  }
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate().catch(() => Promise.reject())
  saving.value = true
  try {
    const payload: UserChatModelSavePayload = {
      id: form.id,
      alias: isEdit.value ? undefined : form.alias.trim(),
      label: form.label?.trim() || undefined,
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey?.trim() || undefined,
      completionsPath: form.completionsPath?.trim() || undefined,
      model: form.model.trim(),
      temperature: form.temperature,
      maxTokens: form.maxTokens
    }
    await saveUserChatModel(payload)
    ElMessage.success('已保存')
    dialogVisible.value = false
    await loadList()
  } catch {
    /* */
  } finally {
    saving.value = false
  }
}

const handleDelete = (row: UserChatModelVo) => {
  ElMessageBox.confirm(`确定删除「${row.alias}」？智能问答中不可再选 user:${row.alias}。`, '删除确认', {
    type: 'warning'
  })
    .then(async () => {
      await deleteUserChatModel(row.id)
      ElMessage.success('已删除')
      await loadList()
    })
    .catch(() => {})
}

onMounted(loadList)
</script>

<style scoped>
.page-wrap {
  max-width: 1100px;
  margin: 0 auto;
}
.page-header {
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #303133;
}
.subtitle {
  margin: 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}
.subtitle code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}
.toolbar-card {
  margin-bottom: 16px;
}
.table-card {
  margin-bottom: 24px;
}
.hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>

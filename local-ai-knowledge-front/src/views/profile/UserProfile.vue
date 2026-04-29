<template>
  <div class="profile-container">
    <div class="page-header">
      <h2>个人中心</h2>
      <p class="subtitle">查看和编辑个人信息</p>
    </div>

    <div class="profile-card">
      <div class="avatar-section">
        <el-avatar :size="80" :icon="UserFilled" class="profile-avatar" />
        <div class="avatar-info">
          <h3>{{ userStore.nickname }}</h3>
          <el-tag v-if="userStore.isAdmin" type="danger" size="small" effect="dark">管理员</el-tag>
          <el-tag v-else type="success" size="small" effect="plain">普通用户</el-tag>
        </div>
      </div>

      <el-divider />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        class="profile-form"
      >
        <el-form-item label="用户名">
          <el-input :value="userStore.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="角色">
          <div class="role-tags">
            <el-tag v-for="role in userStore.roles" :key="role" size="small" type="info" effect="plain">
              {{ role }}
            </el-tag>
          </div>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">修改密码</el-divider>

      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="80px"
        class="profile-form"
      >
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" placeholder="请输入新密码（至少6位）" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>
      </el-form>

      <div class="form-actions">
        <el-button type="primary" @click="handleSave" :loading="saving">保存信息</el-button>
        <el-button @click="handleChangePwd" :loading="changingPwd" :disabled="!pwdForm.oldPassword">修改密码</el-button>
      </div>
    </div>

    <div class="stats-card">
      <h3>使用统计</h3>
      <el-row :gutter="20">
        <el-col :span="8">
          <div class="stat-item">
            <el-icon :size="28" color="#409eff"><ChatDotRound /></el-icon>
            <div class="stat-value">{{ stats.sessions }}</div>
            <div class="stat-label">会话数</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-item">
            <el-icon :size="28" color="#67c23a"><Document /></el-icon>
            <div class="stat-value">{{ stats.documents }}</div>
            <div class="stat-label">文档数</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-item">
            <el-icon :size="28" color="#e6a23c"><Clock /></el-icon>
            <div class="stat-value">{{ stats.joinDays }}</div>
            <div class="stat-label">加入天数</div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getSessions } from '@/api/rag'
import { getAllTasks } from '@/api/document'
import { UserFilled, ChatDotRound, Document, Clock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const pwdFormRef = ref<FormInstance>()
const saving = ref(false)
const changingPwd = ref(false)

const form = reactive({
  nickname: userStore.nickname || ''
})

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const stats = reactive({
  sessions: 0,
  documents: 0,
  joinDays: 0
})

const rules: FormRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_: any, value: string, callback: Function) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const handleSave = async () => {
  saving.value = true
  try {
    // 目前后端未提供修改昵称接口，此处提示
    ElMessage.info('昵称修改功能开发中')
  } finally {
    saving.value = false
  }
}

const handleChangePwd = async () => {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  changingPwd.value = true
  try {
    ElMessage.info('密码修改功能开发中')
  } finally {
    changingPwd.value = false
  }
}

// 加载统计数据
const loadStats = async () => {
  try {
    const [sessions, tasks] = await Promise.all([
      getSessions().catch(() => []),
      getAllTasks().catch(() => [])
    ])
    stats.sessions = sessions.length
    stats.documents = tasks.length

    // 计算加入天数（从 userInfo 的 createdAt 推算）
    const createdAt = (userStore.userInfo as any)?.createdAt
    if (createdAt) {
      const days = Math.floor((Date.now() - new Date(createdAt).getTime()) / 86400000)
      stats.joinDays = Math.max(days, 1)
    } else {
      stats.joinDays = 1
    }
  } catch (e) {
    console.error('加载统计数据失败', e)
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.profile-container {
  max-width: 800px;
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

.profile-card {
  background: #fff;
  border-radius: 8px;
  padding: 32px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

.profile-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.avatar-info h3 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #303133;
}

.profile-form {
  max-width: 500px;
}

.role-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  padding-left: 80px;
}

.stats-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.stats-card h3 {
  margin: 0 0 16px;
  font-size: 16px;
  color: #303133;
}

.stat-item {
  text-align: center;
  padding: 16px;
  border-radius: 8px;
  background: #f5f7fa;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  margin: 8px 0 4px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

@media screen and (max-width: 768px) {
  .profile-card {
    padding: 20px;
  }

  .form-actions {
    padding-left: 0;
    flex-direction: column;
  }

  .avatar-section {
    flex-direction: column;
    text-align: center;
  }
}
</style>

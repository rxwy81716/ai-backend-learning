<template>
  <div class="user-manage-container">
    <div class="page-header">
      <h2>用户管理</h2>
      <p class="subtitle">管理系统用户，分配角色和权限</p>
    </div>

    <div class="user-list-card">
      <div class="card-header">
        <h3>用户列表</h3>
        <div class="header-actions">
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新建用户
          </el-button>
          <el-button text @click="loadUsers">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>

      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" width="150">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28" :icon="UserFilled" />
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="150">
          <template #default="{ row }">
            {{ row.nickname || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180">
          <template #default="{ row }">
            {{ row.email || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="roles" label="角色" width="200">
          <template #default="{ row }">
            <el-tag
              v-for="role in row.roles"
              :key="role.code"
              :type="role.code === 'ROLE_ADMIN' ? 'danger' : role.code === 'ROLE_VIP' ? 'warning' : 'success'"
              size="small"
              style="margin-right: 4px"
            >
              {{ role.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :loading="row._loading"
              @change="handleToggleEnabled(row)"
              :disabled="row.id === currentUserId"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEditDialog(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)" :disabled="row.id === currentUserId">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && users.length === 0" description="暂无用户" />
    </div>

    <!-- 角色分配对话框 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="500px">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="用户">
          <span>{{ roleForm.username }}</span>
        </el-form-item>
        <el-form-item label="当前角色">
          <el-tag
            v-for="role in roleForm.roles"
            :key="role.code"
            type="success"
            size="small"
            style="margin-right: 4px"
          >
            {{ role.name }}
          </el-tag>
        </el-form-item>
        <el-form-item label="新角色">
          <el-select v-model="roleForm.selectedRole" placeholder="请选择角色">
            <el-option label="普通用户" value="ROLE_USER" />
            <el-option label="管理员" value="ROLE_ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleDialogLoading" @click="handleAssignRole">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑用户对话框 -->
    <el-dialog v-model="userDialogVisible" :title="isEdit ? '编辑用户' : '新建用户'" width="500px">
      <el-form :model="userForm" :rules="userFormRules" ref="userFormRef" label-width="80px" label-position="top">
        <el-form-item label="用户名" prop="username" v-if="!isEdit">
          <el-input v-model="userForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" :prop="isEdit ? '' : 'password'" v-if="!isEdit">
          <el-input v-model="userForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="userForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" placeholder="请输入手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userDialogLoading" @click="handleSaveUser">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getUsers, assignRole, setUserEnabled, createUser, updateUser, deleteUser } from '@/api/admin'
import type { UserListItem } from '@/types'
import { useUserStore } from '@/stores/user'
import {
  Refresh,
  UserFilled,
  Setting,
  Plus,
  Edit,
  Delete
} from '@element-plus/icons-vue'

const userStore = useUserStore()

const users = ref<any[]>([])
const loading = ref(false)
const roleDialogVisible = ref(false)
const roleDialogLoading = ref(false)
const userDialogVisible = ref(false)
const userDialogLoading = ref(false)
const isEdit = ref(false)
const userFormRef = ref<FormInstance>()

const currentUserId = computed(() => userStore.userInfo?.id)

const roleForm = reactive({
  userId: 0,
  username: '',
  roles: [] as string[],
  selectedRole: ''
})

const userForm = reactive({
  id: 0,
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: ''
})

const userFormRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 个字符', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

// 角色编码转名称
const getRoleName = (code: string) => {
  const roleNames: Record<string, string> = {
    'ROLE_ADMIN': '管理员',
    'ROLE_USER': '普通用户',
    'ROLE_VIP': '会员'
  }
  return roleNames[code] || code
}

// 加载用户列表
const loadUsers = async () => {
  loading.value = true
  try {
    const data = await getUsers()
    users.value = data.map((u: any) => ({ ...u, _loading: false }))
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

// 打开角色分配对话框
const openRoleDialog = (user: any) => {
  roleForm.userId = user.id
  roleForm.username = user.username
  roleForm.roles = user.roles || []
  roleForm.selectedRole = user.roles?.[0]?.code || 'ROLE_USER'
  roleDialogVisible.value = true
}

// 分配角色
const handleAssignRole = async () => {
  if (!roleForm.selectedRole) {
    ElMessage.warning('请选择角色')
    return
  }

  roleDialogLoading.value = true
  try {
    await assignRole(roleForm.userId, roleForm.selectedRole)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    loadUsers()
  } catch (error: any) {
    ElMessage.error(error.message || '分配失败')
  } finally {
    roleDialogLoading.value = false
  }
}

// 切换启用状态
const handleToggleEnabled = async (user: any) => {
  user._loading = true
  try {
    await setUserEnabled(user.id, user.enabled)
    ElMessage.success(user.enabled ? '用户已启用' : '用户已禁用')
  } catch (error) {
    // 回滚状态
    user.enabled = !user.enabled
    ElMessage.error('操作失败')
  } finally {
    user._loading = false
  }
}

// 打开新建用户对话框
const openCreateDialog = () => {
  isEdit.value = false
  userForm.id = 0
  userForm.username = ''
  userForm.password = ''
  userForm.nickname = ''
  userForm.email = ''
  userForm.phone = ''
  userDialogVisible.value = true
}

// 打开编辑用户对话框
const openEditDialog = (user: any) => {
  isEdit.value = true
  userForm.id = user.id
  userForm.username = user.username
  userForm.password = ''
  userForm.nickname = user.nickname || ''
  userForm.email = user.email || ''
  userForm.phone = user.phone || ''
  userDialogVisible.value = true
}

// 保存用户（新建或编辑）
const handleSaveUser = async () => {
  if (!userFormRef.value) return
  
  await userFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    userDialogLoading.value = true
    try {
      if (isEdit.value) {
        await updateUser(userForm.id, {
          nickname: userForm.nickname,
          email: userForm.email,
          phone: userForm.phone
        })
        ElMessage.success('用户更新成功')
      } else {
        await createUser({
          username: userForm.username,
          password: userForm.password,
          nickname: userForm.nickname,
          email: userForm.email,
          phone: userForm.phone
        })
        ElMessage.success('用户创建成功')
      }
      userDialogVisible.value = false
      loadUsers()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      userDialogLoading.value = false
    }
  })
}

// 删除用户
const handleDelete = async (user: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${user.username}" 吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await deleteUser(user.id)
    ElMessage.success('用户删除成功')
    loadUsers()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 格式化日期
const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-manage-container {
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

.user-list-card {
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

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>

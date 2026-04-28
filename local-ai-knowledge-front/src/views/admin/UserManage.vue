<template>
  <div class="user-manage-container">
    <div class="page-header">
      <h2>用户管理</h2>
      <p class="subtitle">管理系统用户，分配角色和权限</p>
    </div>

    <div class="user-list-card">
      <div class="card-header">
        <h3>用户列表</h3>
        <el-button text @click="loadUsers">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <el-table :data="users" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
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
              :key="role.roleCode"
              :type="role.roleCode === 'ROLE_ADMIN' ? 'danger' : 'success'"
              size="small"
              style="margin-right: 4px"
            >
              {{ role.roleName }}
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
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openRoleDialog(row)">
              <el-icon><Setting /></el-icon>
              分配角色
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
            :key="role.roleCode"
            type="success"
            size="small"
            style="margin-right: 4px"
          >
            {{ role.roleName }}
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsers, assignRole, setUserEnabled } from '@/api/admin'
import type { UserListItem } from '@/types'
import { useUserStore } from '@/stores/user'
import {
  Refresh,
  UserFilled,
  Setting
} from '@element-plus/icons-vue'

const userStore = useUserStore()

const users = ref<UserListItem[]>([])
const loading = ref(false)
const roleDialogVisible = ref(false)
const roleDialogLoading = ref(false)

const currentUserId = computed(() => userStore.userInfo?.id)

const roleForm = reactive({
  userId: 0,
  username: '',
  roles: [] as any[],
  selectedRole: ''
})

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
const openRoleDialog = (user: UserListItem) => {
  roleForm.userId = user.id
  roleForm.username = user.username
  roleForm.roles = user.roles
  roleForm.selectedRole = user.roles[0]?.roleCode || 'ROLE_USER'
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
const handleToggleEnabled = async (user: UserListItem & { _loading?: boolean }) => {
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

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>

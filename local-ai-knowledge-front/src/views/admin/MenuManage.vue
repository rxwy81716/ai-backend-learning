<template>
  <div class="menu-manage-container">
    <div class="page-header">
      <h2>菜单权限管理</h2>
      <p class="subtitle">配置系统菜单和角色权限</p>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：菜单列表 -->
      <el-col :span="12">
        <div class="menu-card">
          <div class="card-header">
            <h3>菜单列表</h3>
            <el-button type="primary" size="small" @click="openCreateMenu">
              <el-icon><Plus /></el-icon>
              新增菜单
            </el-button>
          </div>

          <el-table :data="menus" v-loading="loading" stripe row-key="id" :tree-props="{ children: 'children' }">
            <el-table-column prop="name" label="菜单名称" min-width="120">
              <template #default="{ row }">
                <span>{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="path" label="路由路径" min-width="150">
              <template #default="{ row }">
                <code class="path-code">{{ row.path }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="icon" label="图标" width="80" align="center">
              <template #default="{ row }">
                <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="isVisible" label="显示" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isVisible" type="success" size="small">是</el-tag>
                <el-tag v-else type="info" size="small">否</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEditMenu(row)">
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button link type="danger" size="small" @click="deleteMenu(row)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 右侧：角色菜单配置 -->
      <el-col :span="12">
        <div class="role-permission-card">
          <div class="card-header">
            <h3>角色权限配置</h3>
          </div>

          <div class="role-select">
            <el-select v-model="selectedRoleId" placeholder="选择角色" size="default" style="width: 200px">
              <el-option
                v-for="role in roles"
                :key="role.id"
                :label="role.name"
                :value="role.id"
              />
            </el-select>
          </div>

          <div v-if="selectedRoleId" class="permission-tree">
            <el-checkbox v-model="checkAll" @change="handleCheckAll" :indeterminate="isIndeterminate">
              全选
            </el-checkbox>
            <el-divider />
            <el-checkbox-group v-model="selectedMenuIds">
              <el-checkbox
                v-for="menu in menus"
                :key="menu.id"
                :label="menu.id"
                :value="menu.id"
                style="display: block; margin-left: 0; margin-bottom: 8px"
              >
                {{ menu.name }}
                <code class="path-code-small">{{ menu.path }}</code>
              </el-checkbox>
            </el-checkbox-group>
          </div>

          <div v-if="selectedRoleId" class="permission-actions">
            <el-button type="primary" @click="saveRoleMenus" :loading="saving">
              保存权限配置
            </el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 创建/编辑菜单对话框 -->
    <el-dialog v-model="menuDialogVisible" :title="isEditMenu ? '编辑菜单' : '新增菜单'" width="500px">
      <el-form :model="menuForm" :rules="menuRules" ref="menuFormRef" label-width="100px">
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="menuForm.name" placeholder="如：智能问答" />
        </el-form-item>
        <el-form-item label="路由路径" prop="path">
          <el-input v-model="menuForm.path" placeholder="如：/rag" />
        </el-form-item>
        <el-form-item label="组件路径" prop="component">
          <el-input v-model="menuForm.component" placeholder="如：/views/rag/RagChat.vue" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="menuForm.icon" placeholder="Element Plus 图标名" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="menuForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="显示">
          <el-switch v-model="menuForm.isVisible" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMenu" :loading="menuSaving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface Menu {
  id: number
  parentId: number
  name: string
  path: string
  component?: string
  icon?: string
  sortOrder: number
  isVisible: boolean
  isEnabled: boolean
  children?: Menu[]
}

interface Role {
  id: number
  code: string
  name: string
}

const menus = ref<Menu[]>([])
const roles = ref<Role[]>([])
const loading = ref(false)
const selectedRoleId = ref<number | null>(null)
const selectedMenuIds = ref<number[]>([])
const saving = ref(false)
const menuDialogVisible = ref(false)
const menuSaving = ref(false)
const isEditMenu = ref(false)
const menuFormRef = ref()

const menuForm = reactive({
  id: 0,
  name: '',
  path: '',
  component: '',
  icon: '',
  sortOrder: 0,
  isVisible: true
})

const menuRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入路由路径', trigger: 'blur' }]
}

// 加载菜单列表
const loadMenus = async () => {
  loading.value = true
  try {
    menus.value = await request.get<any, Menu[]>('/api/admin/menus')
  } catch {
    ElMessage.error('加载菜单失败')
  } finally {
    loading.value = false
  }
}

// 加载角色列表
const loadRoles = async () => {
  try {
    roles.value = await request.get<any, Role[]>('/api/admin/roles')
  } catch {
    ElMessage.error('加载角色失败')
  }
}

// 加载角色菜单权限
const loadRoleMenus = async (roleId: number) => {
  try {
    const data = await request.get<any, any>(`/api/admin/roles/${roleId}/menus`)
    selectedMenuIds.value = data.menuIds || []
  } catch {
    ElMessage.error('加载角色菜单失败')
  }
}

// 监听角色选择变化
watch(selectedRoleId, (newVal) => {
  if (newVal) {
    loadRoleMenus(newVal)
  } else {
    selectedMenuIds.value = []
  }
})

// 全选/取消全选
const checkAll = computed({
  get: () => selectedMenuIds.value.length === menus.value.length && menus.value.length > 0,
  set: () => {}
})

const isIndeterminate = computed(() => {
  return selectedMenuIds.value.length > 0 && selectedMenuIds.value.length < menus.value.length
})

const handleCheckAll = (val: boolean) => {
  selectedMenuIds.value = val ? menus.value.map(m => m.id) : []
}

// 保存角色菜单权限
const saveRoleMenus = async () => {
  if (!selectedRoleId.value) return
  saving.value = true
  try {
    await request.put(`/api/admin/roles/${selectedRoleId.value}/menus`, {
      menuIds: selectedMenuIds.value
    })
    ElMessage.success('权限配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 打开创建菜单对话框
const openCreateMenu = () => {
  isEditMenu.value = false
  Object.assign(menuForm, {
    id: 0,
    name: '',
    path: '',
    component: '',
    icon: '',
    sortOrder: 0,
    isVisible: true
  })
  menuDialogVisible.value = true
}

// 打开编辑菜单对话框
const openEditMenu = (menu: Menu) => {
  isEditMenu.value = true
  Object.assign(menuForm, {
    id: menu.id,
    name: menu.name,
    path: menu.path,
    component: menu.component || '',
    icon: menu.icon || '',
    sortOrder: menu.sortOrder || 0,
    isVisible: menu.isVisible
  })
  menuDialogVisible.value = true
}

// 保存菜单
const saveMenu = async () => {
  if (!menuFormRef.value) return
  await menuFormRef.value.validate(async (valid) => {
    if (!valid) return
    menuSaving.value = true
    try {
      if (isEditMenu.value) {
        await request.put(`/api/admin/menus/${menuForm.id}`, menuForm)
        ElMessage.success('菜单已更新')
      } else {
        await request.post('/api/admin/menus', menuForm)
        ElMessage.success('菜单已创建')
      }
      menuDialogVisible.value = false
      loadMenus()
    } catch (e: any) {
      ElMessage.error(e.message || '保存失败')
    } finally {
      menuSaving.value = false
    }
  })
}

// 删除菜单
const deleteMenu = async (menu: Menu) => {
  try {
    await ElMessageBox.confirm(`确定删除菜单「${menu.name}」吗？`, '删除确认', { type: 'warning' })
    await request.delete(`/api/admin/menus/${menu.id}`)
    ElMessage.success('菜单已删除')
    loadMenus()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

onMounted(() => {
  loadMenus()
  loadRoles()
})
</script>

<style scoped>
.menu-manage-container {
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

.menu-card,
.role-permission-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
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

.path-code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  color: #606266;
}

.role-select {
  margin-bottom: 20px;
}

.permission-tree {
  min-height: 200px;
}

.path-code-small {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 11px;
  color: #909399;
  margin-left: 8px;
}

.permission-actions {
  margin-top: 20px;
  text-align: center;
}
</style>

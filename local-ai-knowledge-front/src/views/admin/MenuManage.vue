<template>
  <div class="menu-manage-container">
    <div class="page-header">
      <h2>菜单权限管理</h2>
      <p class="subtitle">配置系统菜单结构和角色权限</p>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：菜单列表（树形表格） -->
      <el-col :span="12">
        <div class="menu-card">
          <div class="card-header">
            <h3>菜单列表</h3>
            <div>
              <el-button size="small" @click="openCreateMenu(0)">
                <el-icon><Plus /></el-icon>
                新增顶级菜单
              </el-button>
            </div>
          </div>

          <el-table
            :data="menus"
            v-loading="loading"
            stripe
            row-key="id"
            :tree-props="{ children: 'children' }"
            default-expand-all
          >
            <el-table-column prop="name" label="菜单名称" min-width="140">
              <template #default="{ row }">
                <span>{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="path" label="路由路径" min-width="150">
              <template #default="{ row }">
                <code class="path-code">{{ row.path }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="icon" label="图标" width="70" align="center">
              <template #default="{ row }">
                <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="60" align="center" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="success" size="small" @click="openCreateMenu(row.id)" title="添加子菜单">
                  <el-icon><Plus /></el-icon>
                </el-button>
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

      <!-- 右侧：角色菜单配置（树形勾选） -->
      <el-col :span="12">
        <div class="role-permission-card">
          <div class="card-header">
            <h3>角色权限配置</h3>
          </div>

          <div class="role-select">
            <el-select v-model="selectedRoleId" placeholder="选择角色" style="width: 200px">
              <el-option
                v-for="role in roles"
                :key="role.id"
                :label="role.name"
                :value="role.id"
              />
            </el-select>
          </div>

          <div v-if="selectedRoleId" class="permission-tree">
            <div class="perm-toolbar">
              <el-button text size="small" @click="handlePermExpandAll(true)">展开全部</el-button>
              <el-button text size="small" @click="handlePermExpandAll(false)">收起全部</el-button>
              <el-divider direction="vertical" />
              <el-button text size="small" @click="handlePermSelectAll">全选</el-button>
              <el-button text size="small" @click="handlePermDeselectAll">清空</el-button>
            </div>
            <el-tree
              ref="permTreeRef"
              :data="menus"
              :props="{ children: 'children', label: 'name' }"
              show-checkbox
              node-key="id"
              default-expand-all
            >
              <template #default="{ data }">
                <span class="tree-node-label">
                  <span>{{ data.name }}</span>
                  <code class="path-code-small">{{ data.path }}</code>
                </span>
              </template>
            </el-tree>
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
    <el-dialog v-model="menuDialogVisible" :title="isEditMenu ? '编辑菜单' : '新增菜单'" width="520px">
      <el-form :model="menuForm" :rules="menuRules" ref="menuFormRef" label-width="100px">
        <el-form-item label="父级菜单">
          <el-tree-select
            v-model="menuForm.parentId"
            :data="parentMenuOptions"
            :props="{ children: 'children', label: 'name', value: 'id' }"
            placeholder="顶级菜单"
            clearable
            check-strictly
            :render-after-expand="false"
            default-expand-all
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="menuForm.name" placeholder="如：智能问答" />
        </el-form-item>
        <el-form-item label="路由路径" prop="path">
          <el-input v-model="menuForm.path" placeholder="如：/rag" />
        </el-form-item>
        <el-form-item label="组件路径">
          <el-input v-model="menuForm.component" placeholder="如：rag/RagChat（父菜单留空）" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="menuForm.icon" placeholder="Element Plus 图标名" />
        </el-form-item>
        <el-form-item label="排序">
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
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
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
const saving = ref(false)
const menuDialogVisible = ref(false)
const menuSaving = ref(false)
const isEditMenu = ref(false)
const menuFormRef = ref()
const permTreeRef = ref<InstanceType<typeof import('element-plus')['ElTree']>>()

const menuForm = reactive({
  id: 0,
  parentId: 0 as number | null,
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

// 父级菜单下拉选项：顶级 + 树形
const parentMenuOptions = computed(() => {
  return [{ id: 0, name: '顶级菜单', children: menus.value }]
})

// ==================== 菜单 CRUD ====================

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

const openCreateMenu = (parentId: number) => {
  isEditMenu.value = false
  Object.assign(menuForm, {
    id: 0,
    parentId: parentId || 0,
    name: '',
    path: '',
    component: '',
    icon: '',
    sortOrder: 0,
    isVisible: true
  })
  menuDialogVisible.value = true
}

const openEditMenu = (menu: Menu) => {
  isEditMenu.value = true
  Object.assign(menuForm, {
    id: menu.id,
    parentId: menu.parentId || 0,
    name: menu.name,
    path: menu.path,
    component: menu.component || '',
    icon: menu.icon || '',
    sortOrder: menu.sortOrder || 0,
    isVisible: menu.isVisible
  })
  menuDialogVisible.value = true
}

const saveMenu = async () => {
  if (!menuFormRef.value) return
  await menuFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    menuSaving.value = true
    try {
      const payload = { ...menuForm, parentId: menuForm.parentId || 0 }
      if (isEditMenu.value) {
        await request.put(`/api/admin/menus/${menuForm.id}`, payload)
        ElMessage.success('菜单已更新')
      } else {
        await request.post('/api/admin/menus', payload)
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

const deleteMenu = async (menu: Menu) => {
  try {
    await ElMessageBox.confirm(
      `确定删除菜单「${menu.name}」吗？${menu.children?.length ? '（包含子菜单）' : ''}`,
      '删除确认',
      { type: 'warning' }
    )
    await request.delete(`/api/admin/menus/${menu.id}`)
    ElMessage.success('菜单已删除')
    loadMenus()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

// ==================== 角色权限配置（树形） ====================

const loadRoles = async () => {
  try {
    roles.value = await request.get<any, Role[]>('/api/admin/roles')
  } catch {
    ElMessage.error('加载角色失败')
  }
}

/** 获取叶子节点 id（非 check-strictly 模式回显只设叶子，树会自动推算父节点状态） */
const getLeafIds = (menuIds: number[], nodes: Menu[]): number[] => {
  const parentIds = new Set<number>()
  const walk = (list: Menu[]) => {
    for (const n of list) {
      if (n.children?.length) {
        parentIds.add(n.id)
        walk(n.children)
      }
    }
  }
  walk(nodes)
  return menuIds.filter(id => !parentIds.has(id))
}

const loadRoleMenus = async (roleId: number) => {
  try {
    const data = await request.get<any, any>(`/api/admin/roles/${roleId}/menus`)
    await nextTick()
    // 只勾叶子节点，el-tree 会自动计算父节点的全选/半选状态
    const leafIds = getLeafIds(data.menuIds || [], menus.value)
    permTreeRef.value?.setCheckedKeys(leafIds)
  } catch {
    ElMessage.error('加载角色菜单失败')
  }
}

watch(selectedRoleId, (newVal) => {
  if (newVal) {
    loadRoleMenus(newVal)
  } else {
    permTreeRef.value?.setCheckedKeys([])
  }
})

/** 收集树中所有节点 id */
const getAllNodeIds = (nodes: Menu[]): number[] => {
  const ids: number[] = []
  const walk = (list: Menu[]) => {
    for (const n of list) {
      ids.push(n.id)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(nodes)
  return ids
}

const handlePermExpandAll = (expand: boolean) => {
  const tree = permTreeRef.value
  if (!tree) return
  for (const id of getAllNodeIds(menus.value)) {
    const node = tree.getNode(id)
    if (node) node.expanded = expand
  }
}

const handlePermSelectAll = () => {
  permTreeRef.value?.setCheckedKeys(getAllNodeIds(menus.value))
}

const handlePermDeselectAll = () => {
  permTreeRef.value?.setCheckedKeys([])
}

const saveRoleMenus = async () => {
  if (!selectedRoleId.value || !permTreeRef.value) return
  saving.value = true
  try {
    // checked（全选节点） + halfChecked（半选父节点）都要保存
    const checked = permTreeRef.value.getCheckedKeys() as number[]
    const halfChecked = permTreeRef.value.getHalfCheckedKeys() as number[]
    const menuIds = [...checked, ...halfChecked]
    await request.put(`/api/admin/roles/${selectedRoleId.value}/menus`, { menuIds })
    ElMessage.success('权限配置已保存')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
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

.text-muted {
  color: #c0c4cc;
}

.role-select {
  margin-bottom: 16px;
}

.permission-tree {
  min-height: 200px;
}

.perm-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.tree-node-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.path-code-small {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 11px;
  color: #909399;
}

.permission-actions {
  margin-top: 20px;
  text-align: center;
}

:deep(.el-tree-node__content) {
  height: 32px;
}
</style>

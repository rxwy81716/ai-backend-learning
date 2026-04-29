<template>
  <div class="role-manage-container">
    <div class="page-header">
      <h2>角色权限管理</h2>
      <p class="subtitle">管理系统角色，配置角色菜单权限</p>
    </div>

    <div class="role-list-card">
      <div class="card-header">
        <h3>角色列表</h3>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新增角色
        </el-button>
      </div>

      <el-table :data="roles" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="角色名称" width="150">
          <template #default="{ row }">
            <el-tag :type="getRoleTagType(row.code)" size="small">
              {{ row.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="角色编码" width="150">
          <template #default="{ row }">
            <code>{{ row.code }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column prop="userCount" label="用户数" width="100" align="center">
          <template #default="{ row }">
            <el-badge :value="row.userCount || 0" :max="99" />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" size="small" @click="openPermDialog(row)">
              <el-icon><Grid /></el-icon>
              权限
            </el-button>
            <el-button link type="primary" size="small" @click="openEditDialog(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button 
              link 
              type="danger" 
              size="small" 
              @click="handleDelete(row)"
              :disabled="row.code === 'ROLE_ADMIN' || row.code === 'ROLE_USER'"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && roles.length === 0" description="暂无角色" />
    </div>

    <!-- 创建/编辑角色对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="isEdit ? '编辑角色' : '新增角色'" 
      width="500px"
      @closed="resetForm"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code" v-if="!isEdit">
          <el-input v-model="form.code" placeholder="如: ROLE_VIP" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input 
            v-model="form.description" 
            type="textarea" 
            :rows="3" 
            placeholder="请输入角色描述" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 权限配置对话框 -->
    <el-dialog
      v-model="permDialogVisible"
      :title="`配置权限 - ${permRole?.name || ''}`"
      width="480px"
      @closed="permRole = null"
    >
      <div class="perm-tree-wrapper" v-loading="permLoading">
        <div class="perm-toolbar">
          <el-button text size="small" @click="handleExpandAll(true)">展开全部</el-button>
          <el-button text size="small" @click="handleExpandAll(false)">收起全部</el-button>
          <el-divider direction="vertical" />
          <el-button text size="small" @click="handleSelectAll">全选</el-button>
          <el-button text size="small" @click="handleDeselectAll">清空</el-button>
        </div>
        <el-tree
          ref="treeRef"
          :data="menuTree"
          :props="treeProps"
          show-checkbox
          node-key="id"
          default-expand-all
          highlight-current
        >
          <template #default="{ node, data }">
            <span class="tree-node-label">
              <span>{{ data.name }}</span>
              <el-tag v-if="data.children && data.children.length" size="small" type="info" effect="plain" class="child-count">
                {{ data.children.length }}
              </el-tag>
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="handleSavePerm">
          保存权限
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Edit, Delete, Grid } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface Role {
  id: number
  code: string
  name: string
  description?: string
  userCount?: number
  createdAt: string
}

interface MenuNode {
  id: number
  parentId: number
  name: string
  path: string
  icon?: string
  children?: MenuNode[]
}

const roles = ref<Role[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: 0,
  name: '',
  code: '',
  description: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

// ==================== 权限配置 ====================

const permDialogVisible = ref(false)
const permLoading = ref(false)
const permSaving = ref(false)
const permRole = ref<Role | null>(null)
const menuTree = ref<MenuNode[]>([])
const treeRef = ref<InstanceType<typeof import('element-plus')['ElTree']>>()

const treeProps = {
  children: 'children',
  label: 'name'
}

/** 收集树中所有节点 id（扁平化） */
const getAllNodeIds = (nodes: MenuNode[]): number[] => {
  const ids: number[] = []
  const walk = (list: MenuNode[]) => {
    for (const n of list) {
      ids.push(n.id)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(nodes)
  return ids
}

/** 获取叶子节点 id（回显时只设叶子，树自动推算父节点全选/半选状态） */
const getLeafIds = (menuIds: number[], nodes: MenuNode[]): number[] => {
  const parentIds = new Set<number>()
  const walk = (list: MenuNode[]) => {
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

/** 打开权限配置弹窗 */
const openPermDialog = async (role: Role) => {
  permRole.value = role
  permDialogVisible.value = true
  permLoading.value = true

  try {
    const [treeData, permData] = await Promise.all([
      request.get<any, MenuNode[]>('/api/admin/menus'),
      request.get<any, { role: Role; menus: MenuNode[]; menuIds: number[] }>(`/api/admin/roles/${role.id}/menus`)
    ])
    menuTree.value = treeData

    await nextTick()
    // 只勾叶子节点，el-tree 自动计算父节点状态
    const leafIds = getLeafIds(permData.menuIds, treeData)
    treeRef.value?.setCheckedKeys(leafIds)
  } catch (e: any) {
    ElMessage.error('加载权限数据失败：' + (e.message || ''))
  } finally {
    permLoading.value = false
  }
}

/** 保存权限：checked（全选）+ halfChecked（半选父节点）都保存 */
const handleSavePerm = async () => {
  if (!permRole.value || !treeRef.value) return

  permSaving.value = true
  try {
    const checked = treeRef.value.getCheckedKeys() as number[]
    const halfChecked = treeRef.value.getHalfCheckedKeys() as number[]
    const menuIds = [...checked, ...halfChecked]
    await request.put(`/api/admin/roles/${permRole.value.id}/menus`, { menuIds })
    ElMessage.success('权限保存成功')
    permDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e.message || ''))
  } finally {
    permSaving.value = false
  }
}

/** 展开/收起全部 */
const handleExpandAll = (expand: boolean) => {
  const tree = treeRef.value
  if (!tree) return
  const allIds = getAllNodeIds(menuTree.value)
  for (const id of allIds) {
    const node = tree.getNode(id)
    if (node) node.expanded = expand
  }
}

/** 全选 */
const handleSelectAll = () => {
  const allIds = getAllNodeIds(menuTree.value)
  treeRef.value?.setCheckedKeys(allIds)
}

/** 清空 */
const handleDeselectAll = () => {
  treeRef.value?.setCheckedKeys([])
}

// ==================== 角色 CRUD ====================

// 获取角色标签类型
const getRoleTagType = (code: string) => {
  if (code === 'ROLE_ADMIN') return 'danger'
  if (code === 'ROLE_VIP') return 'warning'
  return 'success'
}

// 加载角色列表
const loadRoles = async () => {
  loading.value = true
  try {
    const data = await request.get<any, Role[]>('/api/admin/roles')
    roles.value = data
  } catch (error) {
    ElMessage.error('加载角色列表失败')
  } finally {
    loading.value = false
  }
}

// 打开创建对话框
const openCreateDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
}

// 打开编辑对话框
const openEditDialog = (role: Role) => {
  isEdit.value = true
  form.id = role.id
  form.name = role.name
  form.code = role.code
  form.description = role.description || ''
  dialogVisible.value = true
}

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields()
  form.id = 0
  form.name = ''
  form.code = ''
  form.description = ''
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await request.put(`/api/admin/roles/${form.id}`, form)
        ElMessage.success('角色更新成功')
      } else {
        await request.post('/api/admin/roles', form)
        ElMessage.success('角色创建成功')
      }
      dialogVisible.value = false
      loadRoles()
    } catch (error: any) {
      ElMessage.error(error.message || '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

// 删除角色
const handleDelete = async (role: Role) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色「${role.name}」吗？`,
      '删除确认',
      { type: 'warning' }
    )
    await request.delete(`/api/admin/roles/${role.id}`)
    ElMessage.success('角色已删除')
    loadRoles()
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
  loadRoles()
})
</script>

<style scoped>
.role-manage-container {
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

.role-list-card {
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

code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 13px;
}

/* 权限树样式 */
.perm-tree-wrapper {
  min-height: 200px;
}

.perm-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.tree-node-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.child-count {
  font-size: 11px;
  height: 18px;
  line-height: 16px;
}

:deep(.el-tree-node__content) {
  height: 34px;
}

:deep(.el-tree-node__expand-icon) {
  font-size: 14px;
}
</style>

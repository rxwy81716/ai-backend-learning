<template>
  <div class="skill-market">
    <div class="page-header">
      <h2>Skill 能力市场</h2>
      <p class="subtitle">系统已注册的标准化 AI 能力，支持在线测试调用</p>
    </div>

    <!-- 分类筛选 -->
    <div class="filter-bar">
      <el-radio-group v-model="activeCategory" @change="filterSkills">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button v-for="cat in categories" :key="cat" :label="cat">
          {{ categoryLabel(cat) }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- Skill 卡片列表 -->
    <el-row :gutter="20" v-loading="loading">
      <el-col :xs="24" :sm="12" :lg="8" v-for="skill in filteredSkills" :key="skill.name">
        <el-card class="skill-card" shadow="hover" @click="openDetail(skill)">
          <div class="card-header">
            <div class="icon-box" :class="'cat-' + skill.category">
              {{ categoryIcon(skill.category) }}
            </div>
            <div class="title-area">
              <h3>{{ skill.displayName }}</h3>
              <div class="tags">
                <el-tag size="small" :type="categoryType(skill.category)">{{ categoryLabel(skill.category) }}</el-tag>
                <el-tag size="small" type="info">v{{ skill.version }}</el-tag>
              </div>
            </div>
          </div>
          <p class="description">{{ skill.description }}</p>
          <div class="card-footer">
            <span class="param-count">{{ skill.inputParams.length }} 个参数</span>
            <el-button type="primary" size="small" @click.stop="openTest(skill)">
              <el-icon><VideoPlay /></el-icon> 在线测试
            </el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="24" v-if="!loading && filteredSkills.length === 0">
        <el-empty description="暂无已注册的 Skill" />
      </el-col>
    </el-row>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="currentSkill?.displayName" width="600px">
      <template v-if="currentSkill">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="标识名">{{ currentSkill.name }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryLabel(currentSkill.category) }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ currentSkill.version }}</el-descriptions-item>
          <el-descriptions-item label="输出格式">{{ currentSkill.outputFormat }}</el-descriptions-item>
          <el-descriptions-item label="实现类">
            <code>{{ currentSkill.beanClass.split('.').pop() }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="描述">{{ currentSkill.description }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin-top: 16px">输入参数</h4>
        <el-table :data="currentSkill.inputParams.map((p, i) => ({ index: i + 1, desc: p }))" size="small">
          <el-table-column prop="index" label="#" width="50" />
          <el-table-column prop="desc" label="参数说明" />
        </el-table>
      </template>
    </el-dialog>

    <!-- 在线测试弹窗 -->
    <el-dialog v-model="testVisible" :title="'测试: ' + testSkill?.displayName" width="700px">
      <template v-if="testSkill">
        <el-form label-width="100px">
          <el-form-item v-for="param in parsedParams" :key="param.name" :label="param.name">
            <el-input v-model="testParams[param.name]" :placeholder="param.desc" />
          </el-form-item>
        </el-form>
        <div style="text-align: right; margin-bottom: 16px;">
          <el-button type="primary" :loading="testLoading" @click="runTest">
            <el-icon><VideoPlay /></el-icon> 执行
          </el-button>
        </div>
        <div v-if="testResult" class="test-result">
          <el-alert :type="testResult.success ? 'success' : 'error'" :closable="false"
                    :title="testResult.success ? '执行成功' : '执行失败'" />
          <pre class="result-content">{{ testResult.data }}</pre>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { VideoPlay } from '@element-plus/icons-vue'
import { listSkills, executeSkill, type SkillDescriptor, type SkillResult } from '@/api/skill'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const skills = ref<SkillDescriptor[]>([])
const activeCategory = ref('')

// 详情
const detailVisible = ref(false)
const currentSkill = ref<SkillDescriptor | null>(null)

// 测试
const testVisible = ref(false)
const testSkill = ref<SkillDescriptor | null>(null)
const testParams = ref<Record<string, string>>({})
const testLoading = ref(false)
const testResult = ref<SkillResult | null>(null)

const categories = computed(() => {
  const cats = new Set(skills.value.map(s => s.category))
  return Array.from(cats)
})

const filteredSkills = computed(() => {
  if (!activeCategory.value) return skills.value
  return skills.value.filter(s => s.category === activeCategory.value)
})

const parsedParams = computed(() => {
  if (!testSkill.value) return []
  return testSkill.value.inputParams.map(p => {
    const [name, ...descParts] = p.split(':')
    return { name: name.trim(), desc: descParts.join(':').trim() }
  })
})

function categoryLabel(cat: string) {
  const map: Record<string, string> = {
    retrieval: '检索', generation: '生成', analysis: '分析',
    external: '外部服务', general: '通用'
  }
  return map[cat] || cat
}

function categoryIcon(cat: string) {
  const map: Record<string, string> = {
    retrieval: '🔍', generation: '✨', analysis: '📊',
    external: '🌐', general: '⚡'
  }
  return map[cat] || '🔧'
}

function categoryType(cat: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    retrieval: '', generation: 'success', analysis: 'warning',
    external: 'danger', general: 'info'
  }
  return map[cat] || 'info'
}

function filterSkills() { /* reactive via computed */ }

function openDetail(skill: SkillDescriptor) {
  currentSkill.value = skill
  detailVisible.value = true
}

function openTest(skill: SkillDescriptor) {
  testSkill.value = skill
  testParams.value = {}
  testResult.value = null
  testVisible.value = true
}

async function runTest() {
  if (!testSkill.value) return
  testLoading.value = true
  testResult.value = null
  try {
    const params: Record<string, any> = {}
    for (const [k, v] of Object.entries(testParams.value)) {
      if (v) params[k] = v
    }
    testResult.value = await executeSkill(testSkill.value.name, params)
  } catch (e: any) {
    ElMessage.error('执行失败: ' + (e.message || '未知错误'))
  } finally {
    testLoading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    skills.value = await listSkills()
  } catch (e: any) {
    ElMessage.error('加载 Skill 列表失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.skill-market {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 4px 0;
  font-size: 24px;
}

.subtitle {
  color: #909399;
  margin: 0;
}

.filter-bar {
  margin-bottom: 20px;
}

.skill-card {
  margin-bottom: 20px;
  cursor: pointer;
  transition: transform 0.2s;
  height: 200px;
  display: flex;
  flex-direction: column;
}

.skill-card:hover {
  transform: translateY(-4px);
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.icon-box {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.cat-retrieval { background: #ecf5ff; }
.cat-generation { background: #f0f9eb; }
.cat-analysis { background: #fdf6ec; }
.cat-external { background: #fef0f0; }
.cat-general { background: #f4f4f5; }

.title-area h3 {
  margin: 0 0 4px 0;
  font-size: 16px;
}

.tags {
  display: flex;
  gap: 4px;
}

.description {
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}

.param-count {
  color: #909399;
  font-size: 12px;
}

.test-result {
  margin-top: 12px;
}

.result-content {
  margin-top: 12px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
}
</style>

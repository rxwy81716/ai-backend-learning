<template>
  <div class="hot-history">
    <div class="page-header">
      <h2>历史热榜</h2>
      <p class="subtitle">按日期和来源查询历史热榜数据</p>
    </div>

    <!-- 查询条件 -->
    <div class="query-bar">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="日期">
          <el-date-picker
            v-model="queryDate"
            type="date"
            placeholder="选择日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledDate"
          />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="querySource" clearable placeholder="全部来源" style="width: 160px">
            <el-option
              v-for="(label, key) in SOURCE_LABELS"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :loading="loading">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 当日统计摘要 -->
    <div v-if="stats.length > 0" class="day-stats">
      <el-tag
        v-for="s in stats"
        :key="s.source"
        :type="getSourceTagType(s.source)"
        effect="plain"
        class="stats-tag"
      >
        {{ getSourceLabel(s.source) }}：{{ s.item_count }} 条
      </el-tag>
      <el-tag type="success" effect="dark" class="stats-tag">
        合计：{{ stats.reduce((a, b) => a + b.item_count, 0) }} 条
      </el-tag>
    </div>

    <!-- 数据表格 -->
    <div class="hot-list-card">
      <el-table :data="items" v-loading="loading" stripe max-height="600">
        <el-table-column type="index" label="#" width="60" align="center">
          <template #default="{ $index }">
            <span :class="['rank-badge', { 'rank-top': (currentPage - 1) * pageSize + $index < 3 }]">
              {{ (currentPage - 1) * pageSize + $index + 1 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="130">
          <template #default="{ row }">
            <el-tag :type="getSourceTagType(row.source)" size="small">
              {{ getSourceLabel(row.source) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="300">
          <template #default="{ row }">
            <a v-if="row.url" :href="row.url" target="_blank" class="hot-title-link">
              {{ row.title }}
              <el-icon class="link-icon"><TopRight /></el-icon>
            </a>
            <span v-else>{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="摘要" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="content-text">{{ row.content || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="hot_score" label="热度" width="120">
          <template #default="{ row }">
            <span v-if="row.hot_score" class="hot-score">🔥 {{ row.hot_score }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="crawl_time" label="采集时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.crawl_time) }}
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && searched && items.length === 0" description="该日期暂无热榜数据" />

      <!-- 分页 -->
      <div class="pagination-wrap" v-if="pageTotal > 0">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          :total="pageTotal"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, TopRight } from '@element-plus/icons-vue'
import {
  getHotByDate,
  getStatsByDate,
  SOURCE_LABELS,
  SOURCE_TAG_TYPE
} from '@/api/hot'
import type { HotItem, SourceStat } from '@/api/hot'

const loading = ref(false)
const searched = ref(false)
const items = ref<HotItem[]>([])
const stats = ref<SourceStat[]>([])
const pageTotal = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

// 默认今天
const today = new Date().toISOString().split('T')[0]
const queryDate = ref(today)
const querySource = ref('')

const getSourceLabel = (source: string) => SOURCE_LABELS[source] || source
const getSourceTagType = (source: string) => (SOURCE_TAG_TYPE[source] || 'info') as any

const formatTime = (ts: string) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN')
}

// 禁止选择未来日期
const disabledDate = (time: Date) => {
  return time.getTime() > Date.now()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  loadItems()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadItems()
}

// 加载分页数据
const loadItems = async () => {
  loading.value = true
  try {
    const params: any = { page: currentPage.value, size: pageSize.value }
    if (querySource.value) params.source = querySource.value
    const res = await getHotByDate(queryDate.value, params)
    items.value = res.items || []
    pageTotal.value = res.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '查询失败')
  } finally {
    loading.value = false
  }
}

// 查询（重置页码 + 加载统计 + 分页数据）
const handleSearch = async () => {
  if (!queryDate.value) {
    ElMessage.warning('请选择日期')
    return
  }

  searched.value = true
  currentPage.value = 1

  loading.value = true
  try {
    const statsRes = await getStatsByDate(queryDate.value)
    stats.value = statsRes.sources || []
    await loadItems()
  } catch (error: any) {
    ElMessage.error(error.message || '查询失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.hot-history {
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

.query-bar {
  background: #fff;
  border-radius: 8px;
  padding: 16px 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;
}

.day-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.stats-tag {
  font-size: 13px;
}

.hot-list-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: bold;
  background: #f0f2f5;
  color: #606266;
}

.rank-top {
  background: linear-gradient(135deg, #ff6b6b, #ee5a24);
  color: #fff;
}

.hot-title-link {
  color: #303133;
  text-decoration: none;
  transition: color 0.2s;
}

.hot-title-link:hover {
  color: #409eff;
}

.link-icon {
  font-size: 12px;
  margin-left: 4px;
  vertical-align: middle;
  color: #c0c4cc;
}

.content-text {
  color: #909399;
  font-size: 13px;
}

.hot-score {
  font-size: 13px;
  color: #e6a23c;
}

.text-muted {
  color: #c0c4cc;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media screen and (max-width: 768px) {
  .query-bar :deep(.el-form--inline .el-form-item) {
    margin-bottom: 12px;
  }
}
</style>

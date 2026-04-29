<template>
  <div class="hot-dashboard">
    <div class="page-header">
      <h2>每日热榜</h2>
      <p class="subtitle">多源热点数据采集看板，实时掌握全网热点动态</p>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6" v-for="stat in statsCards" :key="stat.source">
        <div class="stat-card" :style="{ borderTopColor: getSourceColor(stat.source) }">
          <div class="stat-header">
            <el-tag :type="getSourceTagType(stat.source)" size="small" effect="dark">
              {{ getSourceLabel(stat.source) }}
            </el-tag>
          </div>
          <div class="stat-number">{{ stat.item_count }}</div>
          <div class="stat-desc">条热榜数据</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6" v-if="statsCards.length > 0">
        <div class="stat-card stat-card-total">
          <div class="stat-header">
            <el-tag type="success" size="small" effect="dark">今日汇总</el-tag>
          </div>
          <div class="stat-number">{{ totalCount }}</div>
          <div class="stat-desc">条总计</div>
        </div>
      </el-col>
    </el-row>

    <!-- 来源筛选 -->
    <div class="filter-bar">
      <el-radio-group v-model="selectedSource" @change="handleSourceChange">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button
          v-for="source in availableSources"
          :key="source"
          :value="source"
        >
          {{ getSourceLabel(source) }}
        </el-radio-button>
      </el-radio-group>
      <el-button text @click="refresh" :loading="loading">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <!-- 热榜列表 -->
    <div class="hot-list-card">
      <el-table :data="pageItems" v-loading="loading" stripe>
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
        <el-table-column prop="title" label="标题" min-width="250">
          <template #default="{ row }">
            <a v-if="row.url" :href="row.url" target="_blank" class="hot-title-link">
              {{ row.title }}
              <el-icon class="link-icon"><TopRight /></el-icon>
            </a>
            <span v-else>{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.content" class="content-text">{{ row.content }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="hot_score" label="热度" width="120">
          <template #default="{ row }">
            <span v-if="row.hot_score" class="hot-score">
              🔥 {{ row.hot_score }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="crawl_time" label="采集时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.crawl_time) }}
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && pageItems.length === 0" description="今日暂无热榜数据">
        <el-button type="primary" @click="refresh">刷新试试</el-button>
      </el-empty>

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

    <!-- 趋势图 -->
    <div class="trend-card" v-if="trendData.length > 0">
      <h3>近7天采集趋势</h3>
      <div ref="trendChartRef" class="trend-chart"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, TopRight } from '@element-plus/icons-vue'
import {
  getTodayHot,
  getTodayStats,
  getTrendStats,
  SOURCE_LABELS,
  SOURCE_COLORS,
  SOURCE_TAG_TYPE
} from '@/api/hot'
import type { HotItem, SourceStat, TrendItem } from '@/api/hot'

const loading = ref(false)
const pageItems = ref<HotItem[]>([])
const pageTotal = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const statsCards = ref<SourceStat[]>([])
const trendData = ref<TrendItem[]>([])
const selectedSource = ref('')
const trendChartRef = ref<HTMLElement>()
let chartInstance: any = null

// 可选来源列表（从统计数据中取）
const availableSources = computed(() => statsCards.value.map(s => s.source))

// 今日汇总
const totalCount = computed(() =>
  statsCards.value.reduce((sum, s) => sum + s.item_count, 0)
)

const getSourceLabel = (source: string) => SOURCE_LABELS[source] || source
const getSourceColor = (source: string) => SOURCE_COLORS[source] || '#409eff'
const getSourceTagType = (source: string) => (SOURCE_TAG_TYPE[source] || 'info') as any

const formatTime = (ts: string) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN')
}

const handleSourceChange = () => {
  currentPage.value = 1
  loadHotItems()
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  loadHotItems()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadHotItems()
}

// 加载热榜分页数据
const loadHotItems = async () => {
  loading.value = true
  try {
    const params: any = { page: currentPage.value, size: pageSize.value }
    if (selectedSource.value) params.source = selectedSource.value
    const res = await getTodayHot(params)
    pageItems.value = res.items || []
    pageTotal.value = res.total || 0
  } catch (error: any) {
    ElMessage.error(error.message || '加载热榜数据失败')
  } finally {
    loading.value = false
  }
}

// 全量刷新（统计 + 分页 + 趋势）
const refresh = async () => {
  loading.value = true
  try {
    const [statsRes, trendRes] = await Promise.all([
      getTodayStats(),
      getTrendStats(7)
    ])
    statsCards.value = statsRes.sources || []
    trendData.value = trendRes.trend || []

    await loadHotItems()

    await nextTick()
    renderTrendChart()
  } catch (error: any) {
    ElMessage.error(error.message || '加载热榜数据失败')
  } finally {
    loading.value = false
  }
}

// 渲染趋势图
const renderTrendChart = async () => {
  if (!trendChartRef.value || trendData.value.length === 0) return

  // 动态导入 echarts（按需加载）
  const echarts = await import('echarts')

  if (chartInstance) {
    chartInstance.dispose()
  }
  chartInstance = echarts.init(trendChartRef.value)

  // 组织数据：按来源分组
  const sources = [...new Set(trendData.value.map(t => t.source))]
  const dates = [...new Set(trendData.value.map(t => t.crawl_date))].sort()

  const series = sources.map(source => ({
    name: getSourceLabel(source),
    type: 'line' as const,
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    itemStyle: { color: getSourceColor(source) },
    data: dates.map(date => {
      const item = trendData.value.find(t => t.crawl_date === date && t.source === source)
      return item ? item.item_count : 0
    })
  }))

  chartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#eee',
      textStyle: { color: '#333' }
    },
    legend: {
      data: sources.map(getSourceLabel),
      bottom: 0
    },
    grid: {
      left: 50,
      right: 20,
      top: 20,
      bottom: 60
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { fontSize: 12 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { fontSize: 12 }
    },
    series
  })
}

// 窗口缩放自适应
const handleResize = () => {
  chartInstance?.resize()
}

onMounted(() => {
  refresh()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped>
.hot-dashboard {
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

/* 统计卡片 */
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border-top: 3px solid #409eff;
  text-align: center;
  margin-bottom: 12px;
}

.stat-card-total {
  border-top-color: #67c23a;
  background: linear-gradient(135deg, #f0f9eb, #fff);
}

.stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  margin: 8px 0 4px;
}

.stat-desc {
  font-size: 12px;
  color: #909399;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

/* 热榜列表 */
.hot-list-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
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

.hot-score {
  font-size: 13px;
  color: #e6a23c;
}

.content-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
}

.text-muted {
  color: #c0c4cc;
}

/* 分页 */
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* 趋势图 */
.trend-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.trend-card h3 {
  margin: 0 0 16px;
  font-size: 16px;
  color: #303133;
}

.trend-chart {
  width: 100%;
  height: 350px;
}

/* 移动端适配 */
@media screen and (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .trend-chart {
    height: 250px;
  }
}
</style>

<template>
  <div class="crawler-manage">
    <div class="page-header">
      <h2>爬虫数据管理</h2>
      <p class="subtitle">管理爬虫采集任务，查看执行日志与运行状态</p>
    </div>

    <!-- 今日统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6" v-for="stat in todayStats" :key="stat.source">
        <div class="stat-card" :class="{ 'has-fail': stat.fail_count > 0 }">
          <div class="stat-header">
            <el-tag size="small" effect="dark">{{ getLabel(stat.source) }}</el-tag>
          </div>
          <div class="stat-body">
            <div class="stat-num">{{ stat.total_stored }}</div>
            <div class="stat-desc">今日入库</div>
          </div>
          <div class="stat-footer">
            <span class="success-text">成功 {{ stat.success_count }}</span>
            <span v-if="stat.fail_count > 0" class="fail-text">失败 {{ stat.fail_count }}</span>
            <span class="cost-text">平均 {{ stat.avg_cost_ms }}ms</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 操作区 -->
    <div class="action-bar">
      <div class="action-left">
        <el-button type="primary" @click="handleExecuteAll" :loading="executeLoading">
          <el-icon><VideoPlay /></el-icon>
          全量采集
        </el-button>
        <el-dropdown @command="handleExecuteOne" trigger="click">
          <el-button :loading="executeLoading">
            指定来源采集
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="src in sources"
                :key="src.name"
                :command="src.name"
              >
                {{ src.displayName }}
                <el-tag size="small" type="info" style="margin-left: 8px">{{ src.crawlType }}</el-tag>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div class="action-right">
        <el-select v-model="logSourceFilter" clearable placeholder="筛选来源" style="width: 150px" @change="loadLogs">
          <el-option label="全部" value="" />
          <el-option
            v-for="src in sources"
            :key="src.name"
            :label="src.displayName"
            :value="src.name"
          />
        </el-select>
        <el-button text @click="refreshAll" :loading="logsLoading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 执行结果提示 -->
    <el-alert
      v-if="lastResult"
      :title="lastResultTitle"
      :type="lastResult.success ? 'success' : 'error'"
      :description="lastResultDesc"
      show-icon
      closable
      class="result-alert"
      @close="lastResult = null"
    />

    <!-- 任务日志表格 -->
    <div class="log-card">
      <el-table :data="logs" v-loading="logsLoading" stripe max-height="500">
        <el-table-column prop="source" label="来源" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ getLabel(row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="trigger_type" label="触发" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.trigger_type === 'MANUAL' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.trigger_type === 'MANUAL' ? '手动' : '定时' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="success" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small" effect="dark">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="crawled_count" label="采集" width="80" align="center" />
        <el-table-column prop="stored_count" label="入库" width="80" align="center" />
        <el-table-column prop="cost_ms" label="耗时" width="100" align="center">
          <template #default="{ row }">
            <span :class="{ 'slow-text': row.cost_ms > 10000 }">{{ formatCost(row.cost_ms) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="error_msg" label="错误信息" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.error_msg" class="error-text">{{ row.error_msg }}</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="执行时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.created_at) }}
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPlay, ArrowDown, Refresh } from '@element-plus/icons-vue'
import {
  getCrawlerSources,
  executeCrawler,
  executeAllCrawlers,
  getCrawlerLogs,
  getCrawlerLogsBySource,
  getCrawlerTodayStats,
  CRAWLER_SOURCE_LABELS
} from '@/api/crawler'
import type { CrawlerSource, PipelineResult, TaskLog, TodayStat } from '@/api/crawler'

const sources = ref<CrawlerSource[]>([])
const todayStats = ref<TodayStat[]>([])
const logs = ref<TaskLog[]>([])
const logSourceFilter = ref('')
const logsLoading = ref(false)
const executeLoading = ref(false)
const lastResult = ref<PipelineResult | null>(null)

const lastResultTitle = ref('')
const lastResultDesc = ref('')

const getLabel = (source: string) => CRAWLER_SOURCE_LABELS[source] || source

const formatTime = (ts: string) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('zh-CN')
}

const formatCost = (ms: number) => {
  if (ms >= 1000) return (ms / 1000).toFixed(1) + 's'
  return ms + 'ms'
}

const loadSources = async () => {
  try {
    sources.value = await getCrawlerSources()
  } catch (e: any) {
    ElMessage.error('获取数据源失败：' + (e.message || ''))
  }
}

const loadTodayStats = async () => {
  try {
    todayStats.value = await getCrawlerTodayStats()
  } catch { /* ignore */ }
}

const loadLogs = async () => {
  logsLoading.value = true
  try {
    logs.value = logSourceFilter.value
      ? await getCrawlerLogsBySource(logSourceFilter.value)
      : await getCrawlerLogs()
  } catch (e: any) {
    ElMessage.error('获取日志失败：' + (e.message || ''))
  } finally {
    logsLoading.value = false
  }
}

const refreshAll = async () => {
  await Promise.all([loadSources(), loadTodayStats(), loadLogs()])
}

const handleExecuteOne = async (source: string) => {
  const label = getLabel(source)
  try {
    await ElMessageBox.confirm(`确认手动触发 "${label}" 采集？`, '手动采集', {
      confirmButtonText: '开始采集',
      cancelButtonText: '取消',
      type: 'info'
    })
  } catch { return }

  executeLoading.value = true
  try {
    const result = await executeCrawler(source)
    lastResult.value = result
    lastResultTitle.value = `${label} 采集${result.success ? '成功' : '失败'}`
    lastResultDesc.value = result.success
      ? `采集 ${result.crawledCount} 条，入库 ${result.storedCount} 条，耗时 ${formatCost(result.costMs)}`
      : `错误：${result.errorMsg || '未知'}`

    ElMessage[result.success ? 'success' : 'error'](`${label} ${result.success ? '采集成功' : '采集失败'}`)
    await Promise.all([loadTodayStats(), loadLogs()])
  } catch (e: any) {
    ElMessage.error('采集请求失败：' + (e.message || ''))
  } finally {
    executeLoading.value = false
  }
}

const handleExecuteAll = async () => {
  try {
    await ElMessageBox.confirm('确认触发全量采集？所有来源将依次执行。', '全量采集', {
      confirmButtonText: '开始',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch { return }

  executeLoading.value = true
  try {
    const results = await executeAllCrawlers()
    const successCount = results.filter(r => r.success).length
    ElMessage.success(`全量采集完成：成功 ${successCount}/${results.length}`)
    await Promise.all([loadTodayStats(), loadLogs()])
  } catch (e: any) {
    ElMessage.error('全量采集失败：' + (e.message || ''))
  } finally {
    executeLoading.value = false
  }
}

onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
.crawler-manage {
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
  border-left: 3px solid #67c23a;
  margin-bottom: 12px;
}

.stat-card.has-fail {
  border-left-color: #f56c6c;
}

.stat-body {
  margin: 8px 0;
}

.stat-num {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-desc {
  font-size: 12px;
  color: #909399;
}

.stat-footer {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.success-text {
  color: #67c23a;
}

.fail-text {
  color: #f56c6c;
}

.cost-text {
  color: #909399;
}

/* 操作区 */
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.action-left,
.action-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.result-alert {
  margin-bottom: 16px;
}

/* 日志表格 */
.log-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.error-text {
  color: #f56c6c;
  font-size: 13px;
}

.slow-text {
  color: #e6a23c;
  font-weight: bold;
}

.text-muted {
  color: #c0c4cc;
}

@media screen and (max-width: 768px) {
  .action-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

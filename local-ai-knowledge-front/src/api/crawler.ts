import request from '@/utils/request'

// ==================== 数据源 ====================

export function getCrawlerSources() {
  return request.get<any, CrawlerSource[]>('/api/admin/crawler/sources')
}

// ==================== 手动触发 ====================

export function executeCrawler(source: string) {
  return request.post<any, PipelineResult>(`/api/admin/crawler/execute/${source}`)
}

export function executeAllCrawlers() {
  return request.post<any, PipelineResult[]>('/api/admin/crawler/execute-all')
}

// ==================== 运行状态 ====================

export function getCrawlerStats() {
  return request.get<any, CrawlerStatsResponse>('/api/admin/crawler/stats')
}

// ==================== 任务日志 ====================

export function getCrawlerLogs(limit: number = 50) {
  return request.get<any, TaskLog[]>('/api/admin/crawler/logs', { params: { limit } })
}

export function getCrawlerLogsBySource(source: string, limit: number = 30) {
  return request.get<any, TaskLog[]>(`/api/admin/crawler/logs/${source}`, { params: { limit } })
}

export function getCrawlerTodayStats() {
  return request.get<any, TodayStat[]>('/api/admin/crawler/logs/today-stats')
}

// ==================== 类型定义 ====================

export interface CrawlerSource {
  name: string
  displayName: string
  crawlType: string
  category: string
}

export interface PipelineResult {
  source: { name: string; displayName: string }
  success: boolean
  crawledCount: number
  storedCount: number
  costMs: number
  triggerType: string
  errorMsg?: string
}

export interface CrawlerStatsResponse {
  registeredCrawlers: string[]
  crawlerCount: number
  bloomFilterElementCount: number
}

export interface TaskLog {
  id: number
  source: string
  trigger_type: string
  success: boolean
  crawled_count: number
  stored_count: number
  cost_ms: number
  error_msg?: string
  created_at: string
}

export interface TodayStat {
  source: string
  total_runs: number
  success_count: number
  fail_count: number
  total_stored: number
  avg_cost_ms: number
  last_run: string
}

// 来源中文映射
export const CRAWLER_SOURCE_LABELS: Record<string, string> = {
  GITHUB_TRENDING: 'GitHub Trending',
  WEIBO_HOT: '微博热搜',
  ZHIHU_HOT: '知乎热榜',
  BILIBILI_HOT: 'B站热门',
  XIAOHONGSHU: '小红书热门',
  DOUYIN: '抖音热点'
}

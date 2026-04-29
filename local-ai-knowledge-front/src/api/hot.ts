import request from '@/utils/request'

// ==================== 热榜数据查询 ====================

// 今日热榜（分页 + 来源筛选）
export function getTodayHot(params?: { source?: string; page?: number; size?: number }) {
  return request.get<any, HotPageResponse>('/api/hot/today', { params })
}

// 指定日期热榜（分页 + 来源筛选）
export function getHotByDate(date: string, params?: { source?: string; page?: number; size?: number }) {
  return request.get<any, HotPageResponse>(`/api/hot/date/${date}`, { params })
}

// ==================== 统计分析 ====================

// 今日统计
export function getTodayStats() {
  return request.get<any, StatsResponse>('/api/hot/stats/today')
}

// 指定日期统计
export function getStatsByDate(date: string) {
  return request.get<any, StatsResponse>(`/api/hot/stats/date/${date}`)
}

// 趋势统计
export function getTrendStats(days: number = 7) {
  return request.get<any, TrendResponse>('/api/hot/stats/trend', { params: { days } })
}

// Top N
export function getTopN(topN: number = 10, date?: string) {
  const params: any = { topN }
  if (date) params.date = date
  return request.get<any, TopNResponse>('/api/hot/top', { params })
}

// ==================== 类型定义 ====================

export interface HotItem {
  id: number
  source: string
  title: string
  content?: string
  url?: string
  rank: number
  hot_score?: string
  metadata?: string
  crawl_time: string
}

export interface HotListResponse {
  date: string
  source?: string
  total: number
  items: HotItem[]
}

export interface HotPageResponse {
  date: string
  total: number
  page: number
  size: number
  pages: number
  items: HotItem[]
}

export interface SourceStat {
  source: string
  item_count: number
  first_crawl: string
  last_crawl: string
}

export interface StatsResponse {
  date: string
  sources: SourceStat[]
}

export interface TrendItem {
  crawl_date: string
  source: string
  item_count: number
}

export interface TrendResponse {
  days: number
  trend: TrendItem[]
}

export interface TopNResponse {
  date: string
  topN: number
  items: HotItem[]
}

// 来源中文映射
export const SOURCE_LABELS: Record<string, string> = {
  GITHUB_TRENDING: 'GitHub Trending',
  WEIBO_HOT: '微博热搜',
  ZHIHU_HOT: '知乎热榜',
  BILIBILI_HOT: 'B站热门',
  XIAOHONGSHU: '小红书热门',
  DOUYIN: '抖音热点'
}

// 来源颜色映射（用于图表/标签）
export const SOURCE_COLORS: Record<string, string> = {
  GITHUB_TRENDING: '#24292e',
  WEIBO_HOT: '#ff8200',
  ZHIHU_HOT: '#0066ff',
  BILIBILI_HOT: '#fb7299',
  XIAOHONGSHU: '#ff2442',
  DOUYIN: '#010101'
}

// 来源标签类型映射
export const SOURCE_TAG_TYPE: Record<string, string> = {
  GITHUB_TRENDING: '',
  WEIBO_HOT: 'warning',
  ZHIHU_HOT: 'primary',
  BILIBILI_HOT: 'danger',
  XIAOHONGSHU: 'danger',
  DOUYIN: 'info'
}

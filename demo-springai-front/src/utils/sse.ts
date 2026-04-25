import { fetchEventSource } from '@microsoft/fetch-event-source'

/**
 * SSE 流式接收封装
 *
 * 为什么不用浏览器原生 EventSource？
 *   1. 原生只支持 GET，不支持 POST
 *   2. 原生不支持自定义 Header（无法带 Token）
 *   3. fetchEventSource 解决了上述限制
 *
 * 后端 Controller 必须设置：
 *   produces = MediaType.TEXT_EVENT_STREAM_VALUE
 */

export interface SseOptions {
  url: string
  method?: 'GET' | 'POST'
  body?: any
  headers?: Record<string, string>
  /** 收到一段文本时的回调（流式逐字推送） */
  onMessage: (chunk: string) => void
  /** 流结束时回调 */
  onDone?: () => void
  /** 出错时回调 */
  onError?: (err: Error) => void
  /** 用于取消请求的 AbortSignal */
  signal?: AbortSignal
}

export async function streamSSE(options: SseOptions): Promise<void> {
  const { url, method = 'GET', body, headers = {}, onMessage, onDone, onError, signal } = options

  try {
    await fetchEventSource(url, {
      method,
      headers: {
        Accept: 'text/event-stream',
        ...(method === 'POST' ? { 'Content-Type': 'application/json' } : {}),
        ...headers
      },
      body: body ? JSON.stringify(body) : undefined,
      signal,

      // 默认 fetchEventSource 在切到后台标签时会断开重连，关闭这个行为
      openWhenHidden: true,

      onopen: async (response) => {
        if (!response.ok) {
          throw new Error(`SSE连接失败: ${response.status}`)
        }
      },

      onmessage: (ev) => {
        // ev.data 就是 SpringAI 推送的文本片段
        // 注意：空行会被解析为空 data 事件，过滤掉
        if (ev.data) {
          onMessage(ev.data)
        }
      },

      onclose: () => {
        onDone?.()
      },

      onerror: (err) => {
        onError?.(err instanceof Error ? err : new Error(String(err)))
        // 抛出后 fetchEventSource 会停止重试
        throw err
      }
    })
  } catch (err) {
    // AbortError 是用户主动取消，不算错误
    if ((err as Error).name !== 'AbortError') {
      onError?.(err as Error)
    }
  }
}

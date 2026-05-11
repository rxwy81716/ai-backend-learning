<template>
  <div class="mcp-page">
    <div class="page-header">
      <h2>MCP 协议面板</h2>
      <p class="subtitle">Model Context Protocol — AI 工具对接的标准化协议</p>
    </div>

    <!-- 什么是 MCP -->
    <el-card shadow="never" class="intro-card">
      <template #header><h3>什么是 MCP？</h3></template>
      <div class="intro-content">
        <div class="intro-text">
          <p><strong>MCP（Model Context Protocol）</strong> 是 Anthropic 推出的开放协议标准，
            定义了 AI 模型与外部工具/数据源之间的通信规范。</p>
          <p>类比：<strong>USB 协议</strong>让各种设备即插即用，<strong>MCP</strong> 让各种 AI 客户端可以用统一方式调用外部工具。</p>
        </div>
        <div class="analogy-box">
          <div class="analogy-item">
            <div class="analogy-icon">🔌</div>
            <div>
              <strong>USB 协议</strong>
              <p>键盘/鼠标/U盘 → 任何电脑</p>
            </div>
          </div>
          <div class="analogy-arrow">≈</div>
          <div class="analogy-item">
            <div class="analogy-icon">🤖</div>
            <div>
              <strong>MCP 协议</strong>
              <p>AI 工具 → 任何 AI 客户端</p>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 服务端状态 -->
    <el-card shadow="never" class="status-card">
      <template #header>
        <div class="status-header">
          <h3>MCP Server 状态</h3>
          <el-tag :type="serverStatus === 'online' ? 'success' : 'danger'">
            {{ serverStatus === 'online' ? '在线' : '离线' }}
          </el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="端点地址">
          <el-link type="primary" :underline="false">
            <code>{{ mcpEndpoint }}</code>
          </el-link>
          <el-button size="small" text @click="copyEndpoint">复制</el-button>
        </el-descriptions-item>
        <el-descriptions-item label="传输协议">SSE（Server-Sent Events）</el-descriptions-item>
        <el-descriptions-item label="暴露工具数">{{ tools.length }}</el-descriptions-item>
        <el-descriptions-item label="最后检测">{{ lastCheck }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 暴露的工具 -->
    <el-card shadow="never" class="tools-card">
      <template #header><h3>暴露的 MCP 工具</h3></template>
      <el-table :data="tools" stripe>
        <el-table-column prop="name" label="工具名" width="200">
          <template #default="{ row }">
            <code>{{ row.name }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="source" label="底层服务" width="200">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.source }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 与 Function Calling 的区别 -->
    <el-card shadow="never" class="diff-card">
      <template #header><h3>MCP vs Function Calling vs Skill</h3></template>
      <el-table :data="diffData" stripe>
        <el-table-column prop="dimension" label="维度" width="140" />
        <el-table-column prop="functionCalling" label="Function Calling" />
        <el-table-column prop="mcp" label="MCP" />
        <el-table-column prop="skill" label="Skill" />
      </el-table>
    </el-card>

    <!-- 接入示例 -->
    <el-card shadow="never" class="example-card">
      <template #header><h3>客户端接入示例</h3></template>
      <el-tabs>
        <el-tab-pane label="Claude Desktop">
          <p>在 <code>claude_desktop_config.json</code> 中添加：</p>
          <pre class="code-block">{
  "mcpServers": {
    "knowledge-base": {
      "url": "{{ mcpEndpoint }}"
    }
  }
}</pre>
        </el-tab-pane>
        <el-tab-pane label="Cursor / Windsurf">
          <p>在 MCP 设置中添加 SSE 服务器：</p>
          <pre class="code-block">Server Name: knowledge-base
URL: {{ mcpEndpoint }}
Transport: SSE</pre>
        </el-tab-pane>
        <el-tab-pane label="自定义客户端">
          <p>使用任何支持 MCP 协议的 SDK：</p>
          <pre class="code-block"># Python (mcp SDK)
from mcp.client import Client

async with Client("{{ mcpEndpoint }}") as client:
    tools = await client.list_tools()
    result = await client.call_tool(
        "searchKnowledge",
        {"query": "Spring AI 配置"}
    )</pre>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:12116'
const mcpEndpoint = ref(`${baseUrl}/mcp/sse`)

const serverStatus = ref<'online' | 'offline'>('offline')
const lastCheck = ref('-')

const tools = ref([
  {
    name: 'searchKnowledge',
    description: '从企业私域知识库中检索与问题最相关的文档片段（向量 + BM25 混合检索）',
    source: 'HybridSearchService'
  },
  {
    name: 'searchHotTopics',
    description: '查询各平台（微博/知乎/B站/GitHub/抖音/小红书）的实时热搜数据',
    source: 'HotSearchService'
  },
  {
    name: 'searchWeb',
    description: '使用搜索引擎获取实时互联网信息（Tavily API）',
    source: 'WebSearchService'
  }
])

const diffData = ref([
  {
    dimension: '定义位置',
    functionCalling: '应用内部（@Tool 注解）',
    mcp: '标准协议（跨应用）',
    skill: '应用内部（@Skill 注解）'
  },
  {
    dimension: '调用方',
    functionCalling: '本应用的 Agent',
    mcp: '任何 MCP 客户端',
    skill: '编排器 / REST API'
  },
  {
    dimension: '发现机制',
    functionCalling: 'Spring AI 自动绑定',
    mcp: 'tools/list 协议请求',
    skill: 'GET /api/skills'
  },
  {
    dimension: '传输方式',
    functionCalling: '进程内调用',
    mcp: 'SSE / stdio 网络传输',
    skill: 'HTTP REST'
  },
  {
    dimension: '典型场景',
    functionCalling: 'Agent 自主调用工具',
    mcp: 'Claude/Cursor 远程调用',
    skill: '能力市场、编排引擎'
  }
])

async function checkServerStatus() {
  try {
    const response = await fetch(`${baseUrl}/mcp/sse`, {
      method: 'GET',
      signal: AbortSignal.timeout(3000)
    })
    serverStatus.value = response.ok || response.status === 200 ? 'online' : 'offline'
  } catch {
    // SSE endpoint may not respond to simple GET, try health check
    try {
      const resp = await fetch(`${baseUrl}/actuator/health`, { signal: AbortSignal.timeout(3000) })
      serverStatus.value = resp.ok ? 'online' : 'offline'
    } catch {
      serverStatus.value = 'offline'
    }
  }
  lastCheck.value = new Date().toLocaleTimeString()
}

function copyEndpoint() {
  navigator.clipboard.writeText(mcpEndpoint.value)
  ElMessage.success('已复制到剪贴板')
}

onMounted(() => {
  checkServerStatus()
})
</script>

<style scoped>
.mcp-page {
  padding: 24px;
  max-width: 1000px;
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

.intro-card, .status-card, .tools-card, .diff-card, .example-card {
  margin-bottom: 20px;
}

h3 { margin: 0; font-size: 16px; }

.intro-content {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}

.intro-text {
  flex: 1;
}

.intro-text p {
  margin: 0 0 8px 0;
  line-height: 1.7;
  color: #606266;
}

.analogy-box {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
  flex-shrink: 0;
}

.analogy-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.analogy-icon {
  font-size: 32px;
}

.analogy-item p {
  margin: 2px 0 0 0;
  font-size: 12px;
  color: #909399;
}

.analogy-arrow {
  font-size: 24px;
  color: #c0c4cc;
}

.status-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.code-block {
  padding: 16px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
}
</style>

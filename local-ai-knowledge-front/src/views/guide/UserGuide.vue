<template>
  <div class="guide-container">
    <div class="guide-header">
      <h2>使用指南</h2>
      <p class="subtitle">快速了解 AI 知识库的核心功能和操作流程</p>
    </div>

    <!-- 快速开始 -->
    <el-card class="guide-card" shadow="hover">
      <template #header>
        <div class="card-title">
          <el-icon :size="22" color="#409EFF"><Promotion /></el-icon>
          <span>快速开始</span>
        </div>
      </template>
      <el-steps :active="3" finish-status="success" align-center class="guide-steps">
        <el-step title="上传文档" description="在「文档管理」上传 PDF/Word/TXT 等文件" />
        <el-step title="等待解析" description="系统自动解析并切片入库（通常几秒～几分钟）" />
        <el-step title="智能提问" description="在「智能问答」输入问题，AI 根据文档内容回答" />
      </el-steps>
    </el-card>

    <!-- 功能模块说明 -->
    <div class="module-grid">
      <!-- 智能问答 -->
      <el-card class="module-card" shadow="hover" @click="$router.push('/rag')">
        <div class="module-icon rag">
          <el-icon :size="32"><ChatDotRound /></el-icon>
        </div>
        <h3>智能问答</h3>
        <p>基于您上传的文档进行问答，AI 会优先从知识库中检索相关内容来回答。</p>
        <div class="module-tips">
          <el-tag size="small" type="success">知识库模式</el-tag>
          <span>根据文档内容精准回答</span>
        </div>
        <div class="module-tips">
          <el-tag size="small" type="info">LLM 直答模式</el-tag>
          <span>跳过知识库，使用 AI 通用知识回答</span>
        </div>
        <div class="module-tips">
          <el-tag size="small" type="warning">网络搜索</el-tag>
          <span>知识库未命中时自动联网查找</span>
        </div>
      </el-card>

      <!-- 文档管理 -->
      <el-card class="module-card" shadow="hover" @click="$router.push('/documents')">
        <div class="module-icon doc">
          <el-icon :size="32"><Document /></el-icon>
        </div>
        <h3>文档管理</h3>
        <p>上传和管理您的知识文档，支持多种格式。</p>
        <div class="module-tips">
          <el-tag size="small" type="warning">私有文档</el-tag>
          <span>仅自己可见，提问时只匹配自己的文档</span>
        </div>
        <div class="module-tips">
          <el-tag size="small" type="success">公开文档</el-tag>
          <span>所有用户可见，共享知识</span>
        </div>
        <div class="module-tips">
          <el-tag size="small">支持格式</el-tag>
          <span>PDF、Word、TXT、Markdown 等</span>
        </div>
      </el-card>

      <!-- 每日热榜 -->
      <el-card class="module-card" shadow="hover" @click="$router.push('/hot/dashboard')">
        <div class="module-icon hot">
          <el-icon :size="32"><TrendCharts /></el-icon>
        </div>
        <h3>每日热榜</h3>
        <p>自动聚合各平台热搜数据，一站式浏览。</p>
        <div class="module-tips">
          <el-tag size="small">覆盖平台</el-tag>
          <span>B站、微博、知乎、GitHub、抖音、小红书</span>
        </div>
        <div class="module-tips">
          <el-tag size="small" type="primary">智能关联</el-tag>
          <span>提问中提到平台名（如"B站推荐视频"），AI 自动查热榜</span>
        </div>
      </el-card>

      <!-- 个人中心 -->
      <el-card class="module-card" shadow="hover" @click="$router.push('/profile')">
        <div class="module-icon profile">
          <el-icon :size="32"><User /></el-icon>
        </div>
        <h3>个人中心</h3>
        <p>管理您的账号信息。</p>
        <div class="module-tips">
          <el-tag size="small">可修改</el-tag>
          <span>昵称、密码等个人信息</span>
        </div>
      </el-card>
    </div>

    <!-- 常见问题 -->
    <el-card class="guide-card faq-card" shadow="hover">
      <template #header>
        <div class="card-title">
          <el-icon :size="22" color="#E6A23C"><QuestionFilled /></el-icon>
          <span>常见问题</span>
        </div>
      </template>
      <el-collapse>
        <el-collapse-item title="上传文档后多久可以提问？" name="1">
          <p>通常几秒到几分钟不等，取决于文档大小。在“文档管理”页面可以看到解析进度，状态显示“完成”后即可提问。</p>
        </el-collapse-item>
        <el-collapse-item title="为什么 AI 回答了「仅供参考」？" name="2">
          <p>当知识库中没有找到相关内容时，AI 会使用自身的通用知识来回答，此时标签会显示“AI 通用知识 · 仅供参考”。建议上传更多相关文档提升回答准确度。</p>
        </el-collapse-item>
        <el-collapse-item title="知识库模式和 LLM 直答模式有什么区别？" name="3">
          <p><strong>知识库模式（默认）</strong>：AI 先从您的文档中检索相关段落，再基于这些内容回答，更准确可靠。<br/>
             <strong>LLM 直答模式</strong>：跳过文档检索，直接用 AI 的训练知识回答，适合通用问题，但可能存在“幻觉”。</p>
        </el-collapse-item>
        <el-collapse-item title="私有文档和公开文档有什么区别？" name="4">
          <p><strong>私有文档</strong>：只有上传者本人提问时会被检索到，其他用户看不到也搜不到。<br/>
             <strong>公开文档</strong>：所有用户提问时都可能匹配到，适合团队共享的公共知识。</p>
        </el-collapse-item>
        <el-collapse-item title="怎么查看热搜/热榜？" name="5">
          <p>两种方式：<br/>
             1. 打开“每日热榜”页面直接浏览各平台热搜。<br/>
             2. 在“智能问答”中直接提问，例如“微博热搜”、“B站推荐视频”，AI 会自动从热榜数据中回答。</p>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {
  Promotion,
  ChatDotRound,
  Document,
  TrendCharts,
  User,
  QuestionFilled,
} from '@element-plus/icons-vue'
</script>

<style scoped>
.guide-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px;
}

.guide-header {
  margin-bottom: 24px;
}

.guide-header h2 {
  font-size: 24px;
  margin: 0;
}

.guide-header .subtitle {
  color: #909399;
  margin-top: 8px;
}

.guide-card {
  margin-bottom: 24px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.guide-steps {
  padding: 20px 0 10px;
}

/* 模块网格 */
.module-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

@media (max-width: 768px) {
  .module-grid {
    grid-template-columns: 1fr;
  }
}

.module-card {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.module-card:hover {
  transform: translateY(-2px);
}

.module-card h3 {
  margin: 12px 0 8px;
  font-size: 16px;
}

.module-card > :deep(.el-card__body) > p {
  color: #606266;
  font-size: 14px;
  margin: 0 0 12px;
  line-height: 1.6;
}

.module-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.module-icon.rag {
  background: linear-gradient(135deg, #409EFF, #66b1ff);
}

.module-icon.doc {
  background: linear-gradient(135deg, #67C23A, #85ce61);
}

.module-icon.hot {
  background: linear-gradient(135deg, #F56C6C, #f89898);
}

.module-icon.profile {
  background: linear-gradient(135deg, #909399, #b1b3b8);
}

.module-tips {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}

/* FAQ */
.faq-card :deep(.el-collapse-item__header) {
  font-weight: 500;
}

.faq-card p {
  color: #606266;
  line-height: 1.8;
  margin: 0;
}
</style>

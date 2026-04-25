<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Document, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)

function handleSelect(index: string) {
  router.push(index)
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-icon">🤖</span>
        <span class="logo-text">RAG 知识助手</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="menu"
        background-color="#001529"
        text-color="#fff"
        active-text-color="#409eff"
        @select="handleSelect"
      >
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>知识问答</span>
        </el-menu-item>
        <el-menu-item index="/kb">
          <el-icon><Document /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
      </el-menu>
      <div class="user-info">
        <el-icon><User /></el-icon>
        <span>未登录</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ route.meta.title || '首页' }}</span>
      </el-header>
      <el-main class="main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped lang="scss">
.layout {
  height: 100vh;
}

.aside {
  background: #001529;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #1f2d3d;

  .logo-icon { font-size: 22px; }
}

.menu {
  flex: 1;
  border-right: none;
}

.user-info {
  padding: 16px;
  color: #cfd3dc;
  display: flex;
  align-items: center;
  gap: 8px;
  border-top: 1px solid #1f2d3d;
  font-size: 13px;
}

.header {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  padding: 0 24px;

  .page-title {
    font-size: 18px;
    font-weight: 600;
  }
}

.main {
  padding: 0;
  background: var(--bg-page);
  overflow: hidden;
}
</style>

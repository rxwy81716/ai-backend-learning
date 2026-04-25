<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)

/**
 * 登录占位实现
 *
 * Day29 会接入真实的：
 *   - JWT Token
 *   - Spring Security
 *   - Pinia 用户状态管理
 *   - 路由守卫
 */
async function handleLogin() {
  if (!username.value || !password.value) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  // TODO: Day29 实现真实登录
  await new Promise((r) => setTimeout(r, 500))
  ElMessage.success('登录成功（演示模式）')
  loading.value = false
  router.push('/')
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="logo">🤖</div>
      <h2 class="title">Spring AI RAG 知识助手</h2>
      <p class="subtitle">企业级知识问答系统</p>

      <el-form @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          @click="handleLogin"
          style="width: 100%"
        >
          登 录
        </el-button>
      </el-form>

      <div class="tip">📌 当前为演示模式，输入任意账号密码即可</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.2);
  text-align: center;

  .logo { font-size: 56px; margin-bottom: 16px; }
  .title { margin: 0 0 8px; font-size: 22px; color: #303133; }
  .subtitle { margin: 0 0 32px; color: #909399; font-size: 14px; }
  .tip { margin-top: 16px; font-size: 12px; color: #909399; }
}
</style>

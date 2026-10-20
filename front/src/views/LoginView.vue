<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Sparkles } from '@lucide/vue'
import { api, saveAuth } from '../api'

const router = useRouter()
const username = ref('')
const password = ref('')
const errorMessage = ref('')
const loading = ref(false)

async function submit() {
  if (loading.value) return
  errorMessage.value = ''
  if (!username.value.trim() || !password.value) {
    errorMessage.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const result = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: username.value.trim(), password: password.value }),
    })
    saveAuth(result.token, result.user)
    // 按角色跳转：管理员进后台，普通用户进聊天页
    router.push(result.user.role === 'admin' ? '/admin' : '/chat')
  } catch (error) {
    // 封禁账号给出明确提示，其余显示后端返回的 message
    errorMessage.value = error.status === 403 ? '账号已被封禁，请联系管理员' : error.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <form class="auth-card" @submit.prevent="submit">
      <div class="brand">
        <span class="brand-mark"><Sparkles :size="20" :stroke-width="2.2" /></span>
        <span class="brand-name">拾光</span>
      </div>
      <h1>登录</h1>
      <p class="subtitle">欢迎回来，继续你的 AI 对话</p>

      <label class="field">
        <span>用户名</span>
        <input v-model="username" type="text" placeholder="请输入用户名" autocomplete="username" />
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
      </label>

      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

      <button class="submit" type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>

      <p class="switch">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </form>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7f7f5;
  padding: 24px;
}
.auth-card {
  width: 100%;
  max-width: 380px;
  background: #fff;
  border: 1px solid #e8e8e4;
  border-radius: 16px;
  padding: 36px 32px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #1a1a18;
  margin-bottom: 20px;
}
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: #1a1a18;
  color: #fff;
}
.brand-name { font-size: 18px; font-weight: 600; }
h1 { font-size: 22px; margin: 0 0 6px; color: #1a1a18; }
.subtitle { font-size: 13px; color: #8a8a85; margin: 0 0 24px; }
.field { display: block; margin-bottom: 16px; }
.field span { display: block; font-size: 13px; color: #55554f; margin-bottom: 6px; }
.field input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #ddddd8;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
}
.field input:focus { border-color: #1a1a18; }
.error { font-size: 13px; color: #c0392b; margin: 0 0 14px; }
.submit {
  width: 100%;
  padding: 11px;
  border: none;
  border-radius: 10px;
  background: #1a1a18;
  color: #fff;
  font-size: 15px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.submit:disabled { opacity: 0.6; cursor: not-allowed; }
.switch { font-size: 13px; color: #8a8a85; text-align: center; margin: 18px 0 0; }
.switch a { color: #1a1a18; font-weight: 600; text-decoration: none; }
.switch a:hover { text-decoration: underline; }
</style>

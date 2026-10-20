<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Sparkles } from '@lucide/vue'
import { api, saveAuth } from '../api'

const router = useRouter()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const errorMessage = ref('')
const loading = ref(false)

// 与后端 RegisterRequest 一致的密码规则：6-12 位，必须同时含字母和数字
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,12}$/

async function submit() {
  if (loading.value) return
  errorMessage.value = ''
  const name = username.value.trim()
  if (!name) {
    errorMessage.value = '请输入用户名'
    return
  }
  if (!PASSWORD_PATTERN.test(password.value)) {
    errorMessage.value = '密码需为 6-12 位，且同时包含字母和数字'
    return
  }
  if (password.value !== confirmPassword.value) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await api('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username: name, password: password.value }),
    })
    // 注册成功后自动登录，直接进入聊天页
    const login = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: name, password: password.value }),
    })
    saveAuth(login.token, login.user)
    router.push('/chat')
  } catch (error) {
    errorMessage.value = error.message
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
      <h1>注册</h1>
      <p class="subtitle">创建账号，开始你的 AI 对话</p>

      <label class="field">
        <span>用户名</span>
        <input v-model="username" type="text" placeholder="请输入用户名" autocomplete="username" />
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" placeholder="6-12 位，含字母和数字" autocomplete="new-password" />
      </label>
      <label class="field">
        <span>确认密码</span>
        <input v-model="confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" />
      </label>

      <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

      <button class="submit" type="submit" :disabled="loading">{{ loading ? '注册中…' : '注册并登录' }}</button>

      <p class="switch">已有账号？<router-link to="/login">去登录</router-link></p>
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

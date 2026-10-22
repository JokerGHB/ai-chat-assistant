<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowUp, BarChart3, CircleHelp, Command, Copy, Ellipsis, FileText, LogOut,
  Menu, MessageSquare, PanelLeftClose, Plus, RefreshCw, Search, Settings2,
  Sparkles, SquarePen, Sun, Trash2, X
} from '@lucide/vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { api, clearAuth, getUser } from '../api'

marked.setOptions({ breaks: true, gfm: true })

// 说明：api 统一走相对路径 /api（vite 代理到后端），并自动携带登录令牌

const sessions = ref([])
const activeId = ref(null)
const input = ref('')
const searchText = ref('')
const sidebarOpen = ref(true)
const showSettings = ref(false)
const showHelp = ref(false)
const settingsTab = ref('workspace') // 'workspace' | 'account'
const sending = ref(false)
const attachedFiles = ref([])
const toastMessage = ref('')
const chatBody = ref(null)
const searchInput = ref(null)
const fileInput = ref(null)
let toastTimer
const router = useRouter()
// 当前登录用户（先从 localStorage 取，进入页面后再用 profile 接口刷新）
const currentUser = ref(getUser())
// 当日剩余对话次数（来自 /api/auth/profile）
const remainingToday = ref(null)
// 头像字母：取用户名首字符
const avatarLetter = computed(() => (currentUser.value?.username || '我').slice(0, 1).toUpperCase())
const filteredSessions = computed(() => sessions.value.filter(item => item.title.toLowerCase().includes(searchText.value.toLowerCase())))
const activeSession = computed(() => sessions.value.find(item => item.id === activeId.value))
const isEmpty = computed(() => !activeSession.value || activeSession.value.messages.length === 0)

function mapSession(row) {
  return { ...row, time: '本地数据库', messages: [] }
}

async function loadSessions(preferredId = null) {
  try {
    sessions.value = (await api('/api/sessions')).map(mapSession)
    const target = sessions.value.find(item => item.id === preferredId)
      || sessions.value.find(item => item.id === activeId.value)
      || sessions.value[0]
    activeId.value = target?.id ?? null
    if (target) await loadMessages(target.id)
  } catch (error) {
    showToast(`连接后端失败：${error.message}`)
  }
}

async function loadMessages(sessionId) {
  const session = sessions.value.find(item => item.id === sessionId)
  if (!session) return
  try {
    session.messages = await api(`/api/sessions/${sessionId}/messages`)
  } catch (error) {
    showToast(`读取消息失败：${error.message}`)
  }
}

function showToast(message) {
  toastMessage.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastMessage.value = '' }, 2200)
}

/** 拉取个人资料：刷新用户名与当日剩余对话次数 */
async function loadProfile() {
  try {
    const profile = await api('/api/auth/profile')
    currentUser.value = profile
    remainingToday.value = profile.remainingToday
  } catch {
    // 401 会由 api 封装统一跳转登录页，这里无需处理
  }
}

/** 退出登录：通知后端删除令牌（即使失败也强制清理本地登录态） */
async function logout() {
  try {
    await api('/api/auth/logout', { method: 'POST' })
  } catch {
    // 忽略登出接口异常，本地状态必须清理
  }
  clearAuth()
  router.push('/login')
}

// ========== 账号设置 ==========
const editUsername = ref('')
const editOldPassword = ref('')
const editNewPassword = ref('')
const editConfirmPassword = ref('')

async function submitUsername() {
  const name = editUsername.value.trim()
  if (!name) return showToast('新用户名不能为空')
  if (name === currentUser.value?.username) { showToast('新用户名与当前相同'); return }
  try {
    await api('/api/auth/username', { method: 'PATCH', body: JSON.stringify({ username: name }) })
    showToast('用户名已修改，请重新登录')
    // 修改用户名后后端会删除所有令牌强制重登
    await logout()
  } catch (error) {
    showToast(`修改失败：${error.message}`)
  }
}

async function submitPassword() {
  if (!editOldPassword.value) return showToast('请输入旧密码')
  if (!editNewPassword.value) return showToast('请输入新密码')
  if (editNewPassword.value !== editConfirmPassword.value) return showToast('两次输入的新密码不一致')
  try {
    await api('/api/auth/password', {
      method: 'PATCH',
      body: JSON.stringify({ oldPassword: editOldPassword.value, newPassword: editNewPassword.value })
    })
    showToast('密码已修改，请重新登录')
    editOldPassword.value = editNewPassword.value = editConfirmPassword.value = ''
    await logout()
  } catch (error) {
    showToast(`修改失败：${error.message}`)
  }
}

function focusComposer() {
  nextTick(() => document.querySelector('.composer textarea')?.focus())
}

function closeSidebarOnMobile() {
  if (window.innerWidth <= 560) sidebarOpen.value = false
}

async function newChat() {
  try {
    const created = await api('/api/sessions', { method: 'POST', body: JSON.stringify({ title: '新对话' }) })
    sessions.value.unshift(mapSession(created))
    activeId.value = created.id
  } catch (error) {
    showToast(`创建会话失败：${error.message}`)
    return
  }
  input.value = ''
  attachedFiles.value = []
  closeSidebarOnMobile()
  focusComposer()
}

async function selectSession(id) {
  activeId.value = id
  await loadMessages(id)
  closeSidebarOnMobile()
}

async function removeSession(id) {
  // 逻辑删除：删除前提示数据保留策略
  if (!window.confirm('删除后 90 天内数据保留，之后自动清除。确定删除该对话吗？')) return
  try {
    await api(`/api/sessions/${id}`, { method: 'DELETE' })
    sessions.value = sessions.value.filter(item => item.id !== id)
    if (activeId.value === id) {
      activeId.value = sessions.value[0]?.id ?? null
      if (activeId.value) await loadMessages(activeId.value)
    }
  } catch (error) {
    showToast(`删除会话失败：${error.message}`)
  }
}

async function sendMessage() {
  const content = input.value.trim()
  const files = attachedFiles.value.map(file => ({ ...file }))
  if ((!content && !files.length) || sending.value) return
  if (files.length) {
    showToast('附件内容尚未接入后端，请先发送纯文本消息')
    return
  }
  if (!activeSession.value) await newChat()
  const session = activeSession.value
  if (!session) return
  const pendingMessage = { role: 'user', content, pending: true }
  session.messages.push(pendingMessage)
  const title = content || files.map(file => file.name).join('、')
  if (session.title === '新对话') session.title = title.length > 24 ? `${title.slice(0, 24)}…` : title
  session.time = '刚刚'
  input.value = ''
  attachedFiles.value = []
  nextTick(() => {
    const textarea = document.querySelector('.composer textarea')
    if (textarea) textarea.style.height = 'auto'
  })
  sending.value = true
  scrollToBottom()
  try {
    const result = await api(`/api/sessions/${session.id}/messages`, {
      method: 'POST', body: JSON.stringify({ content }),
    })
    const pendingIndex = session.messages.indexOf(pendingMessage)
    if (pendingIndex >= 0) session.messages.splice(pendingIndex, 1, result.userMessage)
    session.messages.push(result.assistantMessage)
    const refreshed = await api('/api/sessions')
    sessions.value = refreshed.map(row => {
      const existing = sessions.value.find(item => item.id === row.id)
      return { ...mapSession(row), messages: existing?.messages || [] }
    })
    // 发送成功后刷新当日剩余次数
    loadProfile()
  } catch (error) {
    pendingMessage.pending = false
    // 敏感词(400)/每日超限(429)：直接展示后端返回的提示文案
    if (error.status === 400 || error.status === 429) {
      showToast(error.message)
    } else {
      showToast(`发送失败：${error.message}`)
    }
    await loadMessages(session.id)
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function regenerateResponse(messageIndex) {
  if (!activeSession.value || sending.value) return
  const session = activeSession.value
  let userIndex = messageIndex - 1
  while (userIndex >= 0 && session.messages[userIndex].role !== 'user') userIndex -= 1
  if (userIndex < 0) return
  const source = session.messages[userIndex]
  session.messages.splice(messageIndex, 1)
  sending.value = true
  scrollToBottom()
  api(`/api/sessions/${session.id}/messages`, { method: 'POST', body: JSON.stringify({ content: source.content }) })
    .then(result => { session.messages.splice(messageIndex, 0, result.assistantMessage); loadProfile() })
    .catch(error => showToast(`重新生成失败：${error.message}`))
    .finally(() => { sending.value = false; scrollToBottom() })
}

async function copyMessage(content) {
  try {
    await navigator.clipboard.writeText(content)
    showToast('回复已复制')
  } catch {
    showToast('浏览器未允许复制，请手动选择文字')
  }
}

function pickFiles() {
  fileInput.value?.click()
}

function handleFiles(event) {
  const selected = Array.from(event.target.files || []).map(file => ({
    name: file.name,
    size: file.size,
  }))
  attachedFiles.value.push(...selected)
  event.target.value = ''
}

function removeAttachment(index) {
  attachedFiles.value.splice(index, 1)
}

function formatFileSize(size) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(0)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

function scrollToBottom() {
  nextTick(() => { if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight })
}

function onComposerKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

function renderMarkdown(content) {
  return DOMPurify.sanitize(marked.parse(content))
}

function resizeComposer(event) {
  const textarea = event.target
  textarea.style.height = 'auto'
  textarea.style.height = `${Math.min(textarea.scrollHeight, 180)}px`
}

function onGlobalKeydown(event) {
  const modifier = event.metaKey || event.ctrlKey
  if (modifier && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    newChat()
  } else if (modifier && event.key.toLowerCase() === 'f') {
    event.preventDefault()
    searchInput.value?.focus()
  } else if (event.key === 'Escape') {
    showSettings.value = false
    showHelp.value = false
    if (window.innerWidth <= 560) sidebarOpen.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
  loadSessions()
  loadProfile()
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
  window.clearTimeout(toastTimer)
})

watch(activeId, () => nextTick(() => { if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight }))
</script>

<template>
  <main class="app-shell">
    <button v-if="sidebarOpen" class="sidebar-scrim" aria-label="关闭侧边栏" @click="sidebarOpen = false"></button>
    <aside v-if="sidebarOpen" class="sidebar">
      <div class="sidebar-top">
        <a class="brand" href="#" @click.prevent="newChat">
          <span class="brand-mark"><Sparkles :size="17" :stroke-width="2.2" /></span>
          <span>拾光</span>
        </a>
        <button class="icon-button subtle" aria-label="收起侧边栏" @click="sidebarOpen = false"><PanelLeftClose :size="18" /></button>
      </div>

      <div v-if="currentUser?.role === 'admin'" class="sidebar-admin-entry">
        <button class="admin-entry-btn" @click="router.push('/admin')">
          <BarChart3 :size="15" /><span>管理后台</span>
        </button>
      </div>

      <div class="sidebar-actions">
        <button class="new-chat-btn" @click="newChat"><SquarePen :size="16" /><span>新建对话</span><kbd>Ctrl K</kbd></button>
        <label class="search-box"><Search :size="16" /><input ref="searchInput" v-model="searchText" placeholder="搜索对话" /><kbd>Ctrl F</kbd></label>
      </div>

      <div class="history-label">最近</div>
      <nav class="session-list" aria-label="历史对话">
        <div v-for="item in filteredSessions" :key="item.id" class="session-row" :class="{ active: activeId === item.id }">
          <button class="session-select" @click="selectSession(item.id)"><MessageSquare :size="15" /><span>{{ item.title }}</span></button>
          <button class="session-delete" aria-label="删除对话" title="删除对话" @click="removeSession(item.id)"><Trash2 :size="14" /></button>
        </div>
        <p v-if="!filteredSessions.length" class="no-results">没有找到相关对话</p>
      </nav>

      <div class="sidebar-bottom">
        <div class="profile-row-wrap">
          <button class="profile-row" @click="showSettings = true">
            <span class="avatar">{{ avatarLetter }}</span><span class="profile-copy"><strong>{{ currentUser?.username || '未登录' }}</strong><small>{{ currentUser?.role === 'admin' ? '管理员' : '普通用户' }}</small></span><Ellipsis :size="18" />
          </button>
          <button class="icon-button logout-button" aria-label="退出登录" title="退出登录" @click="logout"><LogOut :size="16" /></button>
        </div>
      </div>
    </aside>

    <section class="main-panel">
      <header class="topbar">
        <div class="topbar-left">
          <button v-if="!sidebarOpen" class="icon-button" aria-label="展开侧边栏" @click="sidebarOpen = true"><Menu :size="19" /></button>
          <span class="context-title">对话</span><span class="crumb-divider">/</span>
          <span class="model-static"><span class="model-status"></span>DeepSeek Flash</span>
        </div>
        <div class="topbar-right">
          <span v-if="remainingToday !== null" class="remaining-badge" title="今日剩余对话次数">今日剩余 {{ remainingToday }} 次</span>
          <button class="share-button" @click="showSettings = true"><Settings2 :size="15" /> 设置</button>
          <button class="icon-button help-button" aria-label="帮助" title="帮助" @click="showHelp = true"><CircleHelp :size="18" /></button>
        </div>
      </header>

      <div ref="chatBody" class="chat-scroll">
        <div v-if="isEmpty" class="welcome-wrap">
          <div class="welcome-icon"><Sparkles :size="23" /></div>
          <p class="eyebrow">你的 AI 助手</p>
          <h1>有什么想法，<span>一起聊聊。</span></h1>
          <p class="welcome-subtitle">从一个问题开始，让灵感慢慢展开。</p>
          <div class="suggestion-grid">
            <button class="suggestion-card" @click="input = '帮我制定一个高效的每周学习计划'">
              <span class="suggestion-icon lavender"><Command :size="17" /></span><span><strong>制定学习计划</strong><small>把目标拆解成可执行的小步骤</small></span><ArrowUp :size="15" />
            </button>
            <button class="suggestion-card" @click="input = '用通俗的方式解释一个复杂概念'">
              <span class="suggestion-icon peach"><Sparkles :size="17" /></span><span><strong>解释复杂概念</strong><small>把难懂的内容讲得清晰易懂</small></span><ArrowUp :size="15" />
            </button>
            <button class="suggestion-card" @click="input = '帮我写一封简洁又友好的邮件'">
              <span class="suggestion-icon mint"><MessageSquare :size="17" /></span><span><strong>写一封邮件</strong><small>整理思路，找到合适的表达</small></span><ArrowUp :size="15" />
            </button>
            <button class="suggestion-card" @click="input = '给我推荐几本适合周末阅读的书'">
              <span class="suggestion-icon yellow"><Sun :size="17" /></span><span><strong>发现新灵感</strong><small>聊聊阅读、旅行或新的爱好</small></span><ArrowUp :size="15" />
            </button>
          </div>
        </div>

        <div v-else class="messages-wrap">
          <article v-for="(message, index) in activeSession.messages" :key="`${message.role}-${index}`" class="message" :class="[message.role, { failed: message.status === 'failed' }]">
            <div v-if="message.role === 'assistant'" class="assistant-avatar"><Sparkles :size="15" /></div>
            <div v-else class="user-avatar">{{ avatarLetter }}</div>
            <div class="message-content">
              <div class="message-author">{{ message.role === 'user' ? '你' : '拾光' }}</div>
              <div v-if="message.role === 'assistant'" class="message-text markdown-body" v-html="renderMarkdown(message.content)"></div>
              <div v-else class="message-text">{{ message.content }}</div>
              <p v-if="message.status === 'failed'" class="failed-hint">回复生成失败{{ message.errorMessage ? `：${message.errorMessage}` : '' }}，可点击"重新生成"重试</p>
              <div v-if="message.attachments?.length" class="message-attachments">
                <span v-for="file in message.attachments" :key="file.name" class="message-attachment"><FileText :size="14" />{{ file.name }}</span>
              </div>
              <div v-if="message.role === 'assistant'" class="message-tools">
                <button title="重新生成回复" @click="regenerateResponse(index)"><RefreshCw :size="13" /> 重新生成</button>
                <button title="复制回复" @click="copyMessage(message.content)"><Copy :size="14" /> 复制</button>
              </div>
            </div>
          </article>
          <article v-if="sending" class="message assistant"><div class="assistant-avatar"><Sparkles :size="15" /></div><div class="message-content"><div class="message-author">拾光</div><div class="typing"><i></i><i></i><i></i><span>正在思考</span></div></div></article>
          <div class="end-spacer"></div>
        </div>
      </div>

      <footer class="composer-area">
        <div class="composer">
          <div v-if="attachedFiles.length" class="attachment-list">
            <div v-for="(file, index) in attachedFiles" :key="`${file.name}-${index}`" class="attachment-chip">
              <FileText :size="14" /><span><strong>{{ file.name }}</strong><small>{{ formatFileSize(file.size) }}</small></span>
              <button :aria-label="`移除 ${file.name}`" @click="removeAttachment(index)"><X :size="13" /></button>
            </div>
          </div>
          <textarea v-model="input" rows="1" placeholder="发送消息，开始一段对话…" @keydown="onComposerKeydown" @input="resizeComposer"></textarea>
          <div class="composer-bottom">
            <div class="composer-tools"><button class="tool-button" title="添加附件" aria-label="添加附件" @click="pickFiles"><Plus :size="17" /></button><span class="composer-hint">AI 可能会出错，请核对重要信息</span></div>
            <button class="send-button" :class="{ ready: input.trim() || attachedFiles.length }" :disabled="(!input.trim() && !attachedFiles.length) || sending" aria-label="发送消息" @click="sendMessage"><ArrowUp :size="17" /></button>
          </div>
          <input ref="fileInput" class="file-input" type="file" multiple @change="handleFiles" />
        </div>
        <div class="keyboard-hint"><kbd>Enter</kbd> 发送 <span>·</span> <kbd>Shift + Enter</kbd> 换行</div>
      </footer>
    </section>

    <div v-if="showSettings" class="modal-backdrop" @click.self="showSettings = false">
      <section class="settings-modal">
        <div class="modal-heading"><div><p class="eyebrow">偏好设置</p><h2>工作区设置</h2></div><button class="icon-button" @click="showSettings = false"><X :size="18" /></button></div>

        <!-- tab 切换 -->
        <div class="modal-tabs">
          <button class="modal-tab" :class="{ active: settingsTab === 'workspace' }" @click="settingsTab = 'workspace'">工作区</button>
          <button class="modal-tab" :class="{ active: settingsTab === 'account' }" @click="settingsTab = 'account'">账号设置</button>
        </div>

        <!-- 工作区 tab -->
        <div v-if="settingsTab === 'workspace'" class="modal-tab-body">
          <div class="setting-item"><div><strong>当前模型</strong><small>通过后端调用国产 DeepSeek 模型</small></div><span class="setting-value">DeepSeek Flash</span></div>
          <div class="setting-item"><div><strong>数据存储</strong><small>会话和消息保存在本地 MySQL 数据库</small></div><span class="local-badge">本机</span></div>
          <div class="modal-note"><Sparkles :size="16" /> 聊天请求经过本机后端发送到 DeepSeek，密钥不会发送到浏览器。</div>
        </div>

        <!-- 账号设置 tab -->
        <div v-else class="modal-tab-body">
          <!-- 修改用户名 -->
          <div class="account-form-block">
            <strong class="account-block-title">修改用户名</strong>
            <small class="account-block-desc">修改后需重新登录</small>
            <div class="account-row">
              <span class="account-label">当前</span>
              <span class="account-value">{{ currentUser?.username }}</span>
            </div>
            <div class="account-row">
              <span class="account-label">新用户名</span>
              <input v-model="editUsername" type="text" maxlength="50" class="account-input" placeholder="2-50 个字符" />
            </div>
            <button class="modal-submit" :disabled="!editUsername.trim() || editUsername.trim() === currentUser?.username" @click="submitUsername">更新用户名</button>
          </div>

          <div class="account-divider"></div>

          <!-- 修改密码 -->
          <div class="account-form-block">
            <strong class="account-block-title">修改密码</strong>
            <small class="account-block-desc">6-12 位，须同时包含字母和数字</small>
            <div class="account-row">
              <span class="account-label">旧密码</span>
              <input v-model="editOldPassword" type="password" class="account-input" placeholder="输入当前密码" />
            </div>
            <div class="account-row">
              <span class="account-label">新密码</span>
              <input v-model="editNewPassword" type="password" class="account-input" placeholder="6-12 位，字母+数字" />
            </div>
            <div class="account-row">
              <span class="account-label">确认新密码</span>
              <input v-model="editConfirmPassword" type="password" class="account-input" placeholder="再次输入新密码" />
            </div>
            <button class="modal-submit" :disabled="!editOldPassword || !editNewPassword || !editConfirmPassword" @click="submitPassword">更新密码</button>
          </div>
        </div>

        <button class="modal-done" @click="showSettings = false">完成</button>
      </section>
    </div>
    <div v-if="showHelp" class="modal-backdrop" @click.self="showHelp = false">
      <section class="settings-modal help-modal">
        <div class="modal-heading"><div><p class="eyebrow">快速上手</p><h2>如何使用拾光</h2></div><button class="icon-button" aria-label="关闭" @click="showHelp = false"><X :size="18" /></button></div>
        <div class="help-list">
          <p><strong>开始聊天</strong><span>输入问题后按 Enter 发送，Shift + Enter 换行。</span></p>
          <p><strong>管理对话</strong><span>从左侧搜索、切换或删除保存在本地数据库的会话。</span></p>
          <p><strong>添加附件</strong><span>附件内容目前还未接入模型，请使用文本消息聊天。</span></p>
        </div>
        <div class="modal-note"><Sparkles :size="16" /> 回复由 DeepSeek 生成；重要信息请自行核实。</div>
        <button class="modal-done" @click="showHelp = false">知道了</button>
      </section>
    </div>
    <Transition name="toast"><div v-if="toastMessage" class="toast-message" role="status">{{ toastMessage }}</div></Transition>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import {
  BarChart3, ClipboardList, LogOut, MessageSquare, ScrollText, Sparkles, Users, X
} from '@lucide/vue'
import { api, clearAuth, getUser } from '../api'

const router = useRouter()
// 当前管理员（用于顶栏展示，先从本地登录态读取）
const currentUser = ref(getUser())
// 当前激活的子页：users-用户管理 sessions-对话管理 stats-统计看板 logs-操作日志
const activeTab = ref('users')

const tabs = [
  { key: 'users', label: '用户管理', icon: Users },
  { key: 'sessions', label: '对话管理', icon: MessageSquare },
  { key: 'stats', label: '统计看板', icon: BarChart3 },
  { key: 'logs', label: '操作日志', icon: ScrollText },
]

// ========== 通用：轻提示与时间格式化 ==========
const toastMessage = ref('')
let toastTimer
function showToast(message) {
  toastMessage.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toastMessage.value = '' }, 2400)
}

/** 后端 LocalDateTime 序列化为 ISO 字符串，这里统一格式化为「年-月-日 时:分」 */
function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 文本截断，用于表格与图表标签 */
function truncate(text, len) {
  const value = text || ''
  return value.length > len ? `${value.slice(0, len)}…` : value
}

const roleLabel = (role) => (role === 'admin' ? '管理员' : '普通用户')
const statusLabel = (status) => (status === 'banned' ? '已封禁' : '正常')
const messageRoleLabel = (role) => ({ user: '用户', assistant: 'AI 回复', system: '系统' }[role] || role)

// 操作日志 action 值 → 中文说明
const ACTION_LABELS = {
  LOGIN: '登录', REGISTER: '注册', LOGOUT: '登出', CHANGE_PASSWORD: '修改密码',
  CHANGE_USERNAME: '修改用户名',
  BAN_USER: '封禁用户', UNBAN_USER: '解封用户',
  DELETE_SESSION: '删除会话', ADMIN_DELETE_SESSION: '管理员删除会话', PURGE_SESSION: '过期会话清理',
}
const actionLabel = (action) => ACTION_LABELS[action] || action

// ========== 1. 用户管理 ==========
const users = ref([])
const usersLoading = ref(false)

async function loadUsers() {
  usersLoading.value = true
  try {
    users.value = await api('/api/admin/users')
  } catch (error) {
    showToast(`加载用户失败：${error.message}`)
  } finally {
    usersLoading.value = false
  }
}

async function toggleBan(user) {
  const banning = user.status !== 'banned'
  if (banning && !window.confirm(`确定封禁「${user.username}」？封禁后其登录状态立即失效。`)) return
  try {
    await api(`/api/admin/users/${user.id}/${banning ? 'ban' : 'unban'}`, { method: 'POST' })
    showToast(banning ? '已封禁' : '已解封')
    await loadUsers()
  } catch (error) {
    showToast(`操作失败：${error.message}`)
  }
}

// ========== 2. 对话管理 ==========
const sessions = ref([])
const sessionsLoading = ref(false)
const sessionKeyword = ref('')

const filteredSessions = computed(() => {
  const keyword = sessionKeyword.value.trim()
  if (!keyword) return sessions.value
  return sessions.value.filter(item =>
    (item.username || '').includes(keyword) || (item.title || '').includes(keyword))
})

async function loadSessions() {
  sessionsLoading.value = true
  try {
    sessions.value = await api('/api/admin/sessions')
  } catch (error) {
    showToast(`加载会话失败：${error.message}`)
  } finally {
    sessionsLoading.value = false
  }
}

// 查看消息弹窗
const inspected = ref(null) // { session, messages, loading }
async function inspectSession(session) {
  inspected.value = { session, messages: [], loading: true }
  try {
    inspected.value.messages = await api(`/api/admin/sessions/${session.id}/messages`)
  } catch (error) {
    showToast(`读取消息失败：${error.message}`)
  } finally {
    inspected.value.loading = false
  }
}

async function physicalDelete(session) {
  if (!window.confirm(`物理删除「${truncate(session.title, 20)}」后数据不可恢复，确定继续？`)) return
  try {
    await api(`/api/admin/sessions/${session.id}`, { method: 'DELETE' })
    showToast('已物理删除')
    await loadSessions()
  } catch (error) {
    showToast(`删除失败：${error.message}`)
  }
}

// ========== 3. 统计看板 ==========
const range = ref('7d')
const overview = ref(null)
const topQuestions = ref([])
const unresolved = ref([])
const statsLoading = ref(false)
const chartRef = ref(null)
let chartInstance = null

async function loadStats() {
  statsLoading.value = true
  try {
    const query = `range=${range.value}`
    const [overviewData, topData, unresolvedData] = await Promise.all([
      api(`/api/admin/stats/overview?${query}`),
      api(`/api/admin/stats/top-questions?${query}&limit=10`),
      api(`/api/admin/stats/unresolved?${query}&limit=50`),
    ])
    overview.value = overviewData
    topQuestions.value = topData
    unresolved.value = unresolvedData
    await nextTick()
    renderChart()
  } catch (error) {
    showToast(`加载统计失败：${error.message}`)
  } finally {
    statsLoading.value = false
  }
}

/** 渲染高频提问横向柱状图（横向更利于阅读中文长句） */
function renderChart() {
  if (!chartRef.value) return
  if (!chartInstance) chartInstance = echarts.init(chartRef.value)
  // echarts 类目轴自下而上排列，反转后 Top1 显示在最上方
  const data = [...topQuestions.value].reverse()
  chartInstance.setOption({
    grid: { left: 8, right: 26, top: 12, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f2f1f4' } },
      axisLabel: { color: '#9a97a0', fontSize: 10 },
      axisLine: { show: false },
    },
    yAxis: {
      type: 'category',
      data: data.map(item => truncate(item.content, 16)),
      axisLabel: { color: '#55525e', fontSize: 10 },
      axisLine: { lineStyle: { color: '#eeedf1' } },
      axisTick: { show: false },
    },
    series: [{
      type: 'bar',
      data: data.map(item => item.cnt),
      barWidth: 12,
      itemStyle: { color: '#7869d4', borderRadius: [0, 6, 6, 0] },
      label: { show: true, position: 'right', color: '#8b8794', fontSize: 10 },
    }],
  })
  chartInstance.resize()
}

function onWindowResize() {
  chartInstance?.resize()
}

// ========== 4. 操作日志 ==========
const logs = ref([])
const logsLoading = ref(false)
async function loadLogs() {
  logsLoading.value = true
  try {
    logs.value = await api('/api/admin/logs?limit=100')
  } catch (error) {
    showToast(`加载日志失败：${error.message}`)
  } finally {
    logsLoading.value = false
  }
}

// ========== 生命周期与交互 ==========
/** 首次切换到某子页时才加载数据，避免一次拉取全部接口 */
function ensureTabLoaded(tab) {
  if (tab === 'users' && !users.value.length) loadUsers()
  else if (tab === 'sessions' && !sessions.value.length) loadSessions()
  else if (tab === 'stats' && !overview.value) loadStats()
  else if (tab === 'stats') nextTick(renderChart) // 返回看板时补一次 resize
  else if (tab === 'logs' && !logs.value.length) loadLogs()
}

watch(activeTab, (tab) => ensureTabLoaded(tab))

async function logout() {
  try {
    await api('/api/auth/logout', { method: 'POST' })
  } catch {
    // 忽略登出异常，本地状态必须清理
  }
  clearAuth()
  router.push('/login')
}

onMounted(() => {
  window.addEventListener('resize', onWindowResize)
  loadUsers()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResize)
  window.clearTimeout(toastTimer)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<template>
  <main class="admin-shell">
    <header class="admin-topbar">
      <div class="admin-brand">
        <span class="brand-mark"><Sparkles :size="17" :stroke-width="2.2" /></span>
        <span class="brand-name">拾光</span>
        <span class="brand-tag">管理后台</span>
      </div>
      <div class="admin-topbar-right">
        <span class="admin-user">{{ currentUser?.username || '管理员' }}</span>
        <button class="admin-ghost-btn" @click="router.push('/chat')">返回对话</button>
        <button class="admin-ghost-btn danger" @click="logout"><LogOut :size="15" /> 退出登录</button>
      </div>
    </header>

    <nav class="admin-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="admin-tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <component :is="tab.icon" :size="16" />{{ tab.label }}
      </button>
    </nav>

    <section class="admin-content">
      <!-- ===== 用户管理 ===== -->
      <div v-if="activeTab === 'users'" class="admin-panel">
        <div class="panel-heading">
          <div><h2>用户管理</h2><p>查看全部账号，可封禁/解封</p></div>
          <button class="admin-refresh" @click="loadUsers">刷新</button>
        </div>
        <div class="table-wrap">
          <table class="admin-table">
            <thead>
              <tr><th>ID</th><th>用户名</th><th>角色</th><th>状态</th><th>创建时间</th><th class="col-actions">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>{{ user.id }}</td>
                <td class="strong">{{ user.username }}</td>
                <td>{{ roleLabel(user.role) }}</td>
                <td><span class="status-chip" :class="user.status === 'banned' ? 'banned' : 'active'">{{ statusLabel(user.status) }}</span></td>
                <td class="muted">{{ formatTime(user.createdAt) }}</td>
                <td class="col-actions">
                  <button class="table-btn" @click="toggleBan(user)">{{ user.status === 'banned' ? '解封' : '封禁' }}</button>
                </td>
              </tr>
              <tr v-if="!users.length && !usersLoading"><td colspan="6" class="empty">暂无用户数据</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ===== 对话管理 ===== -->
      <div v-else-if="activeTab === 'sessions'" class="admin-panel">
        <div class="panel-heading">
          <div><h2>对话管理</h2><p>全平台会话，支持按用户/标题筛选、查看消息与物理删除</p></div>
          <div class="panel-heading-right">
            <input v-model="sessionKeyword" class="admin-search" placeholder="搜索用户名或标题" />
            <button class="admin-refresh" @click="loadSessions">刷新</button>
          </div>
        </div>
        <div class="table-wrap">
          <table class="admin-table">
            <thead>
              <tr><th>ID</th><th>归属用户</th><th>标题</th><th>状态</th><th>最近活动</th><th>创建时间</th><th class="col-actions">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="session in filteredSessions" :key="session.id">
                <td>{{ session.id }}</td>
                <td class="strong">{{ session.username }}</td>
                <td>{{ truncate(session.title, 24) }}</td>
                <td><span class="status-chip" :class="session.deleted ? 'banned' : 'active'">{{ session.deleted ? '已删除' : '正常' }}</span></td>
                <td class="muted">{{ formatTime(session.lastActiveAt) }}</td>
                <td class="muted">{{ formatTime(session.createdAt) }}</td>
                <td class="col-actions">
                  <button class="table-btn" @click="inspectSession(session)">查看消息</button>
                  <button class="table-btn danger" @click="physicalDelete(session)">物理删除</button>
                </td>
              </tr>
              <tr v-if="!filteredSessions.length && !sessionsLoading"><td colspan="7" class="empty">暂无会话数据</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ===== 统计看板 ===== -->
      <div v-else-if="activeTab === 'stats'" class="admin-panel">
        <div class="panel-heading">
          <div><h2>统计看板</h2><p>调用次数、成本与未解决对话，全部来自消息表聚合</p></div>
          <div class="range-switch">
            <button v-for="item in [{ k: '7d', l: '近 7 天' }, { k: '30d', l: '近 30 天' }, { k: 'all', l: '全部' }]"
                    :key="item.k" class="range-btn" :class="{ active: range === item.k }" @click="range = item.k">{{ item.l }}</button>
          </div>
        </div>

        <div class="stat-cards">
          <div class="stat-card">
            <span class="stat-label">AI 调用次数</span>
            <strong class="stat-value">{{ overview?.callCount ?? '—' }}</strong>
            <small>assistant 消息总数</small>
          </div>
          <div class="stat-card">
            <span class="stat-label">总调用成本</span>
            <strong class="stat-value">¥{{ Number(overview?.totalCost ?? 0).toFixed(4) }}</strong>
            <small>按配置单价累计（元）</small>
          </div>
          <div class="stat-card">
            <span class="stat-label">未解决对话</span>
            <strong class="stat-value">{{ overview?.unresolvedCount ?? '—' }}</strong>
            <small>AI 调用失败的消息数</small>
          </div>
        </div>

        <div class="chart-card">
          <h3>高频提问 Top 10</h3>
          <div v-if="topQuestions.length" ref="chartRef" class="chart-box"></div>
          <p v-else class="empty">该时间范围内没有提问数据</p>
        </div>

        <div class="table-wrap">
          <h3 class="table-title">未解决对话明细</h3>
          <table class="admin-table">
            <thead>
              <tr><th>消息 ID</th><th>用户</th><th>会话 ID</th><th>提问/内容</th><th>失败原因</th><th>时间</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in unresolved" :key="item.id">
                <td>{{ item.id }}</td>
                <td class="strong">{{ item.username }}</td>
                <td>{{ item.sessionId }}</td>
                <td>{{ truncate(item.content, 40) }}</td>
                <td class="muted">{{ item.errorMessage || '—' }}</td>
                <td class="muted">{{ formatTime(item.createdAt) }}</td>
              </tr>
              <tr v-if="!unresolved.length && !statsLoading"><td colspan="6" class="empty">没有未解决对话</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- ===== 操作日志 ===== -->
      <div v-else class="admin-panel">
        <div class="panel-heading">
          <div><h2>操作日志</h2><p>只读记录，仅支持写入与查询，不可修改或删除</p></div>
          <button class="admin-refresh" @click="loadLogs">刷新</button>
        </div>
        <div class="table-wrap">
          <table class="admin-table">
            <thead>
              <tr><th>时间</th><th>操作人</th><th>操作</th><th>对象</th><th>详情</th><th>IP</th></tr>
            </thead>
            <tbody>
              <tr v-for="log in logs" :key="log.id">
                <td class="muted">{{ formatTime(log.createdAt) }}</td>
                <td class="strong">{{ log.username }}</td>
                <td>{{ actionLabel(log.action) }}</td>
                <td class="muted">{{ log.targetType ? `${log.targetType} #${log.targetId ?? ''}` : '—' }}</td>
                <td>{{ log.detail || '—' }}</td>
                <td class="muted">{{ log.ip || '—' }}</td>
              </tr>
              <tr v-if="!logs.length && !logsLoading"><td colspan="6" class="empty">暂无操作日志</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- 会话消息检视弹窗 -->
    <div v-if="inspected" class="admin-modal-backdrop" @click.self="inspected = null">
      <section class="admin-modal wide">
        <div class="admin-modal-head">
          <h2>{{ inspected.session.username }} · {{ truncate(inspected.session.title, 24) }}</h2>
          <button class="icon-close" @click="inspected = null"><X :size="18" /></button>
        </div>
        <p v-if="inspected.loading" class="modal-desc">正在读取消息…</p>
        <div v-else class="inspected-messages">
          <div v-for="msg in inspected.messages" :key="msg.id" class="inspected-message" :class="msg.role">
            <div class="inspected-meta">
              <span class="inspected-role">{{ messageRoleLabel(msg.role) }}</span>
              <span v-if="msg.status === 'failed'" class="status-chip banned">调用失败</span>
              <span class="muted">{{ formatTime(msg.createdAt) }}</span>
            </div>
            <p class="inspected-content">{{ msg.content }}</p>
            <p v-if="msg.errorMessage" class="inspected-error">{{ msg.errorMessage }}</p>
          </div>
          <p v-if="!inspected.messages.length" class="empty">该会话暂无消息</p>
        </div>
      </section>
    </div>

    <Transition name="toast"><div v-if="toastMessage" class="admin-toast" role="status">{{ toastMessage }}</div></Transition>
  </main>
</template>

<style scoped>
.admin-shell { min-height: 100vh; background: #f7f7f9; display: flex; flex-direction: column; }
.admin-topbar {
  height: 60px; flex: 0 0 60px; background: #fff; border-bottom: 1px solid #f0eff2;
  display: flex; align-items: center; justify-content: space-between; padding: 0 28px;
}
.admin-brand { display: flex; align-items: center; gap: 9px; }
.brand-mark {
  width: 27px; height: 27px; border-radius: 9px; display: grid; place-items: center; color: #fff;
  background: linear-gradient(145deg, #a89af1, #7161cf); box-shadow: 0 3px 8px #8374d139;
}
.brand-name { font-size: 16px; font-weight: 700; color: #2b2931; }
.brand-tag {
  font-size: 10px; color: #7667ce; background: #f1efff; border-radius: 20px; padding: 3px 9px; font-weight: 600;
}
.admin-topbar-right { display: flex; align-items: center; gap: 10px; }
.admin-user { font-size: 11px; color: #77747e; }
.admin-ghost-btn {
  height: 30px; border: 1px solid #eeedf0; border-radius: 7px; background: #fff; color: #6c6973;
  display: inline-flex; align-items: center; gap: 5px; padding: 0 11px; font-size: 11px; cursor: pointer;
}
.admin-ghost-btn:hover { background: #faf9fc; }
.admin-ghost-btn.danger:hover { color: #c85c63; border-color: #f0d3d6; }

.admin-tabs { display: flex; gap: 4px; padding: 14px 28px 0; background: #fff; border-bottom: 1px solid #f0eff2; }
.admin-tab {
  height: 36px; border: 0; border-radius: 8px 8px 0 0; background: transparent; color: #8a8792;
  display: inline-flex; align-items: center; gap: 7px; padding: 0 14px; font-size: 12px; font-weight: 600; cursor: pointer;
}
.admin-tab:hover { color: #55525e; background: #faf9fc; }
.admin-tab.active { color: #4b3fa8; background: #f1efff; }

.admin-content { flex: 1; width: min(1180px, 100%); margin: 0 auto; padding: 22px 28px 40px; }
.admin-panel { display: grid; gap: 18px; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.panel-heading h2 { margin: 0 0 4px; font-size: 18px; color: #2b2931; }
.panel-heading p { margin: 0; font-size: 11px; color: #9a97a0; }
.panel-heading-right { display: flex; align-items: center; gap: 8px; }
.admin-refresh {
  height: 30px; border: 1px solid #eeedf0; border-radius: 7px; background: #fff; color: #6c6973;
  padding: 0 13px; font-size: 11px; cursor: pointer; white-space: nowrap;
}
.admin-refresh:hover { background: #faf9fc; }
.admin-search {
  height: 30px; width: 190px; border: 1px solid #eeedf0; border-radius: 7px; padding: 0 11px;
  font-size: 11px; color: #37343c; outline: none;
}
.admin-search:focus { border-color: #cbc4f0; }

.table-wrap { background: #fff; border: 1px solid #f0eff2; border-radius: 12px; overflow: auto; }
.table-title { margin: 0; font-size: 13px; color: #2b2931; padding: 14px 16px 0; }
.admin-table { width: 100%; border-collapse: collapse; font-size: 11px; }
.admin-table th {
  text-align: left; padding: 11px 14px; color: #8a8792; font-weight: 600; background: #fbfafc;
  border-bottom: 1px solid #f0eff2; white-space: nowrap;
}
.admin-table td { padding: 11px 14px; border-bottom: 1px solid #f5f4f7; color: #4c4952; vertical-align: middle; }
.admin-table tbody tr:last-child td { border-bottom: 0; }
.admin-table tbody tr:hover td { background: #fcfbff; }
.admin-table .strong { color: #2b2931; font-weight: 600; }
.admin-table .muted { color: #9a97a0; white-space: nowrap; }
.admin-table .empty { text-align: center; color: #a9a6b0; padding: 26px 0; }
.col-actions { white-space: nowrap; }
.table-btn {
  height: 26px; border: 1px solid #eeedf0; border-radius: 6px; background: #fff; color: #6c6973;
  padding: 0 10px; font-size: 10px; cursor: pointer; margin-right: 6px;
}
.table-btn:hover { background: #f6f5fb; color: #5c50b8; border-color: #d9d3f3; }
.table-btn.danger:hover { color: #c85c63; border-color: #f0d3d6; background: #fdf6f6; }

.status-chip { display: inline-block; border-radius: 20px; padding: 3px 9px; font-size: 10px; font-weight: 600; }
.status-chip.active { background: #eaf6ef; color: #559876; }
.status-chip.banned { background: #fdeeee; color: #c05c63; }

.range-switch { display: inline-flex; border: 1px solid #eeedf0; border-radius: 8px; overflow: hidden; background: #fff; }
.range-btn { border: 0; background: transparent; color: #77747e; padding: 0 13px; height: 30px; font-size: 11px; cursor: pointer; }
.range-btn:hover { background: #faf9fc; }
.range-btn.active { background: #7869d4; color: #fff; }

.stat-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.stat-card {
  background: #fff; border: 1px solid #f0eff2; border-radius: 12px; padding: 18px 20px; display: grid; gap: 6px;
}
.stat-label { font-size: 11px; color: #8a8792; }
.stat-value { font-size: 26px; font-weight: 700; color: #2b2931; letter-spacing: -0.02em; }
.stat-card small { font-size: 10px; color: #a9a6b0; }

.chart-card { background: #fff; border: 1px solid #f0eff2; border-radius: 12px; padding: 18px 20px; }
.chart-card h3 { margin: 0 0 14px; font-size: 13px; color: #2b2931; }
.chart-box { width: 100%; height: 340px; }
.chart-card .empty { text-align: center; color: #a9a6b0; padding: 60px 0; margin: 0; }

.admin-modal-backdrop {
  position: fixed; z-index: 30; inset: 0; background: #201a2a42; display: grid; place-items: center;
  padding: 20px; backdrop-filter: blur(3px);
}
.admin-modal {
  width: min(420px, 100%); background: #fff; border: 1px solid #eeeaf2; border-radius: 16px;
  padding: 22px; box-shadow: 0 22px 80px #1b142333;
}
.admin-modal.wide { width: min(680px, 100%); }
.admin-modal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.admin-modal-head h2 { margin: 0; font-size: 15px; color: #2b2931; }
.icon-close { width: 30px; height: 30px; border: 0; border-radius: 8px; background: transparent; color: #8a8792; display: grid; place-items: center; cursor: pointer; }
.icon-close:hover { background: #f2f1f4; color: #37343c; }
.modal-desc { font-size: 11px; color: #77747e; margin: 0 0 12px; line-height: 1.7; }
.password-box {
  border: 1px dashed #d9d3f3; border-radius: 10px; background: #f7f6ff; color: #4b3fa8;
  font-size: 18px; font-weight: 700; letter-spacing: 0.06em; text-align: center; padding: 16px; margin-bottom: 16px;
}
.admin-primary-btn {
  width: 100%; height: 36px; border: 0; border-radius: 9px; background: #7869d4; color: #fff;
  font-size: 12px; font-weight: 600; cursor: pointer;
}
.admin-primary-btn:hover { background: #6959c6; }

.inspected-messages { max-height: 60vh; overflow: auto; display: grid; gap: 10px; }
.inspected-message { border: 1px solid #f0eff2; border-radius: 10px; padding: 10px 12px; background: #fcfbff; }
.inspected-message.user { background: #f7f6ff; border-color: #e8e4fb; }
.inspected-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; font-size: 10px; }
.inspected-role { font-weight: 600; color: #55525e; }
.inspected-content { margin: 0; font-size: 11px; color: #44414a; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.inspected-error { margin: 6px 0 0; font-size: 10px; color: #c05c63; }
.inspected-messages .empty { text-align: center; color: #a9a6b0; padding: 30px 0; }

.admin-toast {
  position: fixed; z-index: 40; left: 50%; bottom: 40px; transform: translateX(-50%);
  border: 1px solid #e9e6ee; border-radius: 10px; background: #302d37; color: #fff;
  padding: 10px 16px; box-shadow: 0 8px 30px #201a2a25; font-size: 11px; white-space: nowrap;
}
.toast-enter-active, .toast-leave-active { transition: opacity .16s, transform .16s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, 6px); }

@media (max-width: 760px) {
  .admin-topbar { padding: 0 16px; }
  .admin-tabs { padding: 12px 16px 0; }
  .admin-content { padding: 18px 16px 32px; }
  .stat-cards { grid-template-columns: 1fr; }
  .panel-heading { flex-direction: column; }
}
</style>

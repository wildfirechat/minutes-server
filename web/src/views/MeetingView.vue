<template>
  <div class="meeting-page">
    <div class="page-header">
      <div class="header-content">
        <h1 class="page-title">📋 会议记录</h1>
        <div v-if="authStore.userInfo" class="user-info">
          <el-avatar :size="24" :icon="UserFilled" />
          <span>{{ authStore.userInfo.displayName || authStore.userInfo.userId }}</span>
        </div>
      </div>
    </div>

    <div class="page-body" v-loading="loading">
      <el-card class="summary-card" shadow="hover" v-if="meeting?.summary">
        <template #header>
          <div class="card-header">
            <span>📝 总结</span>
            <el-button type="primary" text :icon="DocumentCopy" @click="copySummary">
              拷贝
            </el-button>
          </div>
        </template>
        <div class="summary-content">{{ meeting.summary }}</div>
      </el-card>

      <el-card class="participants-card" shadow="hover" v-if="meeting?.participants?.length > 1">
        <template #header>
          <div class="card-header">
            <span>👥 参会者 ({{ meeting.participants.length }})</span>
          </div>
        </template>
        <div class="participants-list">
          <el-tag
            v-for="p in meeting.participants"
            :key="p.id"
            type="info"
            effect="plain"
            class="participant-tag"
          >
            {{ displayNameOf(p.userId) }}
          </el-tag>
        </div>
      </el-card>

      <el-card class="transcription-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>🎙️ 会议转写 ({{ meeting?.transcriptions?.length || 0 }})</span>
            <el-switch
              v-model="autoPlay"
              active-text="自动播放"
              inline-prompt
            />
          </div>
        </template>

        <el-timeline v-if="meeting?.transcriptions?.length">
          <el-timeline-item
            v-for="item in meeting.transcriptions"
            :key="item.id"
            :id="`transcription-${item.id}`"
            :timestamp="formatTimestampMs(item.timestampMs)"
            placement="top"
          >
            <TranscriptionItem
              :item="item"
              :playing-id="playingId"
              :speaker-name="displayNameOf(item.userId)"
              :show-speaker="uniqueUserCount > 1"
              @play="playAudio"
            />
          </el-timeline-item>
        </el-timeline>

        <el-empty v-else description="暂无转写记录" />
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UserFilled, DocumentCopy } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { getMeetingDetail } from '@/api'
import TranscriptionItem from '@/components/TranscriptionItem.vue'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const meeting = ref(null)
const currentAudio = ref(null)
const playingId = ref(null)
const autoPlay = ref(false)

function displayNameOf(userId) {
  if (!meeting.value?.userDisplayNameMap) return userId
  return meeting.value.userDisplayNameMap[userId] || userId
}

const uniqueUserCount = computed(() => {
  if (!meeting.value?.transcriptions?.length) return 0
  const set = new Set()
  for (const t of meeting.value.transcriptions) {
    if (t.userId) set.add(t.userId)
  }
  return set.size
})

async function copySummary() {
  if (!meeting.value?.summary) return
  try {
    await navigator.clipboard.writeText(meeting.value.summary)
    ElMessage.success('已拷贝到剪贴板')
  } catch (err) {
    // fallback：兼容不支持 clipboard API 的环境
    const textarea = document.createElement('textarea')
    textarea.value = meeting.value.summary
    textarea.style.position = 'fixed'
    textarea.style.left = '-9999px'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已拷贝到剪贴板')
    } catch (e) {
      ElMessage.error('拷贝失败')
    }
    document.body.removeChild(textarea)
  }
}

function formatTimestampMs(ms) {
  if (ms == null) return ''
  const date = new Date(ms)
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${h}:${m}:${s}`
}

let playErrorHandled = false

function playAudio(url, id) {
  if (currentAudio.value) {
    currentAudio.value.pause()
    currentAudio.value = null
  }
  if (playingId.value === id) {
    playingId.value = null
    return
  }
  playErrorHandled = false
  const audio = new Audio(url)
  audio.onended = () => {
    playingId.value = null
    if (autoPlay.value) {
      playNext(id)
    }
  }
  audio.onerror = () => {
    if (playErrorHandled) return
    playErrorHandled = true
    ElMessage.error('音频播放失败')
    playingId.value = null
    if (autoPlay.value) {
      playNext(id)
    }
  }
  audio.play().catch(err => {
    if (playErrorHandled) return
    playErrorHandled = true
    ElMessage.error('音频播放失败: ' + err.message)
    playingId.value = null
    if (autoPlay.value) {
      playNext(id)
    }
  })
  currentAudio.value = audio
  playingId.value = id

  if (autoPlay.value) {
    nextTick(() => {
      scrollToItem(id)
    })
  }
}

function scrollToItem(id) {
  const el = document.getElementById(`transcription-${id}`)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

function playNext(currentId) {
  if (!meeting.value?.transcriptions?.length) return
  const list = meeting.value.transcriptions
  const currentIndex = list.findIndex(t => t.id === currentId)
  if (currentIndex === -1) return
  for (let i = currentIndex + 1; i < list.length; i++) {
    if (list[i].audioUrl) {
      // 延迟 300ms 避免前一个 audio 的 cleanup 与下一个的创建产生竞态
      setTimeout(() => {
        playAudio(list[i].audioUrl, list[i].id)
      }, 300)
      break
    }
  }
}

async function fetchData() {
  if (!authStore.conferenceId) {
    router.replace('/')
    return
  }
  loading.value = true
  try {
    const res = await getMeetingDetail(authStore.conferenceId)
    if (res.data.code !== 0) {
      throw new Error(res.data.message || '获取会议详情失败')
    }
    meeting.value = res.data.data
  } catch (err) {
    ElMessage.error(err.message || '获取会议详情失败')
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (currentAudio.value) {
    currentAudio.value.pause()
    currentAudio.value = null
    playingId.value = null
  }
})

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.page-header {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: #fff;
  padding: 24px 0;
  margin-bottom: 24px;
}

.header-content {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  margin-bottom: 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.page-body {
  max-width: 960px;
  margin: 0 auto;
  padding: 0 20px 40px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.summary-card .summary-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #606266;
  font-size: 14px;
}

.participants-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.participant-tag {
  margin: 0;
}

.transcription-card :deep(.el-timeline) {
  padding-left: 4px;
}

@media (max-width: 768px) {
  .page-title {
    font-size: 20px;
  }

  .page-body {
    padding: 0 12px 24px;
  }
}
</style>

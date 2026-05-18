<template>
  <div class="transcription-item" :class="{ playing: isPlaying }">
    <div v-if="showSpeaker" class="transcription-header">
      <el-avatar :size="28" :icon="UserFilled" />
      <span class="speaker-name">{{ speakerName || item.userId }}</span>
      <el-tag v-if="item.screenSharing" size="small" type="warning" effect="plain">屏幕共享</el-tag>
    </div>
    <div class="transcription-content">
      <p class="transcription-text">{{ content }}</p>
      <el-button
        :type="playingId === item.id ? 'primary' : 'default'"
        :icon="playingId === item.id ? VideoPause : VideoPlay"
        circle
        size="small"
        :disabled="!item.audioUrl"
        @click="handlePlay"
        :title="buttonTitle"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { VideoPlay, VideoPause, UserFilled } from '@element-plus/icons-vue'

const props = defineProps({
  item: { type: Object, required: true },
  playingId: { type: [Number, String], default: null },
  speakerName: { type: String, default: '' },
  showSpeaker: { type: Boolean, default: true }
})

const emit = defineEmits(['play'])

const content = computed(() => props.item.correctedContent || props.item.content || '')

const isPlaying = computed(() => props.playingId === props.item.id)

const buttonTitle = computed(() => {
  if (!props.item.audioUrl) return '暂无录音'
  return isPlaying.value ? '暂停' : '播放'
})

function handlePlay() {
  if (!props.item.audioUrl) return
  emit('play', props.item.audioUrl, props.item.id)
}
</script>

<style scoped>
.transcription-item {
  background: #fafafa;
  border-radius: 8px;
  padding: 12px 16px;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.transcription-item:hover {
  background: #f0f9ff;
}

.transcription-item.playing {
  background: #ecf5ff;
  border-left-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}

.transcription-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.speaker-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.transcription-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.transcription-text {
  flex: 1;
  font-size: 14px;
  color: #606266;
  word-break: break-word;
  margin: 0;
  line-height: 1.6;
}
</style>

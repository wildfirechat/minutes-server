import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// Element Plus 组件按需引入
import {
  ElAvatar,
  ElButton,
  ElCard,
  ElEmpty,
  ElIcon,
  ElLoading,
  ElResult,
  ElSwitch,
  ElTag,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus'

// Element Plus CSS 按需引入
import 'element-plus/es/components/avatar/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/result/style/css'
import 'element-plus/es/components/switch/style/css'
import 'element-plus/es/components/tag/style/css'
import 'element-plus/es/components/timeline/style/css'
import 'element-plus/es/components/timeline-item/style/css'

const app = createApp(App)

// 全局注册组件
const components = [
  ElAvatar, ElButton, ElCard, ElEmpty, ElIcon,
  ElResult, ElSwitch, ElTag, ElTimeline, ElTimelineItem,
]
components.forEach(c => app.component(c.name, c))

// 注册 loading 指令
app.use(ElLoading)

app.use(createPinia())
app.use(router)
app.mount('#app')

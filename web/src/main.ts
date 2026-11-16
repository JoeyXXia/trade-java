import { createApp } from 'vue'
import App from './App.vue'
import { createPinia } from 'pinia'
import { makeRouter } from './router'
import 'element-plus/dist/index.css'
import './style.css'

const pinia = createPinia()
const router = makeRouter(pinia)
const app = createApp(App).use(pinia).use(router)
void router.isReady().then(() => app.mount('#app'))

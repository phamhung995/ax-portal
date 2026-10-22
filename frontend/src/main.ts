import { createApp } from 'vue'
import { createPinia } from 'pinia'

import './design-system/tokens.css'
import './design-system/components.css'
import './assets/main.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'
import './styles/table-theme.css'

const app = createApp(App).use(router)

// Mount only after the first route is resolved, so the signed-in layout never flashes on the login page.
router.isReady().then(() => app.mount('#app'))

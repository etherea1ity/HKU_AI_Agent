import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: 'Home - HKU Campus Assistant Platform',
      description: 'Explore HKU course information, academic schedules, and campus resources with the Campus Assistant.'
    }
  },
  {
    path: '/hku-assistant',
    name: 'HKUAssistant',
    component: () => import('../views/LoveMaster.vue'),
    meta: {
      title: 'HKU Assistant - HKU Campus Assistant Platform',
      description: 'Chat with the HKU Assistant for guidance on courses, policies, and campus life.'
    }
  },
  {
    path: '/info-retrieval',
    name: 'InfoRetrieval',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: 'Information Retrieval - HKU Campus Assistant Platform',
      description: 'Search HKU-related documents and knowledge with the intelligent retrieval assistant.'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router 
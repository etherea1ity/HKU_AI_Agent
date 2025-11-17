import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - HKU 校园智能助手',
      description: '香港大学校园智能助手，提供课程查询、校历信息、规章制度解答等服务'
    }
  },
  {
    path: '/hku-assistant',
    name: 'HKUAssistant',
    component: () => import('../views/LoveMaster.vue'),
    meta: {
      title: 'HKU 小助手 - 校园智能助手',
      description: 'HKU 小助手是香港大学校园智能助手的核心功能，解答课程安排、学校规章、校历信息等校园生活问题'
    }
  },
  {
    path: '/info-retrieval',
    name: 'InfoRetrieval',
    component: () => import('../views/SuperAgent.vue'),
    meta: {
      title: '信息检索 - HKU 校园智能助手',
      description: '信息检索功能帮助您快速查找香港大学的相关文档和信息，包括课程资料、规章制度、校园新闻等'
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

<template>
  <footer class="app-footer">
    <div class="footer-content">
      <div class="footer-section">
        <div class="footer-logo">
          <h3>HKU 校园智能助手</h3>
        </div>
        <div class="footer-links">
          <a href="#">《用户协议》</a>
          <a href="#">《隐私政策》</a>
        </div>
      </div>

      <div class="footer-section">
        <h4>校园服务</h4>
        <div class="footer-links">
          <a href="https://www.hku.hk" target="_blank">HKU 官网</a>
          <a href="https://www.hku.hk/calendar" target="_blank">校历信息</a>
          <a href="https://www.hku.hk/regulations" target="_blank">规章制度</a>
        </div>
      </div>

      <div class="footer-section">
        <h4>联系我们</h4>
        <div class="footer-links">
          <a href="mailto:contact@hku.hk">校园邮箱</a>
          <a href="https://www.hku.hk/contact" target="_blank">联系方式</a>
        </div>
      </div>
    </div>

    <div class="footer-bottom">
      <p>© {{ currentYear }} 香港大学校园智能助手 - 服务校园生活</p>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

// 计算当前年份
const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  background-color: #fff;
  padding: 40px 0 20px;
  color: #666;
  border-top: 1px solid #eee;
  width: 100%;
  margin-top: auto;
  position: relative;
  z-index: 2;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  padding: 0 20px;
}

.footer-section {
  flex: 1;
  min-width: 200px;
  margin-bottom: 30px;
  padding: 0 15px;
}

.footer-logo h3 {
  font-size: 1.25rem;
  margin-bottom: 15px;
  color: #333;
}

.footer-section h4 {
  font-size: 1rem;
  margin-bottom: 15px;
  color: #333;
}

.footer-links {
  display: flex;
  flex-direction: column;
}

.footer-links a {
  margin-bottom: 10px;
  color: #666;
  text-decoration: none;
  transition: color 0.2s;
}

.footer-links a:hover {
  color: #007bff;
}

.footer-bottom {
  text-align: center;
  padding-top: 20px;
  margin-top: 20px;
  border-top: 1px solid #eee;
  color: #999;
  font-size: 0.9rem;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .footer-content {
    flex-direction: column;
  }

  .footer-section {
    width: 100%;
    margin-bottom: 20px;
    padding: 0;
  }
}

@media (max-width: 480px) {
  .app-footer {
    padding: 30px 0 15px;
  }

  .footer-section h4 {
    font-size: 0.95rem;
  }

  .footer-links a {
    font-size: 0.9rem;
  }
}
</style>
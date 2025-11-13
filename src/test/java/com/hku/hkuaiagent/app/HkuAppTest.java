package com.hku.hkuaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class HkuAppTest {

    @Resource
    private HkuApp hkuApp;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是程序员鱼皮";
        String answer = hkuApp.doChat(message, chatId);
        message = "我想让另一半（编程导航）更爱我";
        answer = hkuApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        message = "我的另一半叫什么来着？刚跟你说过，帮我回忆一下";
        answer = hkuApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是程序员鱼皮，我想让另一半（编程导航）更爱我，但我不知道该怎么做";
        HkuApp.LoveReport loveReport = hkuApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？";
        String answer = hkuApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        testMessage("周末想带女朋友去上海约会，推荐几个适合情侣的小众打卡地？");
        testMessage("最近和对象吵架了，看看编程导航网站（codefather.cn）的其他情侣是怎么解决矛盾的？");
        testMessage("直接下载一张适合做手机壁纸的星空情侣图片为文件");
        testMessage("执行 Python3 脚本来生成数据分析报告");
        testMessage("保存我的恋爱档案为文件");
        testMessage("生成一份‘七夕约会计划’PDF，包含餐厅预订、活动流程和礼物清单");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = hkuApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        String message = "帮我搜索一些哄另一半开心的图片";
        String answer = hkuApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}

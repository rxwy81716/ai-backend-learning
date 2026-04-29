package com.jianbo.crawler.util;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * User-Agent 随机轮换工具
 *
 * 爬虫请求时随机选择 UA，降低被目标站点反爬识别的概率。
 */
public final class UserAgentUtil {

    private UserAgentUtil() {}

    /** 常用桌面浏览器 User-Agent 池 */
    private static final List<String> UA_POOL = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
    );

    /** 移动端 User-Agent 池（动态页面采集用） */
    private static final List<String> MOBILE_UA_POOL = List.of(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    );

    /** 随机获取一个桌面 UA */
    public static String randomDesktop() {
        return UA_POOL.get(ThreadLocalRandom.current().nextInt(UA_POOL.size()));
    }

    /** 随机获取一个移动端 UA */
    public static String randomMobile() {
        return MOBILE_UA_POOL.get(ThreadLocalRandom.current().nextInt(MOBILE_UA_POOL.size()));
    }
}

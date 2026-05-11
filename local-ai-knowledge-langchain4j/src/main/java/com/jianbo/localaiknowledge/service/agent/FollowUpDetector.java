package com.jianbo.localaiknowledge.service.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 追问检测器（与 Spring AI 版完全一致，无 AI 框架依赖）。
 */
@Component
public class FollowUpDetector {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("^(那|那么|还有|另外|除此之外)"),
            Pattern.compile("^(它|这个|这些|上面|上述|刚才|前面)"),
            Pattern.compile("^(为什么|怎么|如何)(会|能|才|不)"),
            Pattern.compile("^(继续|接着|再|还想)"),
            Pattern.compile("^(是不是|对吗|是吧)$")
    );

    public boolean isFollowUp(String question) {
        if (question == null || question.isBlank()) return false;
        String q = question.trim();
        for (Pattern p : PATTERNS) {
            if (p.matcher(q).find()) return true;
        }
        return q.length() < 8 && !q.contains("？") && !q.contains("?");
    }
}

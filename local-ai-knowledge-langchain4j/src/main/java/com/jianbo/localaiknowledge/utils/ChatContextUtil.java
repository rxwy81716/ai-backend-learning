package com.jianbo.localaiknowledge.utils;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token 计数与裁剪（对标 Spring AI 版 ChatContextUtil）。
 *
 * <h2>API 对比</h2>
 * <pre>
 * Spring AI:      extends JTokkitTokenCountEstimator
 * LangChain4j:    使用 Tokenizer 接口（OpenAiTokenizer 底层也是 JTokkit）
 * </pre>
 *
 * <p>底层都是 JTokkit cl100k_base 分词器，结果一致。</p>
 */
@Component
@Slf4j
public class ChatContextUtil {

    public static final int MAX_CONTEXT_TOKEN = 32768;
    public static final int SAFE_TOKEN_LIMIT = 19660;
    public static final int MAX_MSG_SIZE = 20;

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private final Encoding encoding = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return encoding.countTokens(text);
    }

    public int countTotalToken(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage msg : messages) {
            total += estimate(getContent(msg));
        }
        return total;
    }

    /** Token 裁剪：保留 SystemMessage，从最早的 user/assistant 对开始删 */
    public void trimByToken(List<ChatMessage> messages) {
        int total = countTotalToken(messages);
        while (total > SAFE_TOKEN_LIMIT) {
            int firstNonSystem = -1;
            for (int i = 0; i < messages.size(); i++) {
                if (!(messages.get(i) instanceof SystemMessage)) {
                    firstNonSystem = i;
                    break;
                }
            }
            if (firstNonSystem == -1) break;

            ChatMessage removed = messages.remove(firstNonSystem);
            total -= estimate(getContent(removed));

            // 删除紧随的 assistant
            if (firstNonSystem < messages.size()) {
                ChatMessage removedAssistant = messages.remove(firstNonSystem);
                total -= estimate(getContent(removedAssistant));
            }
        }
    }

    /** 从 ChatMessage 中提取文本内容 */
    public static String getContent(ChatMessage msg) {
        if (msg instanceof SystemMessage sm) return sm.text();
        if (msg instanceof UserMessage um) return um.singleText();
        if (msg instanceof AiMessage am) return am.text();
        return "";
    }
}

package com.diet.config;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AgentScope 模型配置。
 */
@Configuration
public class DietAgentScopeConfig {

    /** 模型供应商：dashscope 或 openai-compatible。 */
    @Value("${diet.llm.provider:dashscope}")
    private String provider;

    /** 模型 API Key；默认兼容原有 DashScope 配置。 */
    @Value("${diet.llm.api-key:${agentscope.dashscope.api-key:}}")
    private String apiKey;

    /** OpenAI 兼容接口地址；为空时使用 OpenAIChatModel 默认地址。 */
    @Value("${diet.llm.base-url:}")
    private String baseUrl;

    /** 主模型用于推荐理由和最终应答，默认使用 qwen-max。 */
    @Value("${diet.llm.main-model:qwen-max}")
    private String mainModelName;

    /** 轻量模型用于意图识别和澄清追问，默认使用 qwen-turbo。 */
    @Value("${diet.llm.light-model:qwen-turbo}")
    private String lightModelName;

    /**
     * 主模型 Bean。
     * RecommendResponseAgent 会优先使用该模型。
     */
    @Bean("DietMainChatModel")
    public Model DietMainChatModel() {
        return buildModel(mainModelName);
    }

    /**
     * 轻量模型 Bean。
     * IntentAgent 和 ClarifyAgent 使用它降低延迟和成本。
     */
    @Bean("DietLightChatModel")
    public Model DietLightChatModel() {
        return buildModel(lightModelName);
    }

    /** 按配置创建模型，上层 Agent 始终依赖统一的 Model 接口。 */
    private Model buildModel(String modelName) {
        if ("dashscope".equalsIgnoreCase(provider)) {
            return DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .build();
        }
        if ("openai-compatible".equalsIgnoreCase(provider) || "openai".equalsIgnoreCase(provider)) {
            OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName);
            if (baseUrl != null && !baseUrl.isBlank()) {
                builder.baseUrl(baseUrl);
            }
            return builder.build();
        }
        throw new IllegalArgumentException(
                "Unsupported diet.llm.provider: " + provider + ", expected dashscope or openai-compatible"
        );
    }
}





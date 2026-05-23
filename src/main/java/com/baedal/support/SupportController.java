package com.baedal.support;

import com.baedal.support.tool.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

/**
 * 1주차에서 만든 Structured Output 엔드포인트.
 * 2주차에는 여기에도 OrderTools를 등록하여 Tool Calling과 Structured Output이
 * 함께 동작할 수 있는지 직접 확인한다.
 */
@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient chatClient;
    private final PerformanceLoggingAdvisor performanceAdvisor;
    private final OrderTools orderTools;

    private final ChatClient chatClientV2;

    public SupportController(
            ChatClient.Builder builder,
            PerformanceLoggingAdvisor performanceAdvisor,
            OrderTools orderTools
    ) {
        this.performanceAdvisor = performanceAdvisor;
        this.orderTools = orderTools;
        this.chatClient = builder
                .defaultAdvisors(performanceAdvisor)
                .build();
        this.chatClientV2 = builder
                .defaultAdvisors(performanceAdvisor)
                .defaultTools(orderTools)
                .build();
    }

    @PostMapping
    public SupportResponse triage(@RequestBody ChatRequest req) {
        return chatClientV2.prompt()
                .user(req.message())
                .call()
                .entity(SupportResponse.class);
    }
}

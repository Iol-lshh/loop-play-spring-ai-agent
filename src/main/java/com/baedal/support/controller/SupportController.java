package com.baedal.support.controller;

import com.baedal.support.model.BaedalPrompt;
import com.baedal.support.model.ChatRequest;
import com.baedal.support.advisor.PerformanceLoggingAdvisor;
import com.baedal.support.model.SupportResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final ChatClient chatClient;

    protected SupportController(ChatClient.Builder builder, PerformanceLoggingAdvisor performanceLoggingAdvisor) {
        this.chatClient = builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .defaultAdvisors(performanceLoggingAdvisor)
                .build();
    }

    @PostMapping
    public SupportResponse triage(@RequestBody ChatRequest req) {
        return chatClient
                .prompt()
                .user(req.message())
                .call()
                .entity(SupportResponse.class);
    }
}

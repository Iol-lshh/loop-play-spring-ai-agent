package com.baedal.support.controller;

import com.baedal.support.model.BaedalPrompt;
import com.baedal.support.model.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat/stream")
public class StreamingChatController {

    private final ChatClient chatClient;

    protected StreamingChatController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(BaedalPrompt.SYSTEM_PROMPT)
                .build();
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest req) {
        return chatClient
                .prompt()
                .user(req.message())
                .stream()
                .content();
    }
}

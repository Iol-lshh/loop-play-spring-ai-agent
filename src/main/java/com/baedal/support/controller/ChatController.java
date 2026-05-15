package com.baedal.support.controller;

import com.baedal.support.model.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatClient.Builder chatClientBuilder;

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        return chatClientBuilder.build()
                .prompt()
                .user(request.message())
                .call()
                .content();
    }
}

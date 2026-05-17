package com.baedal.support.controller;

import com.baedal.support.model.ChatRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatClient chatClient;

    protected ChatController(ChatClient.Builder builder){
        this.chatClient = builder.build();
    }

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        return chatClient
                .prompt()
                .user(request.message())
                .call()
                .content();
    }
}

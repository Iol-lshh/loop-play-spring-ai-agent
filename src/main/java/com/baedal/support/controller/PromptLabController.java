package com.baedal.support.controller;

import com.baedal.support.model.SupportResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/prompt-lab")
public class PromptLabController {

    private final ChatClient chatClient;

    protected PromptLabController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @PostMapping
    public PromptLabResult experiment(@RequestBody PromptLabRequest req) {
        if (req.repeat() < 1 || req.repeat() > 20) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "repeat must be between 1 and 20");
        }

        List<SupportResponse> results = new java.util.ArrayList<>();
        for (int i = 0; i < req.repeat(); i++) {
            results.add(chatClient.prompt()
                    .system(req.systemPrompt())
                    .user(req.message())
                    .call()
                    .entity(SupportResponse.class));
        }

        return PromptLabResult.from(results);
    }

    public record PromptLabRequest(
            String systemPrompt,
            String message,
            int repeat
    ) {}

    public record PromptLabResult(
            int totalRuns,
            Map<String, Long> categoryCounts,
            Map<String, Long> urgencyCounts,
            double categoryConsistency
    ) {
        public static PromptLabResult from(List<SupportResponse> results) {
            var catCounts = results.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.category().name(), Collectors.counting()));
            var urgCounts = results.stream()
                    .collect(Collectors.groupingBy(
                            r -> r.urgency().name(), Collectors.counting()));
            long maxCat = catCounts.values().stream()
                    .mapToLong(Long::longValue).max().orElse(0);

            return new PromptLabResult(
                    results.size(), catCounts, urgCounts,
                    results.isEmpty() ? 0 : (double) maxCat / results.size()
            );
        }
    }
}

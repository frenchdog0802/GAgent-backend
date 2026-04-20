package com.gagent.service;

import com.gagent.dto.RunRequest;
import com.gagent.dto.RunResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class GagentService {

    public RunResponse processRequest(RunRequest request) {
        // TODO: Integrate OpenAI LLM tool calling here
        // For now, this is a mocked response
        
        String simulatedAgentResponse = String.format(
            "I heard you say: '%s'. I am currently a mocked agent. AI planning and execution features will be implemented soon.", 
            request.getMessage()
        );
        
        return new RunResponse(simulatedAgentResponse, "success", Instant.now());
    }
}

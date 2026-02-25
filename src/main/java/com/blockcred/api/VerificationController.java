package com.blockcred.api;

import com.blockcred.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verify")
public class VerificationController {
    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/payload")
    public VerificationResponse verifyPayload(@Valid @RequestBody VerifyPayloadRequest request) {
        return verificationService.verifyPayload(request.payload());
    }

    @GetMapping
    public VerificationResponse verifyByCredentialId(@RequestParam String credentialId) {
        return verificationService.verifyCredentialId(credentialId);
    }

    @GetMapping("/hash/{hash}")
    public VerificationResponse verifyByHash(@PathVariable String hash) {
        return verificationService.verifyHash(hash);
    }
}

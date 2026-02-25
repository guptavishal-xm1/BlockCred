package com.blockcred.api;

import com.blockcred.service.PublicVerificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicVerificationController {
    private final PublicVerificationService publicVerificationService;

    public PublicVerificationController(PublicVerificationService publicVerificationService) {
        this.publicVerificationService = publicVerificationService;
    }

    @GetMapping("/verify")
    public PublicVerificationResponse verify(@RequestParam(value = "t", required = false) String t) {
        return publicVerificationService.verifyFromToken(t);
    }
}

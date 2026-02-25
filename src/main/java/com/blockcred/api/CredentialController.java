package com.blockcred.api;

import com.blockcred.service.CredentialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/credentials")
public class CredentialController {
    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialResponse issue(@Valid @RequestBody IssueCredentialRequest request) {
        return credentialService.createAndQueueAnchor(request.payload());
    }

    @PostMapping("/{credentialId}/revoke")
    public CredentialResponse revoke(@PathVariable String credentialId) {
        return credentialService.requestRevoke(credentialId);
    }
}

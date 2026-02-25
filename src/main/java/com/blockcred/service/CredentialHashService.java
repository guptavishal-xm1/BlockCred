package com.blockcred.service;

import com.blockcred.domain.CredentialCanonicalPayload;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class CredentialHashService {
    private final CanonicalPayloadSerializer serializer;

    public CredentialHashService(CanonicalPayloadSerializer serializer) {
        this.serializer = serializer;
    }

    public String generateHash(CredentialCanonicalPayload payload) {
        return sha256(serializer.serialize(payload));
    }

    public String canonicalJson(CredentialCanonicalPayload payload) {
        return serializer.serialize(payload);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

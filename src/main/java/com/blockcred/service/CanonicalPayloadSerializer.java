package com.blockcred.service;

import com.blockcred.domain.CredentialCanonicalPayload;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

@Component
public class CanonicalPayloadSerializer {
    private final ObjectMapper objectMapper;

    public CanonicalPayloadSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public String serialize(CredentialCanonicalPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize canonical payload", e);
        }
    }
}

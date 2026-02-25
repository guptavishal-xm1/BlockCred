package com.blockcred.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@JsonPropertyOrder({"credentialId", "universityId", "studentId", "program", "degree", "issueDate", "nonce", "version"})
public record CredentialCanonicalPayload(
        @NotBlank String credentialId,
        @NotBlank String universityId,
        @NotBlank String studentId,
        @NotBlank String program,
        @NotBlank String degree,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate issueDate,
        @NotBlank String nonce,
        @NotBlank String version
) {
}

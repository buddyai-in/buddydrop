package com.buddyai.buddydrop.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Client request to begin an upload: the display name, MIME type, and declared byte size. */
public record PresignUploadRequest(
        @NotBlank String filename,
        String contentType,
        @Positive long size) {
}

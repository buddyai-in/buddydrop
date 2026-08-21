package com.buddyai.buddydrop.file.dto;

import com.buddyai.buddydrop.storage.PresignedUpload;

import java.util.UUID;

/** Response to a presign request: the new file's id plus the presigned PUT the browser should use. */
public record PresignUploadResponse(UUID fileId, PresignedUpload upload) {
}

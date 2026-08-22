package com.buddyai.buddydrop.file.dto;

import java.util.List;
import java.util.UUID;

/** Ids of the files to delete in a bulk action. Foreign/unknown ids are ignored server-side. */
public record BulkDeleteRequest(List<UUID> ids) {
}

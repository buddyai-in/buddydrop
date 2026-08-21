package com.buddyai.buddydrop.file;

import java.util.UUID;

/**
 * Narrow seam that lets {@link FileService} tear down a file's share link on delete without depending
 * on the whole sharing subsystem. Implemented by the share module; keeps the dependency one-directional
 * (file &rarr; share-cleanup) and avoids a service cycle.
 */
public interface ShareCleanup {
    void removeSharesForFile(UUID fileId);
}

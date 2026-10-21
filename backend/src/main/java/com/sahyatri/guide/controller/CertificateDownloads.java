package com.sahyatri.guide.controller;

import com.sahyatri.common.storage.GuideCertificateFiles;
import com.sahyatri.guide.service.GuideCertificationService.DocumentFile;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** Certificate documents go out as private, uncached attachments, never rendered inline. */
final class CertificateDownloads {

    private CertificateDownloads() {
    }

    static ResponseEntity<byte[]> send(DocumentFile file) {
        return ResponseEntity.ok()
                .contentType(GuideCertificateFiles.mediaType(file.key()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.content());
    }
}

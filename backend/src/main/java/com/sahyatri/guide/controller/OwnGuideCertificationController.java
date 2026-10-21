package com.sahyatri.guide.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.guide.dto.Certification;
import com.sahyatri.guide.dto.CertificationRequest;
import com.sahyatri.guide.service.GuideCertificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.3. GUIDE only (/api/guide/**); the guide is the token's subject. */
@RestController
@RequestMapping("/api/guide/certifications")
public class OwnGuideCertificationController {

    private final GuideCertificationService certifications;

    public OwnGuideCertificationController(GuideCertificationService certifications) {
        this.certifications = certifications;
    }

    @GetMapping
    public ItemsResponse<Certification> list(@AuthenticationPrincipal Jwt jwt) {
        return new ItemsResponse<>(certifications.listOwn(userId(jwt)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Certification create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CertificationRequest req) {
        return certifications.create(userId(jwt), req);
    }

    @PutMapping("/{id}")
    public Certification update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                @Valid @RequestBody CertificationRequest req) {
        return certifications.update(userId(jwt), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        certifications.delete(userId(jwt), id);
    }

    @PutMapping(path = "/{id}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Certification uploadDocument(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @RequestPart("file") MultipartFile file) {
        return certifications.uploadDocument(userId(jwt), id, file);
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return CertificateDownloads.send(certifications.ownDocument(userId(jwt), id));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

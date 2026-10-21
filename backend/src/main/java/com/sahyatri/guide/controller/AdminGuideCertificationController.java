package com.sahyatri.guide.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.guide.dto.Certification;
import com.sahyatri.guide.dto.VerifyRequest;
import com.sahyatri.guide.service.GuideCertificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21.3. ADMIN only (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/guides/{guideId}/certifications")
public class AdminGuideCertificationController {

    private final GuideCertificationService certifications;

    public AdminGuideCertificationController(GuideCertificationService certifications) {
        this.certifications = certifications;
    }

    @GetMapping
    public ItemsResponse<Certification> list(@PathVariable UUID guideId) {
        return new ItemsResponse<>(certifications.listForAdmin(guideId));
    }

    @PostMapping("/{id}/verify")
    public Certification verify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID guideId, @PathVariable UUID id,
                                @Valid @RequestBody(required = false) VerifyRequest req) {
        return certifications.verify(adminId(jwt), guideId, id, req == null ? null : req.note(), true);
    }

    @PostMapping("/{id}/unverify")
    public Certification unverify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID guideId, @PathVariable UUID id,
                                  @Valid @RequestBody(required = false) VerifyRequest req) {
        return certifications.verify(adminId(jwt), guideId, id, req == null ? null : req.note(), false);
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@PathVariable UUID guideId, @PathVariable UUID id) {
        return CertificateDownloads.send(certifications.adminDocument(guideId, id));
    }

    private static UUID adminId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}

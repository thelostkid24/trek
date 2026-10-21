package com.sahyatri.guide.controller;

import com.sahyatri.guide.dto.GuideCredentials;
import com.sahyatri.guide.service.GuideCertificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Contract: docs/TRD.md §7.21.3. Public; {key} is the guide's id, slug or an old slug. */
@RestController
public class PublicGuideCredentialsController {

    private final GuideCertificationService certifications;

    public PublicGuideCredentialsController(GuideCertificationService certifications) {
        this.certifications = certifications;
    }

    @GetMapping("/api/public/guides/{key}/credentials")
    public GuideCredentials credentials(@PathVariable String key) {
        return certifications.publicCredentials(key);
    }
}

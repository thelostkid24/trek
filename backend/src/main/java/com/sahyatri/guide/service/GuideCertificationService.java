package com.sahyatri.guide.service;

import com.sahyatri.auth.entity.Role;
import com.sahyatri.auth.entity.User;
import com.sahyatri.auth.repository.UserRepository;
import com.sahyatri.auth.service.CurrentUser;
import com.sahyatri.common.audit.AuditLog;
import com.sahyatri.common.config.CatalogProperties;
import com.sahyatri.common.exception.ApiException;
import com.sahyatri.common.storage.FileStorage;
import com.sahyatri.common.storage.GuideCertificateFiles;
import com.sahyatri.guide.dto.Certification;
import com.sahyatri.guide.dto.CertificationRequest;
import com.sahyatri.guide.dto.CertificationStatus;
import com.sahyatri.guide.dto.GuideCredentials;
import com.sahyatri.guide.dto.PublicCertification;
import com.sahyatri.guide.entity.CertificationType;
import com.sahyatri.guide.entity.GuideCertification;
import com.sahyatri.guide.repository.GuideCertificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guide certifications (§7.21.3). The guide (always the token's subject) adds, edits and removes their own and
 * attaches a private document; an admin verifies them against it. Any edit by the guide drops the verification.
 * Documents are never public: only the owning guide and admins can download them.
 */
@Service
public class GuideCertificationService {

    static final int MAX_PER_GUIDE = 20;
    private static final Comparator<GuideCertification> PAGE_ORDER =
            Comparator.comparing(GuideCertification::getType).thenComparing(GuideCertification::getCreatedAt);

    private final GuideCertificationRepository certifications;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final FileStorage storage;
    private final AuditLog audit;
    private final GuideSlugService slugs;
    private final GuideTierService tiers;
    private final CatalogProperties catalog;

    public GuideCertificationService(GuideCertificationRepository certifications, CurrentUser currentUser,
                                     UserRepository users, FileStorage storage, AuditLog audit,
                                     GuideSlugService slugs, GuideTierService tiers, CatalogProperties catalog) {
        this.certifications = certifications;
        this.currentUser = currentUser;
        this.users = users;
        this.storage = storage;
        this.audit = audit;
        this.slugs = slugs;
        this.tiers = tiers;
        this.catalog = catalog;
    }

    // --- The guide's own ---

    @Transactional(readOnly = true)
    public List<Certification> listOwn(UUID guideId) {
        requireSignedInGuide(guideId);
        return sorted(certifications.findByGuideIdOrderByCreatedAtAsc(guideId)).stream()
                .map(c -> toResponse(c, ownDocumentPath(c))).toList();
    }

    @Transactional
    public Certification create(UUID guideId, CertificationRequest req) {
        requireSignedInGuide(guideId);
        if (certifications.countByGuideId(guideId) >= MAX_PER_GUIDE) {
            throw ApiException.conflict("TOO_MANY_CERTIFICATIONS", "A guide can list up to " + MAX_PER_GUIDE);
        }
        GuideCertification c = new GuideCertification(guideId);
        apply(c, req);
        c = certifications.saveAndFlush(c);
        return toResponse(c, ownDocumentPath(c));
    }

    @Transactional
    public Certification update(UUID guideId, UUID id, CertificationRequest req) {
        requireSignedInGuide(guideId);
        GuideCertification c = requireOwn(guideId, id);
        apply(c, req);
        c = certifications.saveAndFlush(c);
        return toResponse(c, ownDocumentPath(c));
    }

    @Transactional
    public void delete(UUID guideId, UUID id) {
        requireSignedInGuide(guideId);
        GuideCertification c = requireOwn(guideId, id);
        certifications.delete(c);
        certifications.flush();
        deleteQuietly(c.getDocumentKey());
    }

    /** Adds or replaces the document; verification is dropped so an admin checks the new one. */
    @Transactional
    public Certification uploadDocument(UUID guideId, UUID id, MultipartFile file) {
        requireSignedInGuide(guideId);
        GuideCertification c = requireOwn(guideId, id);
        GuideCertificateFiles.Document doc = GuideCertificateFiles.prepare(file);
        storage.put(doc.key(), doc.content());
        String previous = c.getDocumentKey();
        c.replaceDocument(doc.key());
        c = certifications.saveAndFlush(c);
        deleteQuietly(previous);
        return toResponse(c, ownDocumentPath(c));
    }

    @Transactional(readOnly = true)
    public DocumentFile ownDocument(UUID guideId, UUID id) {
        requireSignedInGuide(guideId);
        return document(requireOwn(guideId, id));
    }

    // --- Admin ---

    @Transactional(readOnly = true)
    public List<Certification> listForAdmin(UUID guideId) {
        requireGuide(guideId);
        return sorted(certifications.findByGuideIdOrderByCreatedAtAsc(guideId)).stream()
                .map(c -> toResponse(c, adminDocumentPath(c))).toList();
    }

    @Transactional
    public Certification verify(UUID adminId, UUID guideId, UUID id, String note, boolean verified) {
        requireGuide(guideId);
        GuideCertification c = requireOwn(guideId, id);
        if (verified) {
            c.verify(adminId);
        } else {
            c.unverify();
        }
        c = certifications.saveAndFlush(c);
        Map<String, Object> data = new HashMap<>();
        data.put("guide_id", guideId.toString());
        data.put("type", c.getType().name());
        if (note != null && !note.isBlank()) {
            data.put("note", note.trim());
        }
        audit.record(adminId, verified ? "CERTIFICATION_VERIFIED" : "CERTIFICATION_UNVERIFIED",
                "GUIDE_CERTIFICATION", c.getId(), data);
        return toResponse(c, adminDocumentPath(c));
    }

    @Transactional(readOnly = true)
    public DocumentFile adminDocument(UUID guideId, UUID id) {
        requireGuide(guideId);
        return document(requireOwn(guideId, id));
    }

    // --- Public ---

    /** Verified certificates (expired ones too) and the tier. Never pending ones, documents or most numbers. */
    @Transactional(readOnly = true)
    public GuideCredentials publicCredentials(String key) {
        UUID guideId = slugs.resolve(key)
                .flatMap(users::findById)
                .filter(u -> u.getRole() == Role.GUIDE && !u.isDisabled())
                .map(User::getId)
                .orElseThrow(GuideCertificationService::guideNotFound);
        LocalDate today = today();
        List<GuideCertification> all = certifications.findByGuideIdOrderByCreatedAtAsc(guideId);
        List<PublicCertification> items = sorted(all).stream().filter(GuideCertification::isVerified)
                .map(c -> new PublicCertification(c.getId(), c.getType(), c.getTitle(), c.getIssuer(), c.getYear(),
                        c.getValidTo(), c.getType().publicNumber() ? c.getNumber() : null, status(c, today)))
                .toList();
        boolean verified = items.stream().anyMatch(i -> i.status() == CertificationStatus.VERIFIED);
        return new GuideCredentials(tiers.tierFor(guideId, all, today), verified, items);
    }

    /** A document to send: bytes, type and a file name. */
    public record DocumentFile(byte[] content, String key, String filename) {
    }

    private DocumentFile document(GuideCertification c) {
        String key = c.getDocumentKey();
        if (key == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "No document uploaded");
        }
        byte[] bytes = storage.get(key)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "No document uploaded"));
        String name = c.getType().name().toLowerCase().replace('_', '-') + "-certificate."
                + GuideCertificateFiles.extension(key);
        return new DocumentFile(bytes, key, name);
    }

    private void apply(GuideCertification c, CertificationRequest req) {
        String title = blankToNull(req.title());
        if (req.type() == CertificationType.OTHER && title == null) {
            throw ApiException.validation("title", "is required for other certificates");
        }
        if (req.year() != null && req.year() > today().getYear()) {
            throw ApiException.validation("year", "can't be in the future");
        }
        c.edit(req.type(), title, blankToNull(req.issuer()), req.year(), req.validTo(), blankToNull(req.number()));
    }

    private Certification toResponse(GuideCertification c, String documentPath) {
        return new Certification(c.getId(), c.getType(), c.getTitle(), c.getIssuer(), c.getYear(), c.getValidTo(),
                c.getNumber(), status(c, today()), documentPath, c.getVerifiedAt(), c.getCreatedAt(),
                c.getUpdatedAt());
    }

    static CertificationStatus status(GuideCertification c, LocalDate today) {
        if (c.isExpired(today)) {
            return CertificationStatus.EXPIRED;
        }
        return c.isVerified() ? CertificationStatus.VERIFIED : CertificationStatus.PENDING;
    }

    private static String ownDocumentPath(GuideCertification c) {
        return c.getDocumentKey() == null ? null : "/api/guide/certifications/" + c.getId() + "/document";
    }

    private static String adminDocumentPath(GuideCertification c) {
        return c.getDocumentKey() == null ? null
                : "/api/admin/guides/" + c.getGuideId() + "/certifications/" + c.getId() + "/document";
    }

    private static List<GuideCertification> sorted(List<GuideCertification> certs) {
        return certs.stream().sorted(PAGE_ORDER).toList();
    }

    /** Someone else's certificate is "not found", never "forbidden": ids don't leak. */
    private GuideCertification requireOwn(UUID guideId, UUID id) {
        return certifications.findByIdAndGuideId(id, guideId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CERTIFICATION_NOT_FOUND", "Certificate not found"));
    }

    private void requireSignedInGuide(UUID guideId) {
        if (currentUser.require(guideId).getRole() != Role.GUIDE) {
            throw ApiException.forbidden("Guides only");
        }
    }

    private void requireGuide(UUID guideId) {
        users.findById(guideId).filter(u -> u.getRole() == Role.GUIDE).orElseThrow(GuideCertificationService::guideNotFound);
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException ignored) {
            // An orphaned private file is harmless; nothing links to it.
        }
    }

    private LocalDate today() {
        return LocalDate.now(catalog.zone());
    }

    private static ApiException guideNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "GUIDE_NOT_FOUND", "Guide not found");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.sahyatri.guide.controller;

import com.sahyatri.common.web.ItemsResponse;
import com.sahyatri.guide.dto.Specialty;
import com.sahyatri.guide.dto.SpecialtyRequest;
import com.sahyatri.guide.service.GuideSpecialtyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contract: docs/TRD.md §7.21. ADMIN only (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/guide-specialties")
public class AdminGuideSpecialtyController {

    private final GuideSpecialtyService specialties;

    public AdminGuideSpecialtyController(GuideSpecialtyService specialties) {
        this.specialties = specialties;
    }

    @GetMapping
    public ItemsResponse<Specialty> list() {
        return new ItemsResponse<>(specialties.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Specialty create(@Valid @RequestBody SpecialtyRequest req) {
        return specialties.create(req.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        specialties.delete(id);
    }
}

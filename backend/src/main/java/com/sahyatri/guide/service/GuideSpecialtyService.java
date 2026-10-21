package com.sahyatri.guide.service;

import com.sahyatri.common.exception.ApiException;
import com.sahyatri.guide.dto.Specialty;
import com.sahyatri.guide.entity.GuideSpecialty;
import com.sahyatri.guide.repository.GuideSpecialtyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** The specialties guides pick from (§7.21). Admins add and remove them; removing one drops it from every guide. */
@Service
public class GuideSpecialtyService {

    private final GuideSpecialtyRepository specialties;

    public GuideSpecialtyService(GuideSpecialtyRepository specialties) {
        this.specialties = specialties;
    }

    @Transactional(readOnly = true)
    public List<Specialty> list() {
        return specialties.findAllByOrderByPositionAscNameAsc().stream()
                .map(s -> new Specialty(s.getId(), s.getName())).toList();
    }

    @Transactional
    public Specialty create(String name) {
        String clean = name.trim();
        if (specialties.existsByNameIgnoreCase(clean)) {
            throw ApiException.conflict("SPECIALTY_EXISTS", "That specialty is already on the list");
        }
        int position = specialties.findAllByOrderByPositionAscNameAsc().stream()
                .mapToInt(GuideSpecialty::getPosition).max().orElse(-1) + 1;
        GuideSpecialty s = specialties.saveAndFlush(new GuideSpecialty(clean, position));
        return new Specialty(s.getId(), s.getName());
    }

    @Transactional
    public void delete(UUID id) {
        GuideSpecialty s = specialties.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPECIALTY_NOT_FOUND", "Specialty not found"));
        specialties.delete(s);
    }
}

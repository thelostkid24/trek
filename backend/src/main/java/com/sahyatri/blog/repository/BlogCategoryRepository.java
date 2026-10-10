package com.sahyatri.blog.repository;

import com.sahyatri.blog.entity.BlogCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogCategoryRepository extends JpaRepository<BlogCategory, UUID> {

    /** Menu order: by position, then name. */
    List<BlogCategory> findAllByOrderByPositionAscNameAsc();

    Optional<BlogCategory> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByParentId(UUID parentId);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}

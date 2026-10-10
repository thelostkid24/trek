package com.sahyatri.blog.repository;

import com.sahyatri.blog.entity.BlogCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogCategoryRepository extends JpaRepository<BlogCategory, UUID> {

    List<BlogCategory> findAllByOrderByNameAsc();

    Optional<BlogCategory> findBySlug(String slug);

    List<BlogCategory> findByParentId(UUID parentId);

    boolean existsByParentId(UUID parentId);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}

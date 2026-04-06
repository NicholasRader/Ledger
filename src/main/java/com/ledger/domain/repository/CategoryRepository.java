package com.ledger.domain.repository;

import com.ledger.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Returns global defaults + user's own custom categories
    @Query("""
        SELECT c FROM Category c
        WHERE c.user IS NULL OR c.user.id = :userId
        ORDER BY c.isDefault DESC, c.name ASC
    """)
    List<Category> findAvailableForUser(@Param("userId") Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);
}

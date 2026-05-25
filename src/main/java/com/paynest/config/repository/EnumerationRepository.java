package com.paynest.config.repository;

import com.paynest.config.entity.Enumeration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnumerationRepository extends JpaRepository<Enumeration, Long> {

    @Query(value = "SHOW search_path", nativeQuery = true)
    String getSearchPath();

    List<Enumeration> findByEnumTypeAndIsActive(String type, boolean isActive);

    List<Enumeration> findByEnumTypeIgnoreCaseAndIsActiveTrueOrderByDisplayOrderAscEnumValueAsc(String enumType);

    Optional<Enumeration> findByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(
            String enumType,
            String enumCode
    );

    boolean existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndParentEnumIdAndIsActiveTrue(
            String enumType,
            String enumCode,
            Long parentEnumId
    );

    boolean existsByEnumTypeIgnoreCaseAndEnumCodeIgnoreCaseAndIsActiveTrue(
            String enumType,
            String enumCode
    );
}

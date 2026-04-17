package com.umurinan.eda.ch10;

import com.umurinan.eda.ch10.domain.ProcessedEvent;
import com.umurinan.eda.ch10.domain.ProcessedEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@DisplayName("DeduplicationConstraint — processed_events primary key enforces uniqueness")
class DeduplicationConstraintTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("saving two ProcessedEvent entities with the same UUID throws DataIntegrityViolationException")
    void duplicateId_throwsDataIntegrityViolationException() {
        var sharedId = UUID.randomUUID();

        processedEventRepository.saveAndFlush(new ProcessedEvent(sharedId));

        // Detach so JPA treats the second save as a new INSERT rather than a merge
        entityManager.clear();

        assertThatThrownBy(() -> {
            // Use native SQL to force a true duplicate insert bypassing JPA merge logic
            entityManager.createNativeQuery(
                    "INSERT INTO processed_events (id, processed_at) VALUES (?, ?)"
            )
            .setParameter(1, sharedId)
            .setParameter(2, java.sql.Timestamp.from(java.time.Instant.now()))
            .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class)
          .satisfies(ex -> assertThat(ex)
              .isInstanceOfAny(
                  DataIntegrityViolationException.class,
                  jakarta.persistence.PersistenceException.class,
                  org.hibernate.exception.ConstraintViolationException.class
              ));
    }

    @Test
    @DisplayName("saving ProcessedEvent entities with different UUIDs succeeds")
    void differentIds_persistWithoutError() {
        processedEventRepository.saveAndFlush(new ProcessedEvent(UUID.randomUUID()));
        processedEventRepository.saveAndFlush(new ProcessedEvent(UUID.randomUUID()));

        assertThat(processedEventRepository.count()).isGreaterThanOrEqualTo(2);
    }
}

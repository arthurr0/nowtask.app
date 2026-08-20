package app.nowtask.integrations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findTop50ByUserIdOrderByAtDesc(UUID userId);

    List<Notification> findTop50ByUserIdAndReadAtIsNullOrderByAtDesc(UUID userId);

    List<Notification> findByUserIdAndReadAtIsNull(UUID userId);

    int countByUserIdAndReadAtIsNull(UUID userId);
}

interface IntegrationRepository extends JpaRepository<Integration, UUID> {

    List<Integration> findAllByOrderByCreatedAtAsc();

    Optional<Integration> findFirstByNameIgnoreCase(String name);

    List<Integration> findByEnabledTrue();
}

interface IntegrationDeliveryRepository extends JpaRepository<IntegrationDelivery, UUID> {

    List<IntegrationDelivery> findTop20ByIntegrationIdOrderByAtDesc(UUID integrationId);
}

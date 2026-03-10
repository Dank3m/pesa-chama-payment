package com.tablebanking.payment.repository;

import com.tablebanking.payment.entity.StkPushRequest;
import com.tablebanking.payment.entity.enums.CollectionStatus;
import com.tablebanking.payment.entity.enums.CollectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StkPushRequestRepository extends JpaRepository<StkPushRequest, UUID> {

    Optional<StkPushRequest> findByCollectionRef(String collectionRef);

    Optional<StkPushRequest> findByCheckoutRequestId(String checkoutRequestId);

    List<StkPushRequest> findBySourceIdAndCollectionTypeAndStatusIn(
            UUID sourceId, CollectionType collectionType, List<CollectionStatus> statuses);

    List<StkPushRequest> findByStatusAndCreatedAtBefore(CollectionStatus status, Instant before);
}

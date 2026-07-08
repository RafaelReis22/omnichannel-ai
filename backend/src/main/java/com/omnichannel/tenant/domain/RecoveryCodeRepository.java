package com.omnichannel.tenant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, UUID> {

	Optional<RecoveryCode> findByUserIdAndCodeHash(UUID userId, String codeHash);

	List<RecoveryCode> findAllByUserId(UUID userId);
}

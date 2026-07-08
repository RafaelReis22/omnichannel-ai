package com.omnichannel.inbox.domain;

import java.util.Optional;
import java.util.UUID;

import com.omnichannel.channels.ChannelType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactIdentityRepository extends JpaRepository<ContactIdentity, UUID> {

	Optional<ContactIdentity> findByChannelTypeAndExternalId(ChannelType channelType, String externalId);

	java.util.List<ContactIdentity> findAllByContactId(UUID contactId);
}

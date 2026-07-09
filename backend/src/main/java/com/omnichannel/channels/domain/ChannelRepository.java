package com.omnichannel.channels.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {

	java.util.List<Channel> findAllByTypeAndStatus(com.omnichannel.channels.ChannelType type, ChannelStatus status);
}

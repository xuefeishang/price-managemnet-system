package com.pricemanagement.repository;

import com.pricemanagement.entity.IpBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IpBlacklistRepository extends JpaRepository<IpBlacklist, Long> {

    Optional<IpBlacklist> findByIpAddressAndActiveTrue(String ipAddress);

    List<IpBlacklist> findByActiveTrue();
}

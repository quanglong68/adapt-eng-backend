package com.longdq.adaptengbackend.repository;

import com.longdq.adaptengbackend.entity.SubscriptionPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionPackageRepository extends JpaRepository<SubscriptionPackage, Long> {
}

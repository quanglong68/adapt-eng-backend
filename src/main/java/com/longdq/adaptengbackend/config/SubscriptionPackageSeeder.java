package com.longdq.adaptengbackend.config;

import com.longdq.adaptengbackend.entity.SubscriptionPackage;
import com.longdq.adaptengbackend.repository.SubscriptionPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionPackageSeeder implements CommandLineRunner {

    private final SubscriptionPackageRepository subscriptionPackageRepository;

    @Override
    public void run(String... args) {
        if (subscriptionPackageRepository.count() > 0) {
            return;
        }

        SubscriptionPackage oneMonth = new SubscriptionPackage();
        oneMonth.setName("1 Month");
        oneMonth.setPrice(50000L);
        oneMonth.setDurationDays(30);

        SubscriptionPackage sixMonths = new SubscriptionPackage();
        sixMonths.setName("6 Months");
        sixMonths.setPrice(250000L);
        sixMonths.setDurationDays(180);

        subscriptionPackageRepository.save(oneMonth);
        subscriptionPackageRepository.save(sixMonths);

        log.info("Seeded default subscription packages: 1 Month (50k), 6 Months (250k)");
    }
}

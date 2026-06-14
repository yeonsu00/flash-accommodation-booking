package com.flashaccommodationbooking.infrastructure.user;

import com.flashaccommodationbooking.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long> {
}

package com.flashaccommodationbooking.application.user;

import com.flashaccommodationbooking.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.getById(userId);
    }

    @Transactional
    public void deductPoint(Long userId, int amount) {
        User user = userRepository.getById(userId);
        user.deductPoint(amount);
    }

    @Transactional
    public void addPoint(Long userId, int amount) {
        User user = userRepository.getById(userId);
        user.addPoint(amount);
    }

}

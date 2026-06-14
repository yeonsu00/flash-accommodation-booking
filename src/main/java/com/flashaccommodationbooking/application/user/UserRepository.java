package com.flashaccommodationbooking.application.user;

import com.flashaccommodationbooking.domain.user.User;

public interface UserRepository {

    User getById(Long id);

}

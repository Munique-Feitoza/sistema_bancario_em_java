// src/main/java/com/munique/bank/iam/web/UserMapper.java
package com.munique.bank.iam.web;

import com.munique.bank.iam.domain.User;
import com.munique.bank.iam.web.dto.UserResponse;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name());
    }
}

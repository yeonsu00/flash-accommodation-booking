package com.flashaccommodationbooking.domain.user;

import com.flashaccommodationbooking.global.entity.BaseEntity;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(nullable = false)
    private int point;

    @Builder
    private User(String name, int point) {
        this.name = name;
        this.point = point;
    }

    public static User of(String name, int point) {
        return User.builder()
                .name(name)
                .point(point)
                .build();
    }

    public void deductPoint(int amount) {
        if (this.point < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }
        this.point -= amount;
    }

    public void addPoint(int amount) {
        this.point += amount;
    }
}

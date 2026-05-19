package com.pricemanagement.repository;

import com.pricemanagement.entity.Captcha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CaptchaRepository extends JpaRepository<Captcha, Long> {

    Optional<Captcha> findByCaptchaKey(String captchaKey);

    void deleteByExpireTimeBefore(LocalDateTime time);

    void deleteByCaptchaKey(String captchaKey);
}
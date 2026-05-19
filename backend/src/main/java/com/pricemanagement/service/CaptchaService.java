package com.pricemanagement.service;

import com.pricemanagement.dto.CaptchaResponse;
import com.pricemanagement.entity.Captcha;
import com.pricemanagement.repository.CaptchaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import javax.imageio.ImageIO;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final CaptchaRepository captchaRepository;
    private final Random random = new Random();

    private static final int CAPTCHA_LENGTH = 4;
    private static final int EXPIRE_MINUTES = 5;
    private static final int IMAGE_WIDTH = 120;
    private static final int IMAGE_HEIGHT = 40;

    /**
     * 生成验证码
     */
    @Transactional
    public CaptchaResponse generateCaptcha(String ipAddress) {
        // 生成4位数字验证码
        String code = generateCode();

        // 生成UUID作为key
        String key = UUID.randomUUID().toString();

        // 生成验证码图片
        String image = generateCaptchaImage(code);

        // 设置过期时间
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(EXPIRE_MINUTES);

        // 保存到数据库
        Captcha captcha = new Captcha();
        captcha.setCaptchaKey(key);
        captcha.setCaptchaCode(code);
        captcha.setCaptchaImage(image);
        captcha.setIpAddress(ipAddress);
        captcha.setExpireTime(expireTime);
        captcha.setUsed(false);
        captchaRepository.save(captcha);

        log.debug("Generated captcha for IP: {}", ipAddress);

        return new CaptchaResponse(key, image);
    }

    /**
     * 验证验证码
     */
    @Transactional
    public boolean validateCaptcha(String key, String code) {
        if (key == null || code == null) {
            return false;
        }

        Optional<Captcha> captchaOpt = captchaRepository.findByCaptchaKey(key);
        if (captchaOpt.isEmpty()) {
            log.debug("Captcha not found for key: {}", key);
            return false;
        }

        Captcha captcha = captchaOpt.get();

        // 检查是否过期
        if (captcha.getExpireTime().isBefore(LocalDateTime.now())) {
            log.debug("Captcha expired for key: {}", key);
            return false;
        }

        // 检查是否已使用
        if (Boolean.TRUE.equals(captcha.getUsed())) {
            log.debug("Captcha already used for key: {}", key);
            return false;
        }

        // 检查验证码是否匹配（忽略大小写）
        if (!captcha.getCaptchaCode().equalsIgnoreCase(code)) {
            log.debug("Captcha mismatch for key: {}", key);
            return false;
        }

        // 标记已使用
        captcha.setUsed(true);
        captchaRepository.save(captcha);

        return true;
    }

    /**
     * 生成4位数字验证码
     */
    private String generateCode() {
        int code = random.nextInt(10000);
        return String.format("%04d", code);
    }

    /**
     * 生成验证码图片（Base64格式）
     */
    private String generateCaptchaImage(String code) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 设置抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 填充背景
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        // 绘制干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.drawLine(random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT),
                       random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT));
        }

        // 绘制干扰点
        for (int i = 0; i < 30; i++) {
            g.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
            g.fillOval(random.nextInt(IMAGE_WIDTH), random.nextInt(IMAGE_HEIGHT), 2, 2);
        }

        // 绘制验证码
        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < code.length(); i++) {
            // 随机颜色
            g.setColor(new Color(random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            // 随机位置偏移
            int x = 15 + i * 25 + random.nextInt(5);
            int y = 28 + random.nextInt(8);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
        }

        g.dispose();

        // 转换为Base64
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("Failed to generate captcha image", e);
            return null;
        }
    }

    /**
     * 定时清理过期验证码（每小时执行）
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void cleanExpiredCaptchas() {
        try {
            captchaRepository.deleteByExpireTimeBefore(LocalDateTime.now());
            log.debug("Cleaned expired captchas");
        } catch (Exception e) {
            log.error("Failed to clean expired captchas", e);
        }
    }
}
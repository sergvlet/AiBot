// src/main/java/com/chicu/aibot/bot/TelegramBotProperties.java
package com.chicu.aibot.bot;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Свойства бота: токен и username.
 * Читаются из application.yml (или application.properties).
 */
@Component
@ConfigurationProperties(prefix = "telegram.bot")
@Data
public class TelegramBotProperties {
    /**
     * username бота без "@"
     */
    private String username;
    /**
     * токен, полученный от BotFather
     */
    private String token;
}

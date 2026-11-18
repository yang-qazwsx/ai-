package com.ruoyi.system.config;

import java.util.Collections;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.system.api.model.LoginUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 本地/测试环境专用：通过 {@code X-Test-User} 请求头注入登录态与全量权限。
 *
 * <p>仅在 {@code ai.chat.test-security-bypass=true} 时注册；生产环境默认关闭，
 * 权限注解与 fail-closed userId 策略保持不变。专供无网关、无 token 的黑盒回归脚本使用。</p>
 */
@Configuration
@ConditionalOnProperty(name = "ai.chat.test-security-bypass", havingValue = "true")
public class TestSecurityBypassConfig implements WebMvcConfigurer
{
    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(new HandlerInterceptor()
        {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            {
                String raw = request.getHeader("X-Test-User");
                if (raw != null && !raw.isBlank())
                {
                    try
                    {
                        Long userId = Long.valueOf(raw.trim());
                        LoginUser loginUser = new LoginUser();
                        loginUser.setUserid(userId);
                        loginUser.setUsername("test-user-" + userId);
                        loginUser.setPermissions(Collections.singleton("*:*:*"));
                        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
                        SecurityContextHolder.setUserId(String.valueOf(userId));
                        SecurityContextHolder.setUserName(loginUser.getUsername());
                    }
                    catch (NumberFormatException ignored)
                    {
                        // 非法测试头按匿名处理，保持原安全语义
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/v1/**", "/biz/**").order(10000);
    }
}

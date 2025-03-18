package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.time.Duration;
import java.util.Map;

public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringredisTemplate;

    public LoginInterceptor(StringRedisTemplate stringredisTemplate) {
        this.stringredisTemplate = stringredisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }
        //2.基于token获取redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringredisTemplate.opsForHash().entries(key);
        //3.判空
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }
        //将数据转为UserDto
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //4.存在 保存信息到ThreadLocal
        UserHolder.saveUser((UserDTO) userDTO);

        //刷新token有效期
        stringredisTemplate.expire(key, Duration.ofMinutes(30));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}

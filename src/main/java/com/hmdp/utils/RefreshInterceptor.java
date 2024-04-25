package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshInterceptor implements HandlerInterceptor {

        private StringRedisTemplate stringRedisTemplate;
        public RefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
            this.stringRedisTemplate = stringRedisTemplate;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {


            //从请求头中获取token
            String token = request.getHeader("authorization");
            if(StrUtil.isBlank(token)){
                return true;
            }
            //改成从redis中获取用户—用entries方法获取key对应map
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
            if(userMap.isEmpty()){
                response.setStatus(401);
                return false;
            }
            UserDTO userDTO = new UserDTO();
            userDTO = BeanUtil.fillBeanWithMap(userMap,userDTO,false);
            if(userDTO == null) return false;
            //存在，用ThreadLocal存——注意这个存入ThreadLocal是在拦截器中存
            //说一下原理：所有请求都会经过拦截器，每个请求都有个ThreadLocal，经过拦截器就把redis的userDTO取出来放到线程里
            //获得user信息，而不需要每次请求携带user
            //UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            UserHolder.saveUser(userDTO);
            //在拦截器中刷新该hash的有效期（每次有请求过来都刷新）——放到另外一个拦截器
            //stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);
            stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL, TimeUnit.MINUTES);
            return true;
        }

    //该方法在每个请求结束后会被调用
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
        //移除线程中的用户，保证不发生内存泄漏
    }


}

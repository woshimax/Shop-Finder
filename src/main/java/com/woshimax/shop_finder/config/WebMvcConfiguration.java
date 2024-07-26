package com.woshimax.shop_finder.config;

import com.woshimax.shop_finder.utils.LoginInterceptor;
import com.woshimax.shop_finder.utils.RefreshInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    //这是mvc的内容，我们自己写好了mvc的组件（重写），比如拦截器，需要写个配置类实现mvc配置器，然后重写方法启用mvc的组件
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/login",
                "/user/code",
                "shop/**",
                "blog/hot",
                "voucher/**",
                "upload/**",
                "shop-type/**"
        ).order(1);//启用拦截器，配置拦截器路径

        registry.addInterceptor(new RefreshInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }

}

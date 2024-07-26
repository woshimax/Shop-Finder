package com.woshimax.shop_finder.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {//RedissonClient的 工厂类，配置的是redissonClient并创建对象，加入IOC容器
    @Bean
    public RedissonClient redissonClient(){
        //配置redisson
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("lyh123456");//集群服务器的情况下可以用.useClusterServers()配置

        //
        return Redisson.create(config);//传入配置信息，返回一个由工厂类配置好的RedissonClient对象
    }
}

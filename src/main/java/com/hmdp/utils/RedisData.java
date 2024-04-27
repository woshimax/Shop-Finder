package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {//装饰器模式——继承的替代方案，扩展原来shop的功能，让他拥有逻辑过期时间属性，能够用于处理缓存击穿
    private LocalDateTime expireTime;
    private Object data;
}

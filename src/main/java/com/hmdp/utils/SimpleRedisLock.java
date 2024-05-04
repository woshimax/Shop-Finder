package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;//锁的name由外部传入，不写死，共给不同业务用
    private StringRedisTemplate stringRedisTemplate;
    private final static String KEY_PREFIX = "lock:";


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        //锁的key是代表业务名，value则是当前线程的特征属性（线程唯一）
        String threadName = Thread.currentThread().getName();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name, threadName, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);//防止拆箱时出现空指针——null的封装类拆箱会变成空指针
    }

    @Override
    public void unLock() {
        stringRedisTemplate.delete(KEY_PREFIX+name);
    }
}

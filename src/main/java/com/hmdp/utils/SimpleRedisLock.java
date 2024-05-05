package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;//锁的name由外部传入，不写死，共给不同业务用
    private StringRedisTemplate stringRedisTemplate;
    private final static String KEY_PREFIX = "lock:";
    private final static String ID_PREFIX = UUID.randomUUID().toString() + "-";


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        //锁的key是代表业务名，value则是当前线程的特征属性（线程唯一）
        String threadName = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name, threadName, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);//防止拆箱时出现空指针——null的封装类拆箱会变成空指针
    }

    @Override
    public void unLock() {
        //判断当前线程的标识和锁里存的线程标识是否一致——一致代表是当前线程的锁
        //解决一个问题：防止其他线程释放当前线程的锁
        //问题场景：比如一个线程阻塞，业务未完成锁自动释放，之后别的线程创建锁被我这个阻塞完成的线程释放了
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(threadId.equals(id)){
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }

    }
}

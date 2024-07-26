package com.woshimax.shop_finder.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;//锁的name由外部传入，不写死，共给不同业务用
    private StringRedisTemplate stringRedisTemplate;
    private final static String KEY_PREFIX = "lock:";
    private final static String ID_PREFIX = UUID.randomUUID().toString() + "-";
    private final static DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //在静态代码块中new，只需要加载一次资源，不需要每次unlock都加载资源造成性能浪费
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();

        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


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
        //调用lua脚本——一批redis操作具有原子性

        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
                );
        //一行代码解决——保证原子性
        //区别与下面都判断和delete分成两行，可能中间出现阻塞而出现锁误删
    }


    //    @Override
//    public void unLock() {
//        //判断当前线程的标识和锁里存的线程标识是否一致——一致代表是当前线程的锁
//        //解决一个问题：防止其他线程释放当前线程的锁
//        //问题场景：比如一个线程阻塞，业务未完成锁自动释放，之后别的线程创建锁被我这个阻塞完成的线程释放了
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if(threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
//
//    }
}

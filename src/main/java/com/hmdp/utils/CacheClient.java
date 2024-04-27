package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;


//基于StringRedisTemplate来封装一个缓存工具类
//包含专门处理缓存相关问题的方法
//包括：1、处理热点key缓存击穿问题的设置逻辑超时操作（java对象序列化为json）+2、利用逻辑超时处理缓存击穿（json反序列化为java对象）
//3、java对象序列化为json，设置ttl
//4.用缓存空值方式解决缓存穿透问题
@Component
public class CacheClient {

    static final Long CACHE_NULL_TTL = 2L;
    //线程池——防止大量的thread创建和销毁
    private static final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(10,
            10,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(10));
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //一般的存redis缓存的对象，带有ttl
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    //存redis操作之——存处理缓存击穿问题到带有逻辑过期时间的对象
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        //封装过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //存空值，处理缓存穿透
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,
                                         Long time,TimeUnit timeUnit){
        String key = keyPrefix + id;
        //查询操作加入redis，也就是查redis，没有去数据库找；找到则加载到redis，并返回，没找到则fail
        //1、查询redis
        String json = stringRedisTemplate.opsForValue().get(key);
        //2、判断redis是否存在
        if(StrUtil.isNotBlank(json)){
            //不为空值直接返回
            return JSONUtil.toBean(json,type);
        }
        //为空值还是真没有这个东西
        if("".equals(json)) return null;

        //3、redis不存在则查找数据库
        //用redis中添加null值的方式来处理缓存穿透的问题
        R r = dbFallBack.apply(id);//Function是个有参数有返回值的方法对象，用的时候传入方法名即可
        if(r == null){
            //数据库中不存在，往redis中设置超时自动删除的null
            this.set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }

        //4、从数据库中加载到redis中（加上延时删除）
        this.set(key,JSONUtil.toJsonStr(r),time, timeUnit);
        //stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),time, timeUnit);
        //5、直接返回数据库中查到到信息
        //注：其实也就是数据库中查到信息两个作用：一个是存入redis，一个是反给前端
        return r;
    }


    //缓存击穿逻辑超时操作封装

    public <R,ID>R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,
                                          Long time,TimeUnit timeUnit) {
        String key = keyPrefix + id;
        //查询操作加入redis，也就是查redis，没有去数据库找；找到则加载到redis，并返回，没找到则fail
        //1、查询redis
        String json = stringRedisTemplate.opsForValue().get(key);
        //2、判断redis是否存在
        if (StrUtil.isBlank(json)) {
            //未命中返回null
            return null;
        }
        //命中则判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //未过期，直接返回当前店铺信息
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期
            return r;
        }
        //已过期：需要缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        //判断有无锁
        boolean isLock = tryLock(lockKey);

        if (isLock) {
            //有锁先返回当前店铺信息，再开一个线程，在里面读数据库并更新redis

            threadPoolExecutor.execute(() -> {
                //只要碰到上锁问题，try-catch-finally搞起
                try {
                    R r1 = dbFallback.apply(id);
                    //将新数据写入redis
                    this.setWithLogicalExpire(key, r1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        //无锁，直接返回当前店铺信息
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.engine.velocity.SimpleStringResourceLoader;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //设置null值处理缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,
               id2->getById(id2),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁方式处理缓存击穿问题
        //Shop shop = queryWithMutex(id);

        //设置逻辑超时的方式处理缓存击穿问题——注意：这种方式默认不发生缓存穿透（默认redis中有东西——逻辑过期，但是数据存在） ，不存null值
        //解释一下默认有东西：一般这种热key都会通过后台提前加入redis，因此默认redis是存了我们要的数据的，没有就是真没有

        //两种处理击穿问题都需要有互斥锁这个东西，只不过一个是得阻塞等待处理完成——互斥锁处理；
        //另一个则是没锁的不等待，直接返回旧信息；由有锁的去更新信息——这个操作要在一个新线程里处理
//        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,
//                id2->getById(id2),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if(shop == null) return Result.fail("商铺不存在");
        return Result.ok(shop);
    }


    /*
    public Shop queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //查询操作加入redis，也就是查redis，没有去数据库找；找到则加载到redis，并返回，没找到则fail
        //1、查询redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2、判断redis是否存在
        if (StrUtil.isBlank(shopJson)) {
            //未命中返回null
            return null;
        }
        //命中则判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //未过期，直接返回当前店铺信息
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期
            return shop;
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
                    Shop shop1 = getById(id);
                    cacheClient.setWithLogicalExpire(key, shop1, 20L, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
           // return shop;这里不需要再写return，因为按顺序执行后面的return shop了
        }

        //无锁，直接返回当前店铺信息
        return shop;
    }



     */

   //缓存穿透处理封装到redis缓存工具类

    /*

    public Shop queryWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        //查询操作加入redis，也就是查redis，没有去数据库找；找到则加载到redis，并返回，没找到则fail
        //1、查询redis
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2、判断redis是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //不为空值直接返回
            return JSONUtil.toBean(shopJson,Shop.class);
        }
        //为空值还是真没有这个东西
        if("".equals(shopJson)) return null;

        //3、redis不存在则查找数据库
        //用redis中添加null值的方式来处理缓存穿透的问题
        Shop shop = getById(id);
        if(shop == null){
            //数据库中不存在，往redis中设置超时自动删除的null
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //4、从数据库中加载到redis中（加上延时删除）
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //5、直接返回数据库中查到到信息
        //注：其实也就是数据库中查到信息两个作用：一个是存入redis，一个是反给前端
        return shop;
    }
    */

    /*


    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    */
    @Override
    @Transactional//这里的@Transactional注意，控制方法方便回滚
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不为空");
        }
        //先更新数据库，再删除缓存
        //1、更新数据库
        updateById(shop);
        //2、删除缓存

        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+ id);
        return Result.ok();
    }
}

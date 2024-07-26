package com.woshimax.shop_finder.service.impl;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.woshimax.shop_finder.dto.Result;
import com.woshimax.shop_finder.entity.Shop;
import com.woshimax.shop_finder.mapper.ShopMapper;
import com.woshimax.shop_finder.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.woshimax.shop_finder.utils.CacheClient;
import com.woshimax.shop_finder.utils.RedisConstants;

import com.woshimax.shop_finder.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author woshimax
 * @since 2024-3
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //设置null值处理缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class,
                this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁方式处理缓存击穿问题
        //Shop shop = queryWithMutex(id);

        //设置逻辑超时的方式处理缓存击穿问题——注意：这种方式默认不发生缓存穿透（默认redis中有东西——逻辑过期，但是数据存在） ，不存null值
        //解释一下默认有东西：一般这种热key都会通过后台提前加入redis，因此默认redis是存了我们要的数据的，没有就是真没有

        //两种处理击穿问题都需要有互斥锁这个东西，只不过一个是得阻塞等待处理完成——互斥锁处理；
        //另一个则是没锁的不等待，直接返回旧信息；由有锁的去更新信息——这个操作要在一个新线程里处理
//        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY,id,Shop.class,
//                id2->getById(id2),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (shop == null) return Result.fail("商铺不存在");
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
        if (id == null) {
            return Result.fail("店铺id不为空");
        }
        //先更新数据库，再删除缓存
        //1、更新数据库
        updateById(shop);
        //2、删除缓存

        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            //不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            //返回数据
            return Result.ok(page.getRecords());
        }

        //2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //3.查询 Redis 、按照距离排序、分页。结果：shopId、distance
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        //GEOSEARCH key BYLONLAT(表示根据经纬度查找) x y BYRADIUS 10 WITHDISTANCE
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        //4.解析出id
        //附近没有商家
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        //现在只是查找出了[0,end]的数据，后面需要跳到 from 的位置，截取 from-end 的部分
        //而如果集合大小比 from 都要小，那么后面跳到 from 的位置会拿不到数据
        if (list.size() <= from) {
            //没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        //4.1截取 from - end 的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        //直接跳到我们想要读取的数据的开始位置
        list.stream().skip(from).forEach(result -> {
            //4.2获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //4.3获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        //5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            //设置每个商铺距离当前经纬度的距离
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6.返回分页查询结果
        return Result.ok(shops);
    }
}

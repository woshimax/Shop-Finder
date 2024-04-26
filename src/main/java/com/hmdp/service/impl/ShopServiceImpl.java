package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.template.engine.velocity.SimpleStringResourceLoader;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
    @Override
    public Result queryById(Long id) {
        //查询操作加入redis，也就是查redis，没有去数据库找；找到则加载到redis，并返回，没找到则fail
        //1、查询redis
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
        //2、判断redis是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //不为空直接返回
            return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
        }
        //3、redis不存在则查找数据库
        Shop shop = getById(id);
        if(shop == null){
            //数据库都不存在直接返回错误信息
            return Result.fail("不存在商户信息");
        }
        //4、从数据库中加载到redis中（加上延时删除）
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //5、直接返回数据库中查到到信息
        //注：其实也就是数据库中查到信息两个作用：一个是存入redis，一个是反给前端

        return Result.ok(shop);
    }

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

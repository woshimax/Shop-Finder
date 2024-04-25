package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryShopTypeList() {
        List<ShopType> shopTypeList = new ArrayList<>();
        //先查redis
        for(int j = 1;j <= RedisConstants.SHOP_TYPE;j++){

            String shopTypeJSON = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY + j);
            if(j == 1 && shopTypeJSON == null){
                //说明redis没有
                break;
            }
            ShopType shoptype = JSONUtil.toBean(shopTypeJSON, ShopType.class);
            shopTypeList.add(shoptype);
        }
        //判断redis有无
        if(!shopTypeList.isEmpty()){
            //有的话，转成list对象返回
            return shopTypeList;
        }
        //如果没有则查数据库
        List<ShopType> shopTypeListLib = query().orderByAsc("sort").list();
        if(shopTypeListLib == null){
            //没查到
            return null;
        }
        //如果查到了：1、加入redis
        int i = 1;
        for(ShopType shoptype:shopTypeListLib){
            String shopTypeJs = JSONUtil.toJsonStr(shoptype);
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY + i++,shopTypeJs);
        }

        //2、返回list
        return shopTypeListLib;
    }
}

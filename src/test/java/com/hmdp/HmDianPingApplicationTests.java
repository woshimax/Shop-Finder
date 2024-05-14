package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
public class HmDianPingApplicationTests {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IShopService shopService;

    @Test
    void loadShop(){
        //查询店铺信息
        List<Shop> list = shopService.list();
        //利用stream将list转map
        //根据typeId分类，key为typeId，value为店铺的list，List为——这里转map是为了后面方便分类处理
        Map<Long,List<Shop>> map = list.stream().collect(Collectors.groupingBy(shop->shop.getTypeId()));
        //分批完成写入redis
        for(Map.Entry<Long,List<Shop>> entry:map.entrySet()){
            Long typeId = entry.getKey();
            //用typeId来作为geo的key
            String key = "shop:geo" + typeId;
            //获取同类店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for(Shop shop:value){
                locations.add(new RedisGeoCommands.GeoLocation<>(//这个是封装完的东西地点类型——name+xy坐标
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            //按typeId来一类一类丢进geo（geo的value又是kv结构——shopid-point经纬度）
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }


}

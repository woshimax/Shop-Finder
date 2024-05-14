package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Holder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;
    @Override
    public Result follow(Long followUserId,Boolean isFollow) {
        //判断是关注or取关
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOWS_KEY + userId;

        if(isFollow){
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            //这种先操作数据库再操作redis的操作都需要判断成功与否，成功保存/删除数据库，才对redis操作
            boolean isSuccess = save(follow);
            if(isSuccess){

                //存入redis的set中，方便后查看共同关注时用set求交集
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }

        }else{
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());

            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();

        Integer count = query().eq("follow_user_id", followUserId).eq("user_id", userId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommon(Long id) {
        Long currentUserId = UserHolder.getUser().getId();
        String currentUserKey = RedisConstants.FOLLOWS_KEY + currentUserId;
        String targetUserId = RedisConstants.FOLLOWS_KEY + id;
        //intersect求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(currentUserKey, targetUserId);
        if(intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //固定流程：放到stream流中，用map转型，然后collect输出到list
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(users);
    }
}

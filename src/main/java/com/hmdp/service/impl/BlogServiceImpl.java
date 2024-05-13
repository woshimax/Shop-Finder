package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryBlogById(Long id) {
        //1、查询blog
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        //2、查询blog相关用户信息封装到blog里
        queryUser(blog);
        //3、查询是否点赞——前端根据查询结果高亮点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //获得当前用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            //用户未登陆，无需查询当前用户是会否点赞
            return;
        }
        Long userId = user.getId();
        //判断当前用户是否点过赞
        String blogKey = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(blogKey, userId.toString());

        blog.setIsLike(score != null);


    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            queryUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result updateLike(Long id) {
        //获得当前用户
        Long userId = UserHolder.getUser().getId();
        //判断当前用户是否点过赞
        String blogKey = RedisConstants.BLOG_LIKED_KEY + id;
        //根据业务改变技术：用zscore判断是否存在
        Double score = stringRedisTemplate.opsForZSet().score(blogKey, userId.toString());
        //Boolean isLike = stringRedisTemplate.opsForSet().isMember(blogKey, userId.toString());
        if(score == null){
            //没点过赞，更新数据库+redis中set用户信息
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //做操作前都想想要不要判断——更新数据库成功才能移除用户——zset key value score（这个业务用时间戳）
            if(isSuccess) stringRedisTemplate.opsForZSet().add(blogKey,userId.toString(),System.currentTimeMillis());
        }else{
            //点过赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isSuccess)stringRedisTemplate.opsForZSet().remove(blogKey, userId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String blogKey = RedisConstants.BLOG_LIKED_KEY + id;
        //zscore：根据score查前五的key
        Set<String> range = stringRedisTemplate.opsForZSet().range(blogKey, 0, 4);
        if(range == null || range.isEmpty()) return Result.ok(Collections.emptyList());
        //解析为user
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据用户id查询用户 where id in(?,?) order by field(id,?,?)——数据库默认会根据id顺序排序，我们通过order by field指定排序顺序
        //传入自定义string（我们自己定义的排序顺序）
        String idStr = StrUtil.join(",",ids);

        List<UserDTO> userDTOs = userService.query().
                in("id",ids).last("ORDER BY FIELD(id," + idStr + ")").list().//last是在sql语句末添加自己写的语句
                stream().
                map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOs);
    }

    public void queryUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}


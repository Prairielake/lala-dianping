package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Duration duration) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), duration);
    }

    public void setWithLogicalExpire(String key, Object value, Duration duration) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(duration.toSeconds()));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Duration duration) {
        //从Redis查询缓存
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, type);
        }
        if (shopJson != null) {
            return null;
        }
        //不存在 根据id查询数据库
        R r = dbFallBack.apply(id);
        //不存在数据库 返回错误
        if (r == null) {
            //存入NULL 防止恶意访问
            stringRedisTemplate.opsForValue().set(key, "", Duration.ofMinutes(CACHE_NULL_TTL));
            return null;
        }
        //存在 写入Redis
        this.set(key, r, duration);
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Duration duration) {
        //从Redis查询缓存
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //命中 先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期 返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        //已过期 需要缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        /*缓存重建：
         1.获取互斥锁；
         2.判断获取锁是否成功；
         3.成功，开启独立线程 ；
         4.返回过期的商铺信息*/
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key, r1, duration);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return r;
    }

    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(LOCK_SHOP_TTL));
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}

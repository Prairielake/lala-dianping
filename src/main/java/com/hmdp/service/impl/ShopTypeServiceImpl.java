package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lfy
 * @since 2025-3-18
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopTypeService typeService;

    @Override
    public Result queryList() {
        //查询Redis中数据
        String key =  CACHE_SHOPTYPE_KEY;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //有则返回
        if (StrUtil.isNotBlank(shopJson)) {
            List<ShopType> shopType = BeanUtil.copyToList(JSONUtil.parseArray(shopJson), ShopType.class);
            return Result.ok(shopType);
        }
        //无则查询数据库
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        //数据库无则返回
        if (CollUtil.isEmpty(typeList)) {
            return Result.fail("店铺类型信息异常");
        }
        //有则保存至Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));
        return Result.ok(typeList);
    }
}

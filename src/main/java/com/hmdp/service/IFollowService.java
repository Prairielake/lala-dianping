package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lfy
 * @since 2025-3-18
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean idFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}

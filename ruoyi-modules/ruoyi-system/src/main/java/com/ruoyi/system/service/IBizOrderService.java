package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.BizOrder;

/**
 * 订单管理 服务层
 *
 * @author ruoyi
 */
public interface IBizOrderService
{
    BizOrder selectBizOrderById(Long orderId);

    List<BizOrder> selectBizOrderList(BizOrder bizOrder);

    int insertBizOrder(BizOrder bizOrder);

    int updateBizOrder(BizOrder bizOrder);

    int deleteBizOrderByIds(Long[] ids);
}

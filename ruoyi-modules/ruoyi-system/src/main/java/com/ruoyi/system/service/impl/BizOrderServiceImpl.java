package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.domain.BizOrder;
import com.ruoyi.system.mapper.BizOrderMapper;
import com.ruoyi.system.service.IBizOrderService;

/**
 * 订单管理 服务层实现
 *
 * @author ruoyi
 */
@Service
public class BizOrderServiceImpl implements IBizOrderService
{
    @Autowired
    private BizOrderMapper bizOrderMapper;

    @Override
    public BizOrder selectBizOrderById(Long orderId)
    {
        return bizOrderMapper.selectBizOrderById(orderId);
    }

    @Override
    public List<BizOrder> selectBizOrderList(BizOrder bizOrder)
    {
        return bizOrderMapper.selectBizOrderList(bizOrder);
    }

    @Override
    public int insertBizOrder(BizOrder bizOrder)
    {
        if (bizOrder == null || StringUtils.isEmpty(bizOrder.getOrderNo()))
        {
            throw new ServiceException("订单号不能为空");
        }
        if (bizOrder.getQuantity() == null)
        {
            bizOrder.setQuantity(1);
        }
        if (StringUtils.isEmpty(bizOrder.getStatus()))
        {
            bizOrder.setStatus("0");
        }
        return bizOrderMapper.insertBizOrder(bizOrder);
    }

    @Override
    public int updateBizOrder(BizOrder bizOrder)
    {
        if (bizOrder == null || bizOrder.getOrderId() == null)
        {
            throw new ServiceException("订单ID不能为空");
        }
        return bizOrderMapper.updateBizOrder(bizOrder);
    }

    @Override
    public int deleteBizOrderByIds(Long[] ids)
    {
        return bizOrderMapper.deleteBizOrderByIds(ids);
    }
}

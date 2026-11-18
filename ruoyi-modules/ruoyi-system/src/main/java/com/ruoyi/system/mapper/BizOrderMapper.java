package com.ruoyi.system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BizOrder;

/**
 * 订单管理 数据层
 *
 * @author ruoyi
 */
@Mapper
public interface BizOrderMapper
{
    BizOrder selectBizOrderById(@Param("orderId") Long orderId);

    /** 按订单号精确查询（供智能客服「问订单号查状态」使用） */
    BizOrder selectBizOrderByOrderNo(@Param("orderNo") String orderNo);

    List<BizOrder> selectBizOrderList(BizOrder bizOrder);

    int insertBizOrder(BizOrder bizOrder);

    int updateBizOrder(BizOrder bizOrder);

    int deleteBizOrderByIds(@Param("ids") Long[] ids);
}

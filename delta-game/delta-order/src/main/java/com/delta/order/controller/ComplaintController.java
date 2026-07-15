package com.delta.order.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.utils.ImageListUtils;
import com.delta.order.entity.Complaint;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderProgress;
import com.delta.order.service.ComplaintService;
import com.delta.order.service.OrderService;
import com.delta.order.service.OrderProgressService;
import com.delta.order.dto.ComplaintDetailVO;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.event.BusinessEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order/complaint")
@RequiredArgsConstructor
public class ComplaintController {
    private final ComplaintService complaintService;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderProgressService orderProgressService;
    private final CrossModuleMapper crossModuleMapper;

    @PostMapping
    public R<Void> create(@RequestBody Complaint complaint) {
        Long userId = SecurityUtils.getUserId();

        // 1. 校验订单存在且属于当前用户
        Order order = orderService.getById(complaint.getOrderId());
        if (order == null) return R.fail("订单不存在");
        if (!order.getUserId().equals(userId)) return R.fail("无权操作");

        // 2. 校验订单状态
        String status = order.getStatus();
        if (!"IN_PROGRESS".equals(status) && !"COMPLETED".equals(status)) {
            return R.fail("当前订单状态不允许投诉");
        }

        // 3. 校验是否已有进行中的投诉
        long existCount = complaintService.count(new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getOrderId, complaint.getOrderId())
                .in(Complaint::getStatus, "PENDING", "PROCESSING"));
        if (existCount > 0) return R.fail("该订单已有进行中的投诉");

        // 4. 从订单获取打手ID
        complaint.setUserId(userId);
        complaint.setPlayerId(order.getPlayerId());
        complaint.setImages(ImageListUtils.normalize(complaint.getImages()));
        complaint.setStatus("PENDING");
        complaintService.save(complaint);

        // 5. 更新订单状态为DISPUTED
        orderService.disputeOrder(complaint.getOrderId());
        // 6. 通知客服新投诉待处理
        eventPublisher.publishEvent(new BusinessEvent(this, "NEW_COMPLAINT",
                "CS", null, complaint.getOrderId(), "新投诉待处理，订单ID: " + complaint.getOrderId()));
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<ComplaintDetailVO> detail(@PathVariable Long id) {
        Complaint c = complaintService.getById(id);
        if (c == null) return R.fail("投诉不存在");
        if (!c.getUserId().equals(SecurityUtils.getUserId())) return R.fail("无权查看");

        Order order = orderService.getById(c.getOrderId());
        ComplaintDetailVO vo = new ComplaintDetailVO();
        vo.setComplaint(c);
        vo.setOrder(order);

        if (order != null) {
            vo.setUserNickname(crossModuleMapper.selectUserNickname(order.getUserId()));
            vo.setUserAvatar(crossModuleMapper.selectUserAvatar(order.getUserId()));
            if (order.getPlayerId() != null) {
                vo.setPlayerNickname(crossModuleMapper.selectPlayerNickname(order.getPlayerId()));
                vo.setPlayerAvatar(crossModuleMapper.selectPlayerAvatar(order.getPlayerId()));
            }
            java.util.List<OrderProgress> progress = orderProgressService.list(
                    new LambdaQueryWrapper<OrderProgress>()
                            .eq(OrderProgress::getOrderId, order.getId())
                            .orderByAsc(OrderProgress::getCreatedAt)
            );
            vo.setProgress(progress);
        }

        return R.ok(vo);
    }

    @GetMapping("/my")
    public R<Page<Complaint>> my(PageQuery query) {
        return R.ok(complaintService.page(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Complaint>().eq(Complaint::getUserId, SecurityUtils.getUserId())
                        .orderByDesc(Complaint::getCreatedAt)));
    }

    @PostMapping("/{id}/appeal")
    public R<Void> appeal(@PathVariable Long id, @RequestBody Complaint appeal) {
        Complaint c = complaintService.getById(id);
        if (c == null) return R.fail("投诉不存在");
        if (!c.getUserId().equals(SecurityUtils.getUserId())) return R.fail("无权操作");
        if (!"RESOLVED".equals(c.getStatus())) return R.fail("当前状态不允许申诉");
        c.setAppealReason(appeal.getAppealReason());
        c.setAppealImages(ImageListUtils.normalize(appeal.getAppealImages()));
        c.setStatus("APPEALING");
        complaintService.updateById(c);
        return R.ok();
    }
}

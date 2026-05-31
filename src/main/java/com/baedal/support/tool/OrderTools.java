package com.baedal.support.tool;

import com.baedal.support.domain.Order;
import com.baedal.support.domain.OrderMockService;
import com.baedal.support.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 배달 상담 에이전트가 사용할 Tool 묶음.
 * <p>
 * 설계 원칙:
 * <ul>
 *     <li>@Tool의 {@code description}은 LLM이 읽는 "API 문서"다. 한국어로 명확히 작성한다.</li>
 *     <li>각 Tool은 실패 상황을 예외가 아닌 "결과 값"으로 표현한다.
 *         예외를 던지면 LLM이 Fallback할 기회를 잃는다.</li>
 *     <li>{@link #cancelOrder(String, String)}는 <b>멱등(idempotent)</b>하게 설계한다.
 *         이미 취소된 주문을 다시 취소 요청해도 동일한 성공 응답을 돌려준다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTools {

    private final OrderMockService orderService;

    @Tool(description = """
            # 주문 상세 정보 조회
            고객이 주문한 메뉴, 금액, 현재 주문 상태, 예상 배달 시간을 물어볼 때 호출한다.
            존재하지 않는 주문번호이면 null을 반환한다.
            """
    )
    public OrderDetailView getOrderDetail(
            @ToolParam(description = "조회할 주문번호. 'YYYY-XXXX' 형식 (예: 2024-1234)") String orderId
    ) {
        log.info("[Tool] getOrderDetail(orderId={})", orderId);
        return orderService.findById(orderId)
                .map(this::toDetailView)
                .orElse(null);
    }

    @Tool(description = """
            # 배달 현황 및 라이더 위치 조회
            고객이 배달이 어디쯤 왔는지, 라이더 위치를 물어볼 때 호출한다.
            라이더 위치(riderLocation)는 배달 중(DELIVERING) 상태일 때만 유효하며, 그 외 상태에서는 null일 수 있다.
            존재하지 않는 주문번호이면 null을 반환한다.
            """)
    public DeliveryStatusView getDeliveryStatus(
            @ToolParam(description = "조회할 주문번호. 'YYYY-XXXX' 형식 (예: 2024-1234)") String orderId
    ) {
        log.info("[Tool] getDeliveryStatus(orderId={})", orderId);
        return orderService.findById(orderId)
                .map(this::toDeliveryView)
                .orElse(null);
    }

    @Tool(description = """
             # 주문 취소
             고객이 명시적으로 취소를 요청할 때 호출한다.
             반드시 툴을 호출하고, 반환된 결과의 message 필드를 그대로 고객에게 전달한다.

             outcome별 고객 안내:
             | outcome          | 고객 안내                              |
             |------------------|----------------------------------------|
             | CANCELED         | 주문이 취소되었습니다.                  |
             | NOT_CANCELABLE   | 조리가 시작된 이후에는 취소할 수 없습니다. |
             | ALREADY_CANCELED | 이미 취소된 주문입니다.                 |
             | NOT_FOUND        | 존재하지 않는 주문번호입니다.           |
            """)
    public CancelOrderResult cancelOrder(
            @ToolParam(description = "취소할 주문번호. 'YYYY-XXXX' 형식 (예: 2024-1234)") String orderId,
            @ToolParam(description = "취소 사유. 고객이 말한 사유 또는 대화 내용 요약. 명시적으로 말하지 않은 경우 '고객 요청'으로 기입한다.") String reason
    ) {
        log.info("[Tool] cancelOrder(orderId={}, reason={})", orderId, reason);
        Optional<Order> optional = orderService.findById(orderId);
        if (optional.isEmpty()) {
            return CancelOrderResult.of(orderId, CancelOrderResult.Outcome.NOT_FOUND);
        }
        Order order = optional.get();
        if (order.status() == OrderStatus.CANCELED) {
            return CancelOrderResult.of(orderId, CancelOrderResult.Outcome.ALREADY_CANCELED);
        }
        if (!order.isCancelable()) {
            return CancelOrderResult.of(orderId, CancelOrderResult.Outcome.NOT_CANCELABLE);
        }
        order.cancel(reason, LocalDateTime.now());
        return CancelOrderResult.of(orderId, CancelOrderResult.Outcome.CANCELED);
    }

    // ------- 변환기 (참고용 — 수정할 필요 없음) -------

    private OrderDetailView toDetailView(Order order) {
        var lines = order.items().stream()
                .map(i -> new OrderDetailView.Line(i.menuName(), i.quantity(), i.unitPrice()))
                .toList();
        return new OrderDetailView(
                order.orderId(),
                order.storeName(),
                lines,
                order.totalAmount(),
                order.status().name(),
                order.orderedAt(),
                order.estimatedDeliveryAt()
        );
    }

    private DeliveryStatusView toDeliveryView(Order order) {
        String message = switch (order.status()) {
            case CREATED, ACCEPTED -> "아직 조리가 시작되지 않았습니다.";
            case COOKING -> "현재 조리 중입니다.";
            case DELIVERING -> "라이더가 배달 중입니다.";
            case DELIVERED -> "배달이 완료되었습니다.";
            case CANCELED -> "취소된 주문입니다.";
        };
        return new DeliveryStatusView(
                order.orderId(),
                order.status().name(),
                order.riderLocation(),
                order.estimatedDeliveryAt(),
                message
        );
    }
}

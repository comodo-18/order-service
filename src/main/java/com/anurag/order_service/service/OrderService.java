package com.anurag.order_service.service;

import com.anurag.order_service.dto.OrderRequest;
import com.anurag.order_service.dto.OrderResponse;
import com.anurag.order_service.entity.Order;
import com.anurag.order_service.entity.User;
import com.anurag.order_service.event.OrderPlacedEvent;
import com.anurag.order_service.producer.OrderEventProducer;
import com.anurag.order_service.repository.OrderRepository;
import com.anurag.order_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;
    private final OrderEventProducer orderEventProducer;

    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;

    public OrderResponse placeOrder(String username, OrderRequest request) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Step 1: Reserve stock via InventorySync (synchronous REST)
        boolean reserved = reserveStock(request.getProductId(), request.getQuantity());

        if (!reserved) {
            throw new RuntimeException(
                    "Could not reserve stock — insufficient inventory for product " + request.getProductId());
        }

        // Step 2: Stock reserved — create the order
        Order order = Order.builder()
                .userId(user.getId())
                .productId(request.getProductId())
                .variantId(request.getVariantId())
                .quantity(request.getQuantity())
                .status("CONFIRMED")
                .build();

        Order saved = orderRepository.save(order);

        // Step 3: Publish order-placed event to Kafka (async, non-blocking)
        // Order is already confirmed — Kafka failure does NOT rollback the order
        orderEventProducer.publishOrderPlaced(OrderPlacedEvent.builder()
                .orderId(saved.getId())
                .userId(user.getId())
                .productId(saved.getProductId())
                .quantity(saved.getQuantity())
                .status(saved.getStatus())
                .placedAt(saved.getCreatedAt())
                .build());

        return mapToResponse(saved);
    }

    public List<OrderResponse> getMyOrders(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return orderRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private boolean reserveStock(Long productId, int quantity) {
        try {
            String url = inventoryServiceUrl
                    + "/api/inventory/reserve?productId=" + productId
                    + "&quantity=" + quantity;

            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);

            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            // InventorySync returned 400 (insufficient stock)
            return false;
        } catch (Exception e) {
            throw new RuntimeException("InventorySync service unavailable: " + e.getMessage());
        }
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .productId(order.getProductId())
                .variantId(order.getVariantId())
                .quantity(order.getQuantity())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }
}

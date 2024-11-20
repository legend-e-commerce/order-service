package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderDto;
import com.example.orderservice.entity.OrderEntity;
import com.example.orderservice.meaagequeue.KafkaProducer;
import com.example.orderservice.meaagequeue.OrderProducer;
import com.example.orderservice.vo.RequestOrder;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.vo.ResponseOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/order-service")
@Slf4j
public class OrderController {

    private final Environment env;
    private final OrderService orderService;
    private final KafkaProducer kafkaProducer;
    private final OrderProducer orderProducer;

    @GetMapping("/health_check")
    public String status() {
        return String.format("건강해요 PORT: %s", env.getProperty("local.server.port"));
    }

    @PostMapping("/{userId}/orders")
    public ResponseEntity<ResponseOrder> createOrder (
            @PathVariable("userId") String userId,
            @RequestBody RequestOrder requestOrder
    ) {

        log.info("Before added orders data");

        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);


        OrderDto orderDto = mapper.map(requestOrder, OrderDto.class);
        orderDto.setUserId(userId);
        // JPA
//        OrderDto createdOrder = orderService.createOrder(orderDto);
//        ResponseOrder result = mapper.map(createdOrder, ResponseOrder.class);
        
        // Kafka Sink Connector
        orderDto.setOrderId(UUID.randomUUID().toString());
        orderDto.setTotalPrice(requestOrder.getUnitPrice() * requestOrder.getQty());

        // send this order to the kafka
        kafkaProducer.send("example-catalog-topic", orderDto);
        orderProducer.send("orders", orderDto);

        ResponseOrder result = mapper.map(orderDto, ResponseOrder.class);

        log.info("After added orders data");

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{userId}/orders")
    public ResponseEntity<List<ResponseOrder>> getOrders (
            @PathVariable("userId") String userId
    ) {
        log.info("Before recieve orders data");

        Iterable<OrderEntity> orderEntities = orderService.getOrdersByUserId(userId);
        List<ResponseOrder> result = new ArrayList<>();
        orderEntities.forEach(order -> result.add(new ModelMapper().map(order, ResponseOrder.class)));

        log.info("After recieve orders data");

        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

}

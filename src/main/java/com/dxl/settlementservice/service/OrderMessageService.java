package com.dxl.settlementservice.service;

import com.dxl.settlementservice.dao.SettlementMapper;
import com.dxl.settlementservice.dto.OrderMessageDTO;
import com.dxl.settlementservice.enumoperation.SettlementStatus;
import com.dxl.settlementservice.po.SettlementPO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeoutException;

/**
 * @author : dxl
 * @version: 2022/1/10  10:50
 */
@Slf4j
@Service
public class OrderMessageService {
    @Autowired
    SettlementMapper settlementMapper;

    @Async
    public void handleMessage() throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");
        try(Connection connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel()){
            channel.exchangeDeclare(
                    "exchange.order.settlement",
                    BuiltinExchangeType.FANOUT,
                    true,
                    false,
                    null);
            channel.queueDeclare("queue.settlement", true, true, false, null);
            channel.queueBind("queue.settlement", "exchange.order.settlement","key.settlement");
            channel.basicConsume("queue.settlement",true, deliveryCallback, consumerTag -> {});
            while (true){
                Thread.sleep(1000000);
            }
        }catch (Exception e){
            log.error(e.getMessage());
        }
    }
    DeliverCallback deliveryCallback = ((consumerTag, message) -> {
        final ObjectMapper objectMapper = new ObjectMapper();
        final OrderMessageDTO orderMessageDTO = objectMapper.readValue(message.getBody(), OrderMessageDTO.class);
        final SettlementPO settlementPO = new SettlementPO();
        settlementPO.setOrderId(orderMessageDTO.getOrderId());
        settlementPO.setAmount(orderMessageDTO.getPrice());
        settlementPO.setStatus(SettlementStatus.SUCCESS);
        settlementPO.setTransactionId(1);
        settlementPO.setDate(new Date());
        settlementMapper.insert(settlementPO);
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");

        orderMessageDTO.setSettlementId(settlementPO.getId());
        final String messageToSend = objectMapper.writeValueAsString(orderMessageDTO);
        try(Connection connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel()){
            channel.basicPublish("exchange.settlement.order",
                    "key.order",
                    null,
                    messageToSend.getBytes());
        }catch (Exception e){
            log.error(e.getMessage());
        }
    });
}

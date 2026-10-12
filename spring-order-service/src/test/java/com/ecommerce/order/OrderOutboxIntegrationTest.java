package com.ecommerce.order;

import com.ecommerce.order.dto.inventory.InventoryResponse;
import com.ecommerce.order.dto.request.*;
import com.ecommerce.order.dto.response.ProductResponse;
import com.ecommerce.order.notification.OutboxPublisher;
import com.ecommerce.order.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import java.sql.DriverManager;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"notifications.scheduling-enabled=false", "spring.jpa.hibernate.ddl-auto=create"},webEnvironment=SpringBootTest.WebEnvironment.NONE)
@org.springframework.test.annotation.DirtiesContext
@EmbeddedKafka(partitions=3,topics="orders.placed.v1",bootstrapServersProperty="spring.kafka.bootstrap-servers")
@EnabledIfEnvironmentVariable(named="TEST_POSTGRES_URL",matches=".+")
class OrderOutboxIntegrationTest {
    static final String schema="order_outbox_test_"+UUID.randomUUID().toString().replace("-","");
    static final String url=System.getenv("TEST_POSTGRES_URL");
    static final String user=System.getenv().getOrDefault("TEST_DB_USERNAME",System.getProperty("user.name"));
    static final String password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r)throws Exception {
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        r.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        r.add("spring.datasource.username",()->user);r.add("spring.datasource.password",()->password);
    }
    @Autowired OrderService orders;
    @Autowired OutboxPublisher publisher;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EmbeddedKafkaBroker broker;
    @MockBean ProductIntegrationService product;
    @Test void committedOrderPublishesButRolledBackOrderHasNoEvent()throws Exception {
        when(product.getProduct(10L)).thenReturn(new ProductResponse(10L,"SKU","Phone",null,new BigDecimal("10.00"),ProductResponse.ProductStatus.ACTIVE,null,null));
        when(product.getInventory(10L)).thenReturn(new InventoryResponse(10L,5,0,5));
        var request=new CreateOrderRequest(101L,List.of(new OrderItemRequest(10L,2)));
        new TransactionTemplate(transactions).execute(status->{orders.createOrder(request);status.setRollbackOnly();return null;});
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_notification_outbox",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)).isZero();
        orders.createOrder(request);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_notification_outbox WHERE published_at IS NULL",Integer.class)).isEqualTo(1);
        try(var consumer=new KafkaConsumer<String,String>(KafkaTestUtils.consumerProps("outbox-test","false",broker),new StringDeserializer(),new StringDeserializer())){
            broker.consumeFromAnEmbeddedTopic(consumer,"orders.placed.v1");
            publisher.publishNext();
            String payload=KafkaTestUtils.getSingleRecord(consumer,"orders.placed.v1").value();
            var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(json.get("customerId").asLong()).isEqualTo(101L);
            assertThat(json.get("totalAmount").decimalValue()).isEqualByComparingTo("20.00");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_notification_outbox WHERE published_at IS NOT NULL",Integer.class)).isEqualTo(1);
    }
    @AfterAll static void cleanup(@Autowired org.springframework.context.ConfigurableApplicationContext context)throws Exception {
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
}

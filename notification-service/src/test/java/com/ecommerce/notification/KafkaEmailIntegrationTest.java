package com.ecommerce.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties={"notifications.poll-ms=100", "spring.mail.properties.mail.smtp.auth=false", "spring.mail.properties.mail.smtp.starttls.enable=false", "spring.mail.properties.mail.smtp.starttls.required=false"})
@EmbeddedKafka(partitions=3, topics={"orders.placed.v1","orders.placed.v1.DLT"}, bootstrapServersProperty="spring.kafka.bootstrap-servers")
@org.springframework.test.annotation.DirtiesContext
@EnabledIfEnvironmentVariable(named="TEST_POSTGRES_URL",matches=".+")
class KafkaEmailIntegrationTest {
    static final String schema="notification_test_"+UUID.randomUUID().toString().replace("-","");
    static final String url=System.getenv("TEST_POSTGRES_URL");
    static final String user=System.getenv().getOrDefault("TEST_DB_USERNAME",System.getProperty("user.name"));
    static final String password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
    static ServerSocket smtp;
    static HttpServer customers;
    static final List<String> received=new CopyOnWriteArrayList<>();
    static final List<String> recipients=new CopyOnWriteArrayList<>();
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r)throws Exception {
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+schema);}
        smtp=new ServerSocket(0,50,InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(()->{while(!smtp.isClosed())try{var socket=smtp.accept();Thread.ofVirtual().start(()->serve(socket));}catch(IOException ignored){}});
        customers=HttpServer.create(new InetSocketAddress("localhost",0),0);
        customers.createContext("/api/customers/101",exchange->{
            byte[] body="{\"id\":101,\"email\":\"buyer@example.test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);exchange.close();});customers.start();
        r.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        r.add("spring.datasource.username",()->user);r.add("spring.datasource.password",()->password);
        r.add("spring.mail.host",()->"localhost");r.add("spring.mail.port",()->smtp.getLocalPort());
        r.add("services.customer-query.url",()->"http://localhost:"+customers.getAddress().getPort());
    }
    static void serve(Socket socket){
        try(socket;var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
            var out=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.UTF_8)){
            out.print("220 localhost SMTP ready\r\n");out.flush();String line;boolean data=false;StringBuilder body=new StringBuilder();
            while((line=in.readLine())!=null){
                if(data){if(line.equals(".")){received.add(body.toString());data=false;out.print("250 Accepted\r\n");out.flush();}else body.append(line).append('\n');continue;}
                if(line.startsWith("RCPT TO:"))recipients.add(line);
                if(line.equals("DATA")){data=true;out.print("354 Send message\r\n");}
                else if(line.equals("QUIT")){out.print("221 Bye\r\n");out.flush();break;}
                else out.print("250 OK\r\n");out.flush();
            }
        }catch(IOException ignored){}
    }
    @Test void kafkaEventSendsRealSmtpMessageAndReplayIsDeduplicated()throws Exception {
        var event=new OrderPlaced(UUID.randomUUID(),1,7L,"ORD-INTEGRATION",101L,new BigDecimal("20.00"),Instant.now());
        String payload=json.writeValueAsString(event);
        kafka.send("orders.placed.v1","7",payload).get(15,TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notification_inbox WHERE sent_at IS NOT NULL",Integer.class)).isEqualTo(1));
        kafka.send("orders.placed.v1","7",payload).get(15,TimeUnit.SECONDS);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(()->{
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_inbox",Integer.class)).isEqualTo(1);
            assertThat(received).hasSize(1);
        });
        assertThat(recipients).containsExactly("RCPT TO:<buyer@example.test>");
        assertThat(received.getFirst()).contains("ORD-INTEGRATION","placed successfully","20.00");
    }
    @AfterAll static void cleanup(@Autowired org.springframework.context.ConfigurableApplicationContext context)throws Exception {
        context.getBean(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class).destroy();
        context.getBean(org.springframework.kafka.config.KafkaListenerEndpointRegistry.class).stop();
        if(smtp!=null)smtp.close();if(customers!=null)customers.stop(0);
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
}

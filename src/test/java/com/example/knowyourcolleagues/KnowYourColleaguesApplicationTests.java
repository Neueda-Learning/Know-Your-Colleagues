package com.example.knowyourcolleagues;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "transaction.messaging.enabled=false")
class KnowYourColleaguesApplicationTests {

    @Test
    void contextLoads() {
    }

}

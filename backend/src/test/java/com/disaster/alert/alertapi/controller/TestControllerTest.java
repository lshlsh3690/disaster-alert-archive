package com.disaster.alert.alertapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TestControllerTest {

    @Autowired
    private TestController testController;

    @Test
    public void test() {
        // When
        String result = testController.test();

        // Then
        assertEquals("Hello, this is a test endpoint!", result);
    }
}
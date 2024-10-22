package ru.dushenkov.nginx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class HttpMethodTest extends AbstractHttpTest {


    @Override
    protected String getConfigContent() {

        return """
                events {}
                http {
                    server {
                        listen       %%default-port%%;
                        server_name  localhost;
                        location / {
                            return      200;
                        }
                        %%test-info%%
                    }
                }
                """;
    }


    @Test
    public void test() throws Exception {
        String response = raw("""
                TRACE / HTTP/1.1
                Host: localhost
                
                GET / HTTP/1.1
                Host: localhost
                Connection: close
               
                """);
        assertAll(
                () -> assertTrue(response.contains("HTTP/1.1 405 Not Allowed")),
                () -> assertTrue(response.contains("Connection: close"))
        );
    }

}

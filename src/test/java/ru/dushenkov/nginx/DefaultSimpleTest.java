package ru.dushenkov.nginx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DefaultSimpleTest extends AbstractHttpTest {


    @Override
    protected String getConfigContent() {

        return """
                events {}
                http {
                    server {
                        listen       %%default-port%%;
                        server_name  localhost;
                        location / { }
                        %%test-info%%
                    }
                }

                """;
    }


    @Test
    public void postSimple() {
        ResponseDto responseDto = exchange(HttpMethod.POST, "/");
        assertAll(
                () -> assertEquals(HttpStatus.METHOD_NOT_ALLOWED, responseDto.httpStatusCode())
        );
    }

    @Test
    public void simple() {
        ResponseDto responseDto = get("");
        assertAll(
                () -> assertTrue(responseDto.httpStatusCode().is2xxSuccessful()),
                () -> assertTrue(responseDto.content() != null && responseDto.content().contains("Welcome")));
    }

    @Test
    public void notFoundSimple() {
        ResponseDto responseDto = exchange(HttpMethod.POST, "/test");
        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, responseDto.httpStatusCode())
        );
    }

}

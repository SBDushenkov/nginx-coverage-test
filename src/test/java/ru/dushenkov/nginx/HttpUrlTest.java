package ru.dushenkov.nginx;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static ru.dushenkov.nginx.TestUtil.getHeaderFirstRecord;

@SpringBootTest
public class HttpUrlTest extends AbstractHttpTest {

    private static final String X_URI = "X-URI";


    @Override
    protected String getConfigContent() {

        return """
                events {}
                http {
                    server {
                        listen       127.0.0.1:%%default-port%%;
                        server_name  localhost;
                        location / {
                            add_header  X-URI          "x $uri x";
                            add_header  X-Args         "y $args y";
                            add_header  X-Request-URI  "z $request_uri z";
                            return      204;
                        }
                        %%test-info%%
                    }
                }

                """;
    }


    // like(http_get('/foo/bar%'), qr/400 Bad/, 'percent');
    @Test
    public void percent() throws IOException {
        String str = getWithRawUri("/foo/bar%");
        assertTrue(str.contains("400 Bad Request"));
    }

    // like(http_get('/foo/bar%1'), qr/400 Bad/, 'percent digit');
    @Test
    public void percentDigit() throws IOException {
        String str = getWithRawUri("/foo/bar%1");
        assertTrue(str.contains("400 Bad Request"));
    }


    // like(http_get('/foo/bar/.?args'), qr!x /foo/bar/ x!, 'dot args');
    @Test
    public void dotArgs() {
        ResponseDto responseDto = get("/foo/bar/.?args");
        assertAll(
                () -> assertEquals(HttpStatus.NO_CONTENT, responseDto.httpStatusCode()),
                () -> assertEquals("x /foo/bar/ x", getHeaderFirstRecord(responseDto,X_URI))
        );
    }

    // like(http_get('/foo/bar/.#frag'), qr!x /foo/bar/ x!, 'dot frag');
    @Test
    public void dotFrag() {
        ResponseDto responseDto = get("/foo/bar/.#frag");
        assertAll(
                () -> assertEquals(HttpStatus.NO_CONTENT, responseDto.httpStatusCode()),
                () -> assertEquals("x /foo/bar/ x", getHeaderFirstRecord(responseDto,X_URI))
        );
    }


    // like(http_get('/foo/bar/..?args'), qr!x /foo/ x!, 'dot dot args');
    @Test
    public void dotDotArgs() {
        ResponseDto responseDto = get("/foo/bar/..?args");
        assertAll(
                () -> assertEquals(HttpStatus.NO_CONTENT, responseDto.httpStatusCode()),
                () -> assertEquals("x /foo/ x", getHeaderFirstRecord(responseDto,X_URI))
        );
    }

    // like(http_get('/foo/bar/..#frag'), qr!x /foo/ x!, 'dot dot frag');
    @Test
    public void dotDotFrag() {
        ResponseDto responseDto = get("/foo/bar/..#frag");
        assertAll(
                () -> assertEquals(HttpStatus.NO_CONTENT, responseDto.httpStatusCode()),
                () -> assertEquals("x /foo/ x", getHeaderFirstRecord(responseDto,X_URI))
        );
    }

    //        like(http_get('/foo/bar/.'), qr!x /foo/bar/ x!, 'trailing dot');
    @Test
    public void trailingDot() {
        ResponseDto responseDto = get("/foo/bar/.");
        assertAll(
                () -> assertEquals(HttpStatus.NO_CONTENT, responseDto.httpStatusCode()),
                () -> assertEquals("x /foo/bar/ x", getHeaderFirstRecord(responseDto,X_URI))
        );
    }

    // like(http_get('/foo/bar/..'), qr!x /foo/ x!, 'trailing dot dot');
    @Test
    public void dotDotDot() {
        ResponseDto responseDto = get("/foo/bar/..");
        assertAll(
                () -> assertEquals(HttpStatus.NO_CONTENT, responseDto.httpStatusCode()),
                () -> assertEquals("x /foo/ x", getHeaderFirstRecord(responseDto,X_URI))
        );
    }


// TODO разобраться с default хостом
// like(http_get('http://localhost'), qr!x / x!, 'absolute');
// like(http_get('http://localhost/'), qr!x / x!, 'absolute slash');
// like(http_get('http://localhost?args'), qr!x / x.*y args y!ms,
//         'absolute args');
// like(http_get('http://localhost?args#frag'), qr!x / x.*y args y!ms,
//         'absolute args and frag')

    // like(http_get('http://localhost:8080'), qr!x / x!, 'port');
    @Test
    public void port() {
        ResponseDto responseDto = get("");

        assertAll(
                () -> assertEquals(HttpStatus.NO_CONTENT, responseDto.httpStatusCode()),
                () -> assertEquals("x / x", getHeaderFirstRecord(responseDto,X_URI))
        );
    }
//        like(http_get('http://localhost:8080/'), qr!x / x!, 'port slash');
//        like(http_get('http://localhost:8080?args'), qr!x / x.*y args y!ms,
//        like(http_get('http://localhost:8080?args#frag'), qr!x / x.*y args y!ms,
//                'port args and frag');

    // like(http_get('/ /'), qr/400 Bad/, 'space');
    @Test
    public void space() throws IOException {
        String str = getWithRawUri("/ /");
        assertTrue(str.contains("400 Bad Request"));
    }

    // like(http_get("/\x02"), qr/400 Bad/, 'control');
    @Test
    public void control() throws IOException {
        String str = getWithRawUri("/\u0002");
        assertTrue(str.contains("400 Bad Request"));
    }

// TODO научить работать с сырыми байтами
// like(http_get('/%02'), qr!x /\x02 x!, 'control escaped');
// str = getWithRawUri("/%02");
// assertTrue(str.contains("400 Bad Request"));

}

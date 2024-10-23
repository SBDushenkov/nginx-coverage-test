package ru.dushenkov.nginx;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static ru.dushenkov.nginx.TestUtil.getHeaderFirstRecord;

public class IndexTest extends AbstractHttpTest {

    private static final String X_URI = "X-URI";

    @Override
    protected String getConfigContent() {
        /*
        alias %%TESTDIR%%/ здесь лишнее:
        $content =~ s/%%TESTDIR%%/$self->{_testdir}/gms;
        запускаем nginx с аргументом -p $testdir/ , где my $testdir = $self->{_testdir};
        файл Nginx.pm
        оставил как есть, чтобы сравнивать было легче

        Если не ставить аргумент с префиксом и использовать для TESTDIR произвольную директорию,
        то в тесте defaultIndex найдет не тот файл index.html и в redirect не найдет /re.html
        (а перезапускаться на каждом тесте я не хочу, чтобы в скрипте гарантированно отработало покрытие)

        Можно поправить index.html -> default location

         */
        return """
                
                events {
                }
                
                http {
                    server {
                        listen       %%default-port%%;
                        server_name  localhost;
                        add_header   X-URI $uri;
                
                        location / {
                            # index index.html by default
                        }
                
                        location /redirect/ {
                            alias %%TESTDIR%%/;
                            index /re.html;
                        }
                
                        location /loop/ {
                            index /loop/;
                        }
                
                        location /no_index/ {
                            alias %%TESTDIR%%/;
                            index nonexisting.html;
                        }
                
                        location /many/ {
                            alias %%TESTDIR%%/;
                            index nonexisting.html many.html;
                        }
                
                        location /var/ {
                            alias %%TESTDIR%%/;
                            index $server_name.html;
                        }
                
                        location /va2/ {
                            alias %%TESTDIR%%/;
                            # before 1.13.8, the token produced emerg:
                            # directive "index" is not terminated by ";"
                            index ${server_name}.html;
                        }
                
                        location /var_redirect/ {
                            index /$server_name.html;
                        }
                
                        location /not_found/ {
                            error_log %%TESTDIR%%/log_not_found.log;
                
                            location /not_found/off/ {
                                error_log %%TESTDIR%%/off.log;
                                log_not_found off;
                            }
                        }
                        %%test-info%%
                    }
                }
                """.replace("%%TESTDIR%%", nginxHtmlDir.toString());
    }

    @BeforeEach
    @Override
    public void setUp() throws IOException, InterruptedException {
        Path bak = nginxHtmlDir.resolve("bak");
        FileSystemUtils.deleteRecursively(bak);
        Files.createDirectories(bak);
        try (var fileStream = Files.walk(nginxHtmlDir, 1)) {

            fileStream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Files.move(file, bak.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        Files.writeString(nginxHtmlDir.resolve("index.html"), "body");
        Files.writeString(nginxHtmlDir.resolve("many.html"), "manyBody");
        Files.writeString(nginxHtmlDir.resolve("re.html"), "rebody");
        Files.writeString(nginxHtmlDir.resolve("localhost.html"), "varbody");
        super.setUp();
    }

    @AfterEach
    public void tearDown() throws IOException {
        try (var fileStream = Files.walk(nginxHtmlDir, 1)) {
            fileStream
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        Path bak = nginxHtmlDir.resolve("bak");
        try (var fileStream = Files.walk(bak, 1)) {
            fileStream
                    .forEach(file -> {
                        try {
                            Files.move(file, nginxHtmlDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        FileSystemUtils.deleteRecursively(bak);
    }


    //like(http_get('/'), qr/X-URI: \/index.html.*body/ms, 'default index');
    @Test
    public void defaultIndex() {
        ResponseDto responseDto = get("/");
        assertAll(
                () -> assertEquals(HttpStatus.OK, responseDto.httpStatusCode()),
                () -> assertEquals("body", responseDto.content()),
                () -> assertEquals("/index.html", getHeaderFirstRecord(responseDto, X_URI))
        );
    }

    //like(http_get('/no_index/'), qr/403 Forbidden/, 'no index');
    @Test
    public void noIndex() {
        ResponseDto responseDto = get("/no_index/");
        assertAll(
                () -> assertEquals(HttpStatus.FORBIDDEN, responseDto.httpStatusCode())
        );
    }

    //like(http_get('/redirect/'), qr/X-URI: \/re.html.*rebody/ms, 'redirect');
    @Test
    public void redirect() {
        ResponseDto responseDto = get("/redirect/");
        assertAll(
                () -> assertEquals(HttpStatus.OK, responseDto.httpStatusCode()),
                () -> assertEquals("rebody", responseDto.content())
        );
    }

    //like(http_get('/loop/'), qr/500 Internal/, 'redirect loop');
    @Test
    public void loop() {
        ResponseDto responseDto = get("/loop/");
        assertAll(
                () -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseDto.httpStatusCode())
        );
    }

    //like(http_get('/many/'), qr/X-URI: \/many\/many.html.*manybody/ms, 'many');
    @Test
    public void many() {
        ResponseDto responseDto = get("/many/");
        assertAll(
                () -> assertEquals("/many/many.html", getHeaderFirstRecord(responseDto, X_URI)),
                () -> assertEquals(HttpStatus.OK, responseDto.httpStatusCode()),
                () -> assertEquals("manyBody", responseDto.content())
        );
    }

    //like(http_get('/var/'), qr/X-URI: \/var\/localhost.html.*varbody/ms, 'var');
    @Test
    public void var() {
        ResponseDto responseDto = get("/var/");
        assertAll(
                () -> assertEquals("/var/localhost.html", getHeaderFirstRecord(responseDto, X_URI)),
                () -> assertEquals(HttpStatus.OK, responseDto.httpStatusCode()),
                () -> assertEquals("varbody", responseDto.content())
        );
    }

    //like(http_get('/va2/'), qr/X-URI: \/va2\/localhost.html.*varbody/ms, 'var 2');
    @Test
    public void var2() {
        ResponseDto responseDto = get("/va2/");
        assertAll(
                () -> assertEquals("/va2/localhost.html", getHeaderFirstRecord(responseDto, X_URI)),
                () -> assertEquals(HttpStatus.OK, responseDto.httpStatusCode()),
                () -> assertEquals("varbody", responseDto.content())
        );
    }

    //like(http_get('/var_redirect/'), qr/X-URI: \/localhost.html.*varbody/ms,
    //	'var with redirect');
    @Test
    public void varWithRedirect() {
        ResponseDto responseDto = get("/var_redirect/");
        assertAll(
                () -> assertEquals("/localhost.html", getHeaderFirstRecord(responseDto, X_URI)),
                () -> assertEquals(HttpStatus.OK, responseDto.httpStatusCode()),
                () -> assertEquals("varbody", responseDto.content())
        );
    }

    //like(http_get('/not_found/'), qr/404 Not Found/, 'not found');
    @Test
    public void notFound() {
        ResponseDto responseDto = get("/not_found/");
        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, responseDto.httpStatusCode())
        );
    }

    //like(http_get('/not_found/off/'), qr/404 Not Found/, 'not found log off');
    @Test
    public void notFoundOff() {
        ResponseDto responseDto = get("/not_found/off/");
        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, responseDto.httpStatusCode())
        );
    }

    //like(http_get('/forbidden/'), qr/403 Forbidden/, 'directory access denied');
    @Test
    public void accessDenied() throws IOException {
        Path forbidden = Files.createDirectory(nginxHtmlDir.resolve("forbidden"));
        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("---------");
        Files.setPosixFilePermissions(forbidden, permissions);

        ResponseDto responseDto = get("/forbidden/");
        Files.deleteIfExists(forbidden);
        assertAll(
                () -> assertEquals(HttpStatus.FORBIDDEN, responseDto.httpStatusCode())
        );
    }

    //like(http_get('/index.html/'), qr/404 Not Found/, 'not a directory');
    @Test
    public void notADirectory() {
        ResponseDto responseDto = get("/index.html/");
        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, responseDto.httpStatusCode())
        );
    }
}

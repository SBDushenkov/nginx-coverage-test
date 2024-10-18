package ru.dushenkov.nginx;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import ru.dushenkov.nginx.config.WebConfig;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static ru.dushenkov.nginx.TestUtil.getHeaderFirstRecord;

@SpringBootTest
@Import(WebConfig.class)
@Log4j2
public abstract class AbstractHttpTest implements InitializingBean {
    @Autowired
    @Qualifier("defaultHttpWebClient")
    WebClient httpWebClient;

    @Value("${nginx.port-prefix}")
    protected String portPrefix;
    @Value("${nginx.default-http-port}")
    protected String defaultHttpPort;
    @Value("${nginx.work-dir}")
    protected String workDirString;
    @Value("${nginx.host}")
    protected String host;

    protected String baseUrl;
    protected Path nginxConfDir;
    protected Path nginxLogDir;
    protected Path nginxTargetDir;


    @BeforeEach
    public void setUp() throws IOException, InterruptedException {

        Path pidPath = nginxLogDir.resolve("nginx.pid");
        if (!Files.exists(pidPath)) {
            fail("Nginx not running");
        }

        String testInfo = ("location /test-info { " +
                "add_header test-class \"%%class-name%%\"; " +
                "return 200; }")
//        String testInfo = "location /test-info/ { return 200; }"
                .replace("%%class-name%%", getClass().getSimpleName());

        String conf = getConfigContent();
        conf = conf
                .replace("%%default-port%%", defaultHttpPort)
                .replace("%%test-info%%", testInfo);

        Path confFile = nginxConfDir.resolve("nginx.conf");
        Files.deleteIfExists(confFile);
        Files.writeString(confFile, conf);

        Path nginx = nginxTargetDir.resolve("nginx");

        log.info("Nginx reloading");
        Process process = new ProcessBuilder(nginx.toString(), "-s", "reload")
                .inheritIO()
                .start();
        process.waitFor(1000, TimeUnit.MILLISECONDS);

        int i = 1;
        log.info("Nginx reload result = {}", process.exitValue());

        assertEquals(0, process.exitValue(), "Configuration reload failed");

        while (true) {
            log.info("Nginx checking new configuration applied. Attempt {}", i);
            ResponseDto responseDto = get("/test-info");
            if (Objects.equals(getClass().getSimpleName(), getHeaderFirstRecord(responseDto, "test-class"))) {
                break;
            }
            if (i++ >= 10) {
                fail("Nginx configuration reload failed takes too long");
            }
            Thread.sleep(1000);
        }

        log.info("Nginx new configuration applied successfully");
    }


    protected abstract String getConfigContent();

    @Override
    public void afterPropertiesSet() {
        nginxLogDir = Path.of(workDirString, "coverage", "nginx-install", "logs");
        nginxConfDir = Path.of(workDirString, "coverage", "nginx-install", "conf");
        nginxTargetDir = Path.of(workDirString, "coverage", "target");

        baseUrl = "http://" + host + ":" + defaultHttpPort;

    }

    protected ResponseDto get(String uri) {

        return exchange(HttpMethod.GET, uri);

    }

    protected ResponseDto exchange(HttpMethod method, String uri) {

        boolean uriIsIp = uri.contains("127.0.0.1");
        AtomicReference<HttpStatusCode> statusCodeRef = new AtomicReference<>();
        AtomicReference<HttpHeaders> headersRef = new AtomicReference<>();
        String content = httpWebClient
                .method(method)
                .uri(uriIsIp ? uri : (baseUrl + uri))
                .exchangeToMono(clientResponse -> {
                    statusCodeRef.set(clientResponse.statusCode());
                    headersRef.set(clientResponse.headers().asHttpHeaders());
                    return clientResponse.bodyToMono(String.class);
                })
                .block();


        return new ResponseDto(
                statusCodeRef.get(),
                content,
                headersRef.get()
        );

    }

    protected String getWithRawUri(String raw) throws IOException {

        try (Socket s = new Socket(InetAddress.getByName("localhost"), Integer.parseInt(defaultHttpPort))) {

            String request = MessageFormat.format("""
                    GET {0} HTTP/1.1
                    Host: localhost:{1}
                    \n\r\n
                    """, raw, defaultHttpPort);
            s.getOutputStream().write(request.getBytes());

            InputStream is = s.getInputStream();

            StringBuilder sb = new StringBuilder();
            int b;
            while (true) {
                try {
                    if ((b = is.read()) == -1) {
                        break;
                    }
                    sb.append((char) b);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return sb.toString();
        }
    }

}

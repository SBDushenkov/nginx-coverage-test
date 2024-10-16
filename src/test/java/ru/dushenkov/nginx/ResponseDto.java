package ru.dushenkov.nginx;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

public record ResponseDto(HttpStatusCode httpStatusCode,
                          String content,
                          HttpHeaders headers
                          ) {
}

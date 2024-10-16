package ru.dushenkov.nginx;

import java.util.List;

public class TestUtil {

    public static String getHeaderFirstRecord(ResponseDto responseDto, String header) {
        if (responseDto.headers() != null || responseDto.headers().get(header) == null) {
            return null;
        }
        List<String> strs = responseDto.headers().get(header);
        if (strs.isEmpty()) {
            return null;
        }
        return strs.get(0);
    }
}

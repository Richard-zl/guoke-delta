package com.delta.common.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import java.util.List;

public class JsonUtils {
    public static String toJson(Object obj) { return JSON.toJSONString(obj); }
    public static <T> T parse(String json, Class<T> clazz) { return JSON.parseObject(json, clazz); }
    public static <T> List<T> parseList(String json, Class<T> clazz) { return JSON.parseArray(json, clazz); }
    public static <T> T parse(String json, TypeReference<T> ref) { return JSON.parseObject(json, ref); }
}

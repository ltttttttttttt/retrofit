package retrofit2;

import retrofit2.http.GET;

import java.lang.annotation.Annotation;

/**
 * creator: lt  2021/2/6  lt.dygzs@qq.com
 * effect : 默认的GET注解,默认注解使用
 * warning:
 */
class DefaultGET implements GET {
    String value;

    public DefaultGET(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return GET.class;
    }
}

package retrofit2;

import retrofit2.http.Query;

import java.lang.annotation.Annotation;

/**
 * creator: lt  2021/2/7  lt.dygzs@qq.com
 * effect : 默认的Query注解,默认注解使用
 * warning:
 */
@SuppressWarnings("BadAnnotationImplementation")
class DefaultQuery implements Query {
    final String value;
    final boolean encoded;

    public DefaultQuery(String value) {
        this(value, false);
    }

    public DefaultQuery(String value, boolean encoded) {
        this.value = value;
        this.encoded = encoded;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean encoded() {
        return encoded;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Query.class;
    }
}

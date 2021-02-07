package retrofit2;

import retrofit2.http.POST;

import java.lang.annotation.Annotation;

/**
 * creator: lt  2021/2/6  lt.dygzs@qq.com
 * effect : 默认的POST注解,默认注解使用
 * warning:
 */
class DefaultPOST implements POST {
    String value;
    boolean useFormUrlEncoded;

    public DefaultPOST(String value) {
        this(value, true);
    }

    public DefaultPOST(String value, boolean useFormUrlEncoded) {
        this.value = value;
        this.useFormUrlEncoded = useFormUrlEncoded;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean isUseFormUrlEncoded() {
        return useFormUrlEncoded;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return POST.class;
    }
}

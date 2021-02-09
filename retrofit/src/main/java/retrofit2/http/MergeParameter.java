package retrofit2.http;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * creator: lt  2021/2/9  lt.dygzs@qq.com
 * effect : 表示需要合并参数
 * warning:
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface MergeParameter {
    /**
     * 如果设置了value则使用对应的名称,否则使用统一配置的名称(且此注解无意义)
     */
    String value() default "";
}

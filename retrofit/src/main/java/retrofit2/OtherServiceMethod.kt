package retrofit2

import java.lang.reflect.Method
import java.lang.reflect.Type

/**
 * creator: lt  2021/3/3  lt.dygzs@qq.com
 * effect : 给用户提供更方便的动态代理方法转对象的功能,可以自定义Call<T>的生成
 * warning:
 */
abstract class OtherServiceMethod<T>(
        val retrofit: Retrofit,
        val method: Method,
        val adapterType: Type,
        val annotations: Array<Annotation>,
) : ServiceMethod<T>() {

    interface Factory<T> {
        fun createServiceMethod(
                retrofit: Retrofit,
                method: Method,
                requestFactory: RequestFactory,
        ): OtherServiceMethod<T>
    }

    abstract fun createCall(): Call<T>

    override fun invoke(args: Array<out Any>): T? {
        val callAdapter = HttpServiceMethod.createCallAdapter<T, T?>(retrofit, method, adapterType, annotations)
        return callAdapter.adapt(createCall())
    }
}
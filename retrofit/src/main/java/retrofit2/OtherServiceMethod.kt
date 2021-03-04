package retrofit2

import okhttp3.HttpUrl
import java.lang.reflect.Method

/**
 * creator: lt  2021/3/3  lt.dygzs@qq.com
 * effect : 给用户提供更方便的动态代理方法转对象的功能,可以自定义Call<T>的生成
 * warning:
 */
abstract class OtherServiceMethod<T>(
        val retrofit: Retrofit,
        val method: Method,
        val requestFactory: RequestFactory,
) : ServiceMethod<T>() {

    interface Factory<T> {

        /**
         * 创建OtherServiceMethod,如果自身可以处理就返回,否则可以返回null使用Retrofit默认的
         */
        fun createServiceMethod(
                retrofit: Retrofit,
                method: Method,
                requestFactory: RequestFactory,
        ): OtherServiceMethod<T>?
    }

    abstract fun createCall(
            url: HttpUrl,
            requestParameterMap: Map<String?, Any?>?
    ): Call<T>

    override fun invoke(args: Array<out Any>): T? {
        val callAdapter = HttpServiceMethod.createCallAdapter<T, T?>(retrofit, method, method.genericReturnType, method.annotations)
        val request = requestFactory.create(args)
        val requestParameterMap = requestFactory.getRequestParameterMap(requestFactory.requestBuilder!!)
        return callAdapter.adapt(createCall(request.url(), requestParameterMap))
    }
}
package retrofit2

import okhttp3.HttpUrl
import java.lang.reflect.Method

/**
 * creator: lt  2021/3/3  lt.dygzs@qq.com
 * effect : 给用户提供更方便的动态代理方法转对象的功能,可以自定义Call<T>的生成
 * warning:
 * [retrofit]Retrofit对象
 * [method]需要生成的动态代理
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

    /**
     * 用于创建Call
     * [url]HttpUrl,可以通过调用toString来获取String类型的url
     * [requestParameterMap]请求参数键值对
     * [args]动态代理请求该方法是传入的参数
     *
     * [url]都会传入,但是[requestParameterMap]只有POST和GET才会传入,其他需要自己获取参数(其实是我没用到过,而且太懒...)
     * 返回Retrofit.Call对象,用于替换掉原生的OkHttp的Call
     */
    abstract fun createCall(
            url: HttpUrl,
            requestParameterMap: Map<String?, Any?>?,
            args: Array<out Any>
    ): Call<T>

    override fun invoke(args: Array<out Any>): T? {
        val callAdapter = HttpServiceMethod.createCallAdapter<T, T?>(retrofit, method, method.genericReturnType, method.annotations)
        val request = requestFactory.create(args)
        val requestParameterMap = requestFactory.requestBuilder.getRequestParameterMap()
        return callAdapter.adapt(createCall(request.url(), requestParameterMap, args))
    }
}
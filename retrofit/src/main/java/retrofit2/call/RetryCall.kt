package retrofit2.call

import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

/**
 * creator: lt  2021/3/5  lt.dygzs@qq.com
 * effect : 用于处理重试请求
 * warning:
 */
class RetryCall<T>(private val retryNumber: Int, var call: Call<T>) : Call<T> {
    private var retry = 0

    override fun execute(): Response<T> {
        while (retry <= retryNumber) {
            retry++
            try {
                val t = call.execute()
                if (t.isSuccessful && t.body() != null)
                    return t
            } catch (e: Exception) {
                e.printStackTrace()
                if (retry > retryNumber)
                    throw e
            }
            call = call.clone()
        }
        throw IOException()
    }

    override fun enqueue(callback: Callback<T>) {
        if (retry <= retryNumber) {
            retry++
            call.enqueue(object : Callback<T> {
                override fun onResponse(c: Call<T>, response: Response<T>) {
                    if (response.isSuccessful && response.body() != null)
                        callback.onResponse(c, response)
                    else {
                        call = call.clone()
                        enqueue(callback)
                    }
                }

                override fun onFailure(c: Call<T>, t: Throwable) {
                    t.printStackTrace()
                    call = call.clone()
                    enqueue(callback)
                }
            })
        } else {
            callback.onFailure(this, IOException())
        }
    }

    override fun clone(): Call<T> = call.clone()

    override fun isExecuted(): Boolean = call.isExecuted

    override fun cancel() = call.cancel()

    override fun isCanceled(): Boolean = call.isCanceled

    override fun request(): Request? = call.request()

    override fun timeout(): Timeout = call.timeout()
}
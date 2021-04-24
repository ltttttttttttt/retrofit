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
class RetryCall<T>(
    var call: Call<T>,
    private val retryNumber: Int,
    private val retryWaitTime: Long = 0L,
) : Call<T> {
    private var retry = 0
    private var isCanceled = false

    override fun execute(): Response<T> {
        while (retry <= retryNumber) {
            if (isCanceled)
                throw IOException("Canceled")
            retry++
            try {
                val t = call.execute()
                if (t.isSuccessful && t.body() != null)
                    return t
            } catch (e: Exception) {
                e.printStackTrace()
                if (isCanceled || retry > retryNumber || (e is IOException && e.message?.toLowerCase() == "canceled"))
                    throw e
            }
            try {
                Thread.sleep(retryWaitTime)
            } catch (e: InterruptedException) {
                throw IOException("Canceled")
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
                    if (isCanceled)
                        callback.onFailure(c, IOException("Canceled"))
                    else if (response.isSuccessful && response.body() != null)
                        callback.onResponse(c, response)
                    else {
                        reEnqueue(callback, c)
                    }
                }

                override fun onFailure(c: Call<T>, t: Throwable) {
                    if (isCanceled)
                        callback.onFailure(c, IOException("Canceled"))
                    else {
                        t.printStackTrace()
                        reEnqueue(callback, c)
                    }
                }
            })
        } else {
            callback.onFailure(this, if (isCanceled) IOException("Canceled") else IOException())
        }
    }

    private fun reEnqueue(callback: Callback<T>, c: Call<T>) {
        // TODO by lt 2021/4/24 18:26 处理异步定时,可以直接拿retrofit的线程池
        // TODO by lt 2021/4/24 18:46 需要将回调放入相应线程中(主线程),reEnqueue和enqueue方法 
        //submit{
//        try {
//            Thread.sleep(retryWaitTime)
//        } catch (e: InterruptedException) {
//            callback.onFailure(c, IOException("Canceled"))
//            return
//        }
        if (isCanceled)
            callback.onFailure(c, IOException("Canceled"))
        else {
            call = call.clone()
            enqueue(callback)
        }
        //}
    }

    override fun clone(): Call<T> = call.clone()

    override fun isExecuted(): Boolean = call.isExecuted

    override fun cancel() {
        isCanceled = true
        call.cancel()
    }

    override fun isCanceled(): Boolean = call.isCanceled

    override fun request(): Request? = call.request()

    override fun timeout(): Timeout = call.timeout()
}
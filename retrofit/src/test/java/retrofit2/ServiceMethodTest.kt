/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit2

import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import org.junit.Test
import retrofit2.helpers.ToStringConverterFactory
import java.lang.reflect.Method

/**
 * 测试ServiceMethod
 */
class ServiceMethodTest {

    interface Example {
        fun method(foo2: String?, ping: String?): Call<ResponseBody>
    }

    @Test
    fun test() {
        buildRequest(Example::class.java, "bar", "pong")
    }

    companion object {
        fun <T> buildRequest(cls: Class<T>?, vararg args: String) {
            val retrofitBuilder = Retrofit.Builder()
                    .baseUrl("http://example.com/")
                    .addConverterFactory(ToStringConverterFactory())
                    .setServiceMethodFactory(object : OtherServiceMethod.Factory<Any?> {
                        override fun createServiceMethod(retrofit: Retrofit, method: Method, requestFactory: RequestFactory): OtherServiceMethod<Any?>? {
                            assert(method == Example::class.java.getMethod("method", String::class.java, String::class.java))
                            return object : OtherServiceMethod<Any?>(retrofit, method, requestFactory) {
                                override fun createCall(url: HttpUrl, requestParameterMap: Map<String?, Any?>?, args: Array<out Any>): Call<Any?> {
                                    assert(requestParameterMap!!["foo2"] == "bar")
                                    assert(requestParameterMap["ping"] == "pong")
                                    assert(args[0] == "bar")
                                    assert(args[1] == "pong")
                                    args.forEach(::println)
                                    return DefaultCallAdapterFactoryTest.EmptyCall() as Call<Any?>
                                }
                            }
                        }
                    })
            val callFactory = okhttp3.Call.Factory { request: Request? -> throw UnsupportedOperationException("Not implemented") }
            val retrofit = retrofitBuilder.callFactory(callFactory).build()
            (retrofit.create(cls) as Example).method(args[0], args[1])
        }
    }
}
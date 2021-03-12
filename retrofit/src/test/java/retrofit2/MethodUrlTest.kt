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
 * 测试方法的名字转url和其分隔符的设置
 */
class MethodUrlTest {

    interface Example {
        fun `method$abc`(foo2: String?, ping: String?): Call<ResponseBody>

        fun method_abcd(foo2: String?, ping: String?): Call<ResponseBody>
    }

    @Test
    fun testDefault() {
        fun <T> buildRequest(cls: Class<T>?, vararg args: String) {
            val retrofitBuilder = Retrofit.Builder()
                    .baseUrl("http://example.com/")
                    .addConverterFactory(ToStringConverterFactory())
                    .setServiceMethodFactory(object : OtherServiceMethod.Factory<Any?> {
                        override fun createServiceMethod(retrofit: Retrofit, method: Method, requestFactory: RequestFactory): OtherServiceMethod<Any?>? {
                            return object : OtherServiceMethod<Any?>(retrofit, method, requestFactory) {
                                override fun createCall(url: HttpUrl, requestParameterMap: Map<String?, Any?>?, args: Array<out Any>): Call<Any?> {
                                    assert(url.toString() == "http://example.com/method/abc")
                                    return DefaultCallAdapterFactoryTest.EmptyCall() as Call<Any?>
                                }
                            }
                        }
                    })
            val callFactory = okhttp3.Call.Factory { request: Request? -> throw UnsupportedOperationException("Not implemented") }
            val retrofit = retrofitBuilder.callFactory(callFactory).build()
            (retrofit.create(cls) as Example).`method$abc`(args[0], args[1])
        }

        buildRequest(Example::class.java, "bar", "pong")
    }

    @Test
    fun test_() {
        fun <T> buildRequest(cls: Class<T>?, vararg args: String) {
            val retrofitBuilder = Retrofit.Builder()
                    .baseUrl("http://example.com/")
                    .addConverterFactory(ToStringConverterFactory())
                    .setServiceMethodFactory(object : OtherServiceMethod.Factory<Any?> {
                        override fun createServiceMethod(retrofit: Retrofit, method: Method, requestFactory: RequestFactory): OtherServiceMethod<Any?>? {
                            return object : OtherServiceMethod<Any?>(retrofit, method, requestFactory) {
                                override fun createCall(url: HttpUrl, requestParameterMap: Map<String?, Any?>?, args: Array<out Any>): Call<Any?> {
                                    assert(url.toString() == "http://example.com/method/abcd")
                                    return DefaultCallAdapterFactoryTest.EmptyCall() as Call<Any?>
                                }
                            }
                        }
                    })
                    .setMethodDelimiter("_")
            val callFactory = okhttp3.Call.Factory { request: Request? -> throw UnsupportedOperationException("Not implemented") }
            val retrofit = retrofitBuilder.callFactory(callFactory).build()
            (retrofit.create(cls) as Example).method_abcd(args[0], args[1])
        }

        buildRequest(Example::class.java, "bar", "pong")
    }

}
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

import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import org.assertj.core.api.Assertions
import org.junit.Test
import retrofit2.helpers.ToStringConverterFactory
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.io.IOException
import javax.annotation.Nullable

/**
 * 测试FormUrlEncoded的自动处理
 */
class AutoFormUrlEncodedTest {

    interface Example {
        fun method(@Nullable foo2: String?, ping: String?): Call<ResponseBody>
    }

    interface Example2 {
        fun method(): Call<ResponseBody>
    }

    interface Example3 {
        @FormUrlEncoded
        fun method(): Call<ResponseBody>
    }

    interface Example4 {
        @POST("test", isUseFormUrlEncoded = false)
        fun method(@Nullable foo2: String?, ping: String?): Call<ResponseBody>
    }

    @Test
    fun test1() {
        val request = buildRequest(Example::class.java, "bar", "pong")
        val body = request.body()
        Assertions.assertThat(body!!.contentType().toString()).isEqualTo("application/x-www-form-urlencoded")
    }

    @Test
    fun test2() {
        val request = buildRequest(Example2::class.java)
        val body = request.body()
        Assertions.assertThat(body!!.contentType().toString()).isEqualTo("null")
    }

    @Test
    fun test3() {
        try {
            buildRequest(Example3::class.java)
        } catch (e: IllegalArgumentException) {
            assert("Form-encoded method must contain at least one @Field.\n" +
                    "    for method Example3.method" == e.message)
            return
        }
        assert(false)
    }

    @Test
    fun test4() {
        try {
            buildRequest(Example4::class.java)
        } catch (e: IllegalArgumentException) {
            assert("@Field parameters can only be used with form encoding. (parameter #1)\n" +
                    "    for method Example4.method" == e.message)
            return
        }
        assert(false)
    }

    companion object {
        fun <T> buildRequest(cls: Class<T>?, vararg args: Any?): Request {
            val retrofitBuilder = Retrofit.Builder()
                    .baseUrl("http://example.com/")
                    .addConverterFactory(ToStringConverterFactory())
            return buildRequest(cls, retrofitBuilder, *args)
        }

        fun <T> buildRequest(cls: Class<T>?, builder: Retrofit.Builder, vararg args: Any?): Request {
            val callFactory = okhttp3.Call.Factory { request: Request? -> throw UnsupportedOperationException("Not implemented") }
            val retrofit = builder.callFactory(callFactory).build()
            val method = TestingUtils.onlyMethod(cls)
            return try {
                RequestFactory.parseAnnotations(retrofit, method).create(args)
            } catch (e: Exception) {
                throw e
            }
        }
    }
}
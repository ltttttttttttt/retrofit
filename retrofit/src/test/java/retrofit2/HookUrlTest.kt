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
import okhttp3.ResponseBody
import org.assertj.core.api.Assertions
import org.junit.Test
import retrofit2.helpers.ToStringConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 测试HookUrl
 */
class HookUrlTest {

    @Test
    @Throws(Exception::class)
    fun testHookUrl() {
        class Example {
            @POST("method")
            fun method(@Query("maybe") @RequestFactoryTest.NonNull o: Any?): Call<ResponseBody>? {
                return null
            }
        }

        val request = buildRequest(Example::class.java, "yep")
        Assertions.assertThat(request.url().toString()).isEqualTo("http://example.com/v/method?maybe=yep")
    }

    @Test
    @Throws(Exception::class)
    fun testHookUrlNotAnnotation() {
        class Example {
            fun method(o: Any?): Call<ResponseBody>? {
                return null
            }
        }

        val request = buildRequest(Example::class.java, "yep")
        Assertions.assertThat(request.url().toString()).isEqualTo("http://example.com/v/method?o=yep")
    }

    @Test
    @Throws(Exception::class)
    fun testHookUrlMethodNotAnnotation() {
        class Example {
            fun method(@Query("maybe") @RequestFactoryTest.NonNull o: Any?): Call<ResponseBody>? {
                return null
            }
        }

        val request = buildRequest(Example::class.java, "yep")
        Assertions.assertThat(request.url().toString()).isEqualTo("http://example.com/v/method?maybe=yep")
    }

    @Test
    @Throws(Exception::class)
    fun testHookUrlAnd_Url() {
        class Example {
            @GET
            fun method(@Url @RequestFactoryTest.NonNull o: String): Call<ResponseBody>? {
                return null
            }
        }

        val request = buildRequest(Example::class.java, "yep")
        Assertions.assertThat(request.url().toString()).isEqualTo("http://example.com/v/yep")
    }

    private fun <T> buildRequest(cls: Class<T>?, vararg args: Any?): Request {
        val retrofitBuilder = Retrofit.Builder()
                .baseUrl("http://example.com/")
                .addConverterFactory(ToStringConverterFactory())
                .defaultAnnotation(GET::class.java)
                .setHandlerUrlListener { s, method ->
                    "v/$s"
                }
        return RequestFactoryTest.buildRequest(cls, retrofitBuilder, *args)
    }
}
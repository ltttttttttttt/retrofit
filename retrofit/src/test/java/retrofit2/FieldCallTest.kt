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
import retrofit2.http.Field
import retrofit2.http.POST
import java.io.IOException

interface Example {
    @POST("/foo")
    fun method(foo2: String?, @Field("ping") ping2: String?): Call<ResponseBody>
}

class FieldCallTest {
    @Test
    fun simpleFormEncoded() {
        val request = buildRequest(Example::class.java, "bar", "pong")
        val body = request.body()
        assertBody(body, "foo2=bar&ping=pong")
        Assertions.assertThat(body!!.contentType().toString()).isEqualTo("application/x-www-form-urlencoded")
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
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                throw AssertionError(e)
            }
        }

        private fun assertBody(body: RequestBody?, expected: String) {
            body!!
            val buffer = Buffer()
            try {
                body.writeTo(buffer)
                Assertions.assertThat(buffer.readUtf8()).isEqualTo(expected)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
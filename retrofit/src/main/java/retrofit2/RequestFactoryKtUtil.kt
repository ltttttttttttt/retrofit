@file:JvmName("RequestFactoryKtUtil")

package retrofit2

import okhttp3.FormBody
import okhttp3.HttpUrl
import retrofit2.http.*
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.URLDecoder
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf

/**
 * creator: lt  2020/9/23  lt.dygzs@qq.com
 * effect :
 * warning:
 */

internal val namesField by lazy {
    val field = FormBody.Builder::class.java.getDeclaredField("names")
    field.isAccessible = true
    field
}
internal val valuesField by lazy {
    val field = FormBody.Builder::class.java.getDeclaredField("values")
    field.isAccessible = true
    field
}
internal val encodedQueryNamesAndValuesField by lazy {
    val field = HttpUrl.Builder::class.java.getDeclaredField("encodedQueryNamesAndValues")
    field.isAccessible = true
    field
}
internal var mapType: Type? = null

private val httpMethodAnnotations = arrayOf(
        GET::class.java,
        POST::class.java,
        HEAD::class.java,
        OPTIONS::class.java,
        PUT::class.java,
        DELETE::class.java,
        Multipart::class.java,
        HTTP::class.java,
        PATCH::class.java,
)

private val httpParameterAnnotations = arrayOf(
        retrofit2.http.Field::class.java,
        Query::class.java,
        Url::class.java,
        Header::class.java,
        Part::class.java,
        Path::class.java,
        Body::class.java,
        QueryMap::class.java,
        PartMap::class.java,
        HeaderMap::class.java,
        FieldMap::class.java,
        QueryName::class.java,
        Tag::class.java,
)

/**
 * 处理如果方法不加任何注解,则默认是@POST和@FormUrlEncoded,并且url为方法名,
 */
@Deprecated("使用了更优解")
internal fun RequestFactory.Builder.handlerParseMethodDefaultAnnotation() {
    if (retrofit.defaultAnnotationClass == POST::class.java) {
        if (parameterTypes.isNotEmpty() && !(parameterTypes.size == 1 && isKotlinSuspendFunction)) {
            if (isMultipart) {
                throw Utils.methodError(method, "Only one encoding annotation is allowed.")
            }
            isFormEncoded = true
        }

        val value = method.name.replace('$', '\\')
        val httpMethod = "POST"
        val hasBody = true
        if (this.httpMethod != null) {
            throw Utils.methodError(
                    method,
                    "Only one HTTP method is allowed. Found: %s and %s.",
                    this.httpMethod,
                    httpMethod)
        }
        this.httpMethod = httpMethod
        this.hasBody = hasBody

        if (value.isEmpty()) {
            return
        }
        // Get the relative URL path and existing query string, if present.
        val question = value.indexOf('?')
        if (question != -1 && question < value.length - 1) {
            // Ensure the query string does not have any named parameters.
            val queryParams = value.substring(question + 1)
            val queryParamMatcher = RequestFactory.Builder.PARAM_URL_REGEX.matcher(queryParams)
            if (queryParamMatcher.find()) {
                throw Utils.methodError(
                        method, "URL query string \"%s\" must not have replace block. "
                        + "For dynamic query parameters use @Query.",
                        queryParams)
            }
        }

        relativeUrl = value
        relativeUrlParamNames = RequestFactory.Builder.parsePathParameters(value)
    } else if (retrofit.defaultAnnotationClass == GET::class.java) {
        val value = method.name.replace('$', '\\')
        val httpMethod = "GET"
        val hasBody = false
        if (this.httpMethod != null) {
            throw Utils.methodError(
                    method,
                    "Only one HTTP method is allowed. Found: %s and %s.",
                    this.httpMethod,
                    httpMethod)
        }
        this.httpMethod = httpMethod
        this.hasBody = hasBody

        if (value.isEmpty()) {
            return
        }
        // Get the relative URL path and existing query string, if present.
        val question: Int = value.indexOf('?')
        if (question != -1 && question < value.length - 1) {
            // Ensure the query string does not have any named parameters.
            val queryParams: String = value.substring(question + 1)
            val queryParamMatcher = RequestFactory.Builder.PARAM_URL_REGEX.matcher(queryParams)
            if (queryParamMatcher.find()) {
                throw Utils.methodError(
                        method, "URL query string \"%s\" must not have replace block. "
                        + "For dynamic query parameters use @Query.",
                        queryParams)
            }
        }

        relativeUrl = value
        relativeUrlParamNames = RequestFactory.Builder.parsePathParameters(value)
    }
}

/**
 * 处理如果参数不加任何注解,则默认是@Field,并且value为参数名
 */
@Deprecated("使用了更优解")
internal fun RequestFactory.Builder.handlerParameterFromNoAnnotation(
        p: Int,
        type: Type,
        annotations: Array<Annotation>,
        kFunction: KFunction<*>?
): ParameterHandler<*> {
    if (retrofit.defaultAnnotationClass == POST::class.java) {
        validateResolvableType(p, type)
        if (!isFormEncoded) {
            throw Utils.parameterError(method, p, "@Field parameters can only be used with form encoding.")
        }
        val ktFunction = kFunction
                ?: throw Utils.parameterError(method, p, "@Field not find, or not use kt file by lt 2333.")
        val name: String = ktFunction.parameters[p + 1].name
                ?: throw Utils.parameterError(method, p, "parameter name is null.")
        val encoded = false

        gotField = true

        val rawParameterType = Utils.getRawType(type)
        return when {
            Iterable::class.java.isAssignableFrom(rawParameterType) -> {
                if (type !is ParameterizedType) {
                    throw Utils.parameterError(
                            method,
                            p,
                            rawParameterType.simpleName
                                    + " must include generic type (e.g., "
                                    + rawParameterType.simpleName
                                    + "<String>)")
                }
                val iterableType = Utils.getParameterUpperBound(0, type)
                val converter = retrofit.stringConverter<Any>(iterableType, annotations)
                ParameterHandler.Field(name, converter, encoded).iterable()
            }
            rawParameterType.isArray -> {
                val arrayComponentType = RequestFactory.Builder.boxIfPrimitive(rawParameterType.componentType)
                val converter = retrofit.stringConverter<Any>(arrayComponentType, annotations)
                ParameterHandler.Field(name, converter, encoded).array()
            }
            else -> {
                val converter = retrofit.stringConverter<Any>(type, annotations)
                ParameterHandler.Field(name, converter, encoded)
            }
        }
    } else if (retrofit.defaultAnnotationClass == GET::class.java) {
        validateResolvableType(p, type)
        val ktFunction = kFunction
                ?: throw Utils.parameterError(method, p, "@Query not find, or not use kt file by lt 2333.")
        val name: String = ktFunction.parameters[p + 1].name
                ?: throw Utils.parameterError(method, p, "parameter name is null.")
        val encoded = false

        val rawParameterType = Utils.getRawType(type)
        gotQuery = true
        return when {
            Iterable::class.java.isAssignableFrom(rawParameterType) -> {
                if (type !is ParameterizedType) {
                    throw Utils.parameterError(
                            method,
                            p,
                            rawParameterType.simpleName
                                    + " must include generic type (e.g., "
                                    + rawParameterType.simpleName
                                    + "<String>)")
                }
                val iterableType = Utils.getParameterUpperBound(0, type)
                val converter = retrofit.stringConverter<Any>(iterableType, annotations)
                ParameterHandler.Query(name, converter, encoded).iterable()
            }
            rawParameterType.isArray -> {
                val arrayComponentType = RequestFactory.Builder.boxIfPrimitive(rawParameterType.componentType)
                val converter = retrofit.stringConverter<Any>(arrayComponentType, annotations)
                ParameterHandler.Query(name, converter, encoded).array()
            }
            else -> {
                val converter = retrofit.stringConverter<Any>(type, annotations)
                ParameterHandler.Query(name, converter, encoded)
            }
        }
    } else {
        throw IllegalArgumentException("defaultAnnotation must set GET.class or POST.class")
    }
}

/**
 * 检查是否设置了合并参数,如果开启了则POST将Field合并到一块,GET将Query合并,且仅支持GET和POST
 * 需要参数区分有无注解,且需要区分使用java和使用kt
 */
@OptIn(ExperimentalStdlibApi::class)
internal fun RequestFactory.handlerSingleParameterHandlers(requestBuilder: RequestBuilder, singleParameterName: String) {
    if (requestBuilder.method == "POST") {
        val formBuilder = requestBuilder.formBuilder ?: return
        val names = namesField.get(formBuilder) as? ArrayList<String?>
        val values = valuesField.get(formBuilder) as? ArrayList<String?>
        val size = names?.size ?: 0
        if (size > 0) {
            names!!
            values!!
            val map = HashMap<String?, String?>(size)
            for (i in 0 until size) {
                map[names[i]] = values[i]
            }
            names.clear()
            values.clear()
            names.add(singleParameterName)
            if (mapType == null)
                mapType = typeOf<HashMap<String, String>>().javaType
            values.add(
                    retrofit.stringConverter<Any>(mapType, arrayOf(DefaultField(singleParameterName))).convert(map)
            )
        }
    } else if (requestBuilder.method == "GET") {
        val urlBuilder = requestBuilder.urlBuilder ?: return
        val list = encodedQueryNamesAndValuesField.get(urlBuilder) as? ArrayList<String?> ?: return
        val size = list.size
        if (size > 0) {
            val map = HashMap<String, String?>(size / 2)
            for (i in 0 until size step 2) {
                val value = list[i + 1]
                map[list[i]!!] = if (value == null) null else URLDecoder.decode(value, "UTF-8")
            }
            list.clear()
            list.add(singleParameterName)
            if (mapType == null)
                mapType = typeOf<HashMap<String, String>>().javaType
            list.add(
                    retrofit.stringConverter<Any>(mapType, arrayOf(DefaultQuery(singleParameterName))).convert(map)
            )
        }
    }
}

/**
 * 获取请求键值对map
 * 只能获取到get和post的
 */
@OptIn(ExperimentalStdlibApi::class)
internal fun RequestFactory.getRequestParameterMap(requestBuilder: RequestBuilder): Map<String?, Any?>? {
    if (requestBuilder.method == "POST") {
        val formBuilder = requestBuilder.formBuilder ?: return null
        val names = namesField.get(formBuilder) as? ArrayList<String?>
        val values = valuesField.get(formBuilder) as? ArrayList<String?>
        val size = names?.size ?: 0
        if (size > 0) {
            names!!
            values!!
            val map = HashMap<String?, String?>(size)
            for (i in 0 until size) {
                map[names[i]] = values[i]
            }
            return map
        }
    } else if (requestBuilder.method == "GET") {
        val urlBuilder = requestBuilder.urlBuilder ?: return null
        val list = encodedQueryNamesAndValuesField.get(urlBuilder) as? ArrayList<String?> ?: return null
        val size = list.size
        if (size > 0) {
            val map = HashMap<String?, String?>(size / 2)
            for (i in 0 until size step 2) {
                val value = list[i + 1]
                map[list[i]] = if (value == null) null else URLDecoder.decode(value, "UTF-8")
            }
            return map
        }
    }
    return null
}

/**
 * 处理如果方法不加任何注解,则默认是@POST和@FormUrlEncoded,并且url为方法名,
 */
internal fun RequestFactory.Builder.getMethodDefaultAnnotationAndHttpMethod(): Pair<Annotation?, Class<*>> {
    methodAnnotations.forEach {
        val annotationCLass = it::class.java.interfaces[0]
        if (it != null && httpMethodAnnotations.contains(annotationCLass))
            return null to annotationCLass
    }
    return when (retrofit.defaultAnnotationClass) {
        POST::class.java -> {
            if (parameterTypes.isNotEmpty() && !(parameterTypes.size == 1 && isKotlinSuspendFunction)) {
                if (isMultipart) {
                    throw Utils.methodError(method, "Only one encoding annotation is allowed.")
                }
                isFormEncoded = true
            }
            DefaultPOST(method.name.replace('$', '\\')) to POST::class.java
        }
        GET::class.java -> DefaultGET(method.name.replace('$', '\\')) to GET::class.java
        else -> throw IllegalArgumentException("defaultAnnotation must set GET.class or POST.class")
    }
}

/**
 * 处理如果参数不加任何注解,则默认是@Field,并且value为参数名
 */
internal fun getParameterDefaultAnnotation(httpMethodClass: Class<*>,
                                           annotations: Array<Annotation>,
                                           kFunction: KFunction<*>?,
                                           position: Int
): Annotation? {
    if (parameterAnnotationContainsRequestAnnotation(annotations) != null) return null
    return when (httpMethodClass) {
        POST::class.java -> DefaultField(kFunction?.parameters?.get(position + 1)?.name
                ?: throw IllegalStateException("kFunction not find, not use kt file by lt 2333."))
        GET::class.java -> DefaultQuery(kFunction?.parameters?.get(position + 1)?.name
                ?: throw IllegalStateException("kFunction not find, or not use kt file by lt 2333."))
        else -> null
    }
}

/**
 * 查看参数注解中是否含有请求的注解,并将找到的注解返回出去
 * 返回null表示没找到请求参数注解
 */
internal fun parameterAnnotationContainsRequestAnnotation(annotations: Array<Annotation>): Class<*>? {
    annotations.forEach {
        val clazz = it::class.java.interfaces[0]
        if (httpParameterAnnotations.contains(clazz))
            return clazz
    }
    return null
}
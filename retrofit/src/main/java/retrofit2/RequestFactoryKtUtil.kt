package retrofit2

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.jvm.kotlinFunction

/**
 * creator: lt  2020/9/23  lt.dygzs@qq.com
 * effect :
 * warning:
 */
object RequestFactoryKtUtil {

    /**
     * 处理如果方法不加任何注解,则默认是@POST和@FormUrlEncoded,并且url为方法名,
     */
    @JvmStatic
    @Suppress("EXPOSED_FUNCTION_RETURN_TYPE", "EXPOSED_RECEIVER_TYPE")
    fun RequestFactory.Builder.handlerParseMethodDefaultAnnotation() {
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
    }

    /**
     * 处理如果参数不加任何注解,则默认是@Field,并且value为参数名
     */
    @JvmStatic
    @Suppress("EXPOSED_FUNCTION_RETURN_TYPE", "EXPOSED_RECEIVER_TYPE")
    fun RequestFactory.Builder.handlerParameterFromNoAnnotation(
            p: Int,
            type: Type,
            annotations: Array<Annotation>
    ): ParameterHandler<*> {
        validateResolvableType(p, type)
        if (!isFormEncoded) {
            throw Utils.parameterError(method, p, "@Field parameters can only be used with form encoding.")
        }
        val ktFunction = method.kotlinFunction
                ?: throw Utils.parameterError(method, p, "@Field not find, or use kt file by lt 2333.")
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
                val converter: Converter<Any, String> = retrofit.stringConverter(iterableType, annotations)
                ParameterHandler.Field(name, converter, encoded).iterable()
            }
            rawParameterType.isArray -> {
                val arrayComponentType = RequestFactory.Builder.boxIfPrimitive(rawParameterType.componentType)
                val converter: Converter<Any, String> = retrofit.stringConverter(arrayComponentType, annotations)
                ParameterHandler.Field(name, converter, encoded).array()
            }
            else -> {
                val converter: Converter<Any, String> = retrofit.stringConverter(type, annotations)
                ParameterHandler.Field(name, converter, encoded)
            }
        }
    }
}
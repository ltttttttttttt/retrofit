package retrofit2

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.jvm.kotlinFunction

/**
 * creator: lt  2020/9/23  lt.dygzs@qq.com
 * effect : 处理如果参数不加任何注解,则默认是@Field,并且value为参数名
 * warning:
 */
object RequestFactoryKtUtil {
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
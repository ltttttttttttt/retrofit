/*
 * Copyright (C) 2012 Square, Inc.
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
package retrofit2;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import kotlin.jvm.functions.Function2;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.http.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static java.util.Collections.unmodifiableList;

/**
 * Retrofit adapts a Java interface to HTTP calls by using annotations on the declared methods to
 * define how requests are made. Create instances using {@linkplain Builder the builder} and pass
 * your interface to {@link #create} to generate an implementation.
 *
 * <p>For example,
 *
 * <pre><code>
 * Retrofit retrofit = new Retrofit.Builder()
 *     .baseUrl("https://api.example.com/")
 *     .addConverterFactory(GsonConverterFactory.create())
 *     .build();
 *
 * MyApi api = retrofit.create(MyApi.class);
 * Response&lt;User&gt; user = api.getUser().execute();
 * </code></pre>
 *
 * @author Bob Lee (bob@squareup.com)
 * @author Jake Wharton (jw@squareup.com)
 */
public final class Retrofit {
  private final Map<Method, ServiceMethod<?>> serviceMethodCache = new ConcurrentHashMap<>();

  final okhttp3.Call.Factory callFactory;
  HttpUrl baseUrl;
  final List<Converter.Factory> converterFactories;
  final List<CallAdapter.Factory> callAdapterFactories;
  final @Nullable Executor callbackExecutor;
  final boolean validateEagerly;
  final Class<?> defaultAnnotationClass;
  final @org.jetbrains.annotations.Nullable String singleParameterName;
  final @org.jetbrains.annotations.Nullable OtherServiceMethod.Factory<?> otherServiceMethodFactory;
  final @org.jetbrains.annotations.Nullable Function2<String,Method, String> handlerUrlListener;
  final @org.jetbrains.annotations.NotNull String methodDelimiter;

  Retrofit(
      okhttp3.Call.Factory callFactory,
      HttpUrl baseUrl,
      List<Converter.Factory> converterFactories,
      List<CallAdapter.Factory> callAdapterFactories,
      @Nullable Executor callbackExecutor,
      boolean validateEagerly,
      @NotNull Class<?> defaultAnnotationClass,
      @org.jetbrains.annotations.Nullable String singleParameterName,
      @org.jetbrains.annotations.Nullable OtherServiceMethod.Factory<?> otherServiceMethodFactory,
      @org.jetbrains.annotations.Nullable Function2<String, Method, String> handlerUrlListener,
      @org.jetbrains.annotations.NotNull String methodDelimiter) {
    this.callFactory = callFactory;
    this.baseUrl = baseUrl;
    this.converterFactories = converterFactories; // Copy+unmodifiable at call site.
    this.callAdapterFactories = callAdapterFactories; // Copy+unmodifiable at call site.
    this.callbackExecutor = callbackExecutor;
    this.validateEagerly = validateEagerly;
    this.defaultAnnotationClass = defaultAnnotationClass;
    this.singleParameterName = singleParameterName;
    this.otherServiceMethodFactory = otherServiceMethodFactory;
    this.handlerUrlListener = handlerUrlListener;
    this.methodDelimiter = methodDelimiter;
  }

  /**
   * Create an implementation of the API endpoints defined by the {@code service} interface.
   *
   * <p>The relative path for a given method is obtained from an annotation on the method describing
   * the request type. The built-in methods are {@link retrofit2.http.GET GET}, {@link
   * retrofit2.http.PUT PUT}, {@link retrofit2.http.POST POST}, {@link retrofit2.http.PATCH PATCH},
   * {@link retrofit2.http.HEAD HEAD}, {@link retrofit2.http.DELETE DELETE} and {@link
   * retrofit2.http.OPTIONS OPTIONS}. You can use a custom HTTP method with {@link HTTP @HTTP}. For
   * a dynamic URL, omit the path on the annotation and annotate the first parameter with {@link
   * Url @Url}.
   *
   * <p>Method parameters can be used to replace parts of the URL by annotating them with {@link
   * retrofit2.http.Path @Path}. Replacement sections are denoted by an identifier surrounded by
   * curly braces (e.g., "{foo}"). To add items to the query string of a URL use {@link
   * retrofit2.http.Query @Query}.
   *
   * <p>The body of a request is denoted by the {@link retrofit2.http.Body @Body} annotation. The
   * object will be converted to request representation by one of the {@link Converter.Factory}
   * instances. A {@link RequestBody} can also be used for a raw representation.
   *
   * <p>Alternative request body formats are supported by method annotations and corresponding
   * parameter annotations:
   *
   * <ul>
   *   <li>{@link retrofit2.http.FormUrlEncoded @FormUrlEncoded} - Form-encoded data with key-value
   *       pairs specified by the {@link retrofit2.http.Field @Field} parameter annotation.
   *   <li>{@link retrofit2.http.Multipart @Multipart} - RFC 2388-compliant multipart data with
   *       parts specified by the {@link retrofit2.http.Part @Part} parameter annotation.
   * </ul>
   *
   * <p>Additional static headers can be added for an endpoint using the {@link
   * retrofit2.http.Headers @Headers} method annotation. For per-request control over a header
   * annotate a parameter with {@link Header @Header}.
   *
   * <p>By default, methods return a {@link Call} which represents the HTTP request. The generic
   * parameter of the call is the response body type and will be converted by one of the {@link
   * Converter.Factory} instances. {@link ResponseBody} can also be used for a raw representation.
   * {@link Void} can be used if you do not care about the body contents.
   *
   * <p>For example:
   *
   * <pre>
   * public interface CategoryService {
   *   &#64;POST("category/{cat}/")
   *   Call&lt;List&lt;Item&gt;&gt; categoryList(@Path("cat") String a, @Query("page") int b);
   * }
   * </pre>
   */
  @SuppressWarnings("unchecked") // Single-interface proxy creation guarded by parameter safety.
  public <T> T create(final Class<T> service) {
    validateServiceInterface(service);
    return (T)
        Proxy.newProxyInstance(
            service.getClassLoader(),
            new Class<?>[] {service},
            new InvocationHandler() {
              private final Platform platform = Platform.get();
              private final Object[] emptyArgs = new Object[0];

              @Override
              public @Nullable Object invoke(Object proxy, Method method, @Nullable Object[] args)
                  throws Throwable {
                // If the method is a method from Object then defer to normal invocation.
                if (method.getDeclaringClass() == Object.class) {
                  return method.invoke(this, args);
                }
                args = args != null ? args : emptyArgs;
                return platform.isDefaultMethod(method)
                    ? platform.invokeDefaultMethod(method, service, proxy, args)
                    : loadServiceMethod(method).invoke(args);
              }
            });
  }

  private void validateServiceInterface(Class<?> service) {
    if (!service.isInterface()) {
      throw new IllegalArgumentException("API declarations must be interfaces.");
    }

    Deque<Class<?>> check = new ArrayDeque<>(1);
    check.add(service);
    while (!check.isEmpty()) {
      Class<?> candidate = check.removeFirst();
      if (candidate.getTypeParameters().length != 0) {
        StringBuilder message =
            new StringBuilder("Type parameters are unsupported on ").append(candidate.getName());
        if (candidate != service) {
          message.append(" which is an interface of ").append(service.getName());
        }
        throw new IllegalArgumentException(message.toString());
      }
      Collections.addAll(check, candidate.getInterfaces());
    }

    if (validateEagerly) {
      Platform platform = Platform.get();
      for (Method method : service.getDeclaredMethods()) {
        if (!platform.isDefaultMethod(method) && !Modifier.isStatic(method.getModifiers())) {
          loadServiceMethod(method);
        }
      }
    }
  }

  ServiceMethod<?> loadServiceMethod(Method method) {
    ServiceMethod<?> result = serviceMethodCache.get(method);
    if (result != null) return result;

    synchronized (serviceMethodCache) {
      result = serviceMethodCache.get(method);
      if (result == null) {
        result = ServiceMethod.parseAnnotations(this, method);
        serviceMethodCache.put(method, result);
      }
    }
    return result;
  }

  /**
   * 预初始化所有的方法,使用时更快(且可以初始化kt的方法)
   * 注意需要在子线程调用,否则会造成anr,且由于将所有方法载入,所以会增加内存消耗,属于懒汉式(空间换时间)
   *
   * @param interfaceClass retrofit.create传入的class,注意需要先调用create
   */
  public void preInit(Class<?> interfaceClass) {
    for (Method method : interfaceClass.getMethods()) {
      if (method.getDeclaringClass() == Object.class)
        continue;
      loadServiceMethod(method);
    }
  }

  /**
   * The factory used to create {@linkplain okhttp3.Call OkHttp calls} for sending a HTTP requests.
   * Typically an instance of {@link OkHttpClient}.
   */
  public okhttp3.Call.Factory callFactory() {
    return callFactory;
  }

  /** The API base URL. */
  public HttpUrl baseUrl() {
    return baseUrl;
  }

  /**
   * Set the API base URL.
   */
  public void setBaseUrl(URL baseUrl) {
    Objects.requireNonNull(baseUrl, "baseUrl == null");
    this.baseUrl = HttpUrl.get(baseUrl.toString());
  }

  /**
   * Set the API base URL.
   */
  public void setBaseUrl(String baseUrl) {
    Objects.requireNonNull(baseUrl, "baseUrl == null");
    this.baseUrl = HttpUrl.get(baseUrl);
  }

  /**
   * Set the API base URL.
   *
   * <p>The specified endpoint values (such as with {@link GET @GET}) are resolved against this
   * value using {@link HttpUrl#resolve(String)}. The behavior of this matches that of an {@code
   * <a href="">} link on a website resolving on the current URL.
   *
   * <p><b>Base URLs should always end in {@code /}.</b>
   *
   * <p>A trailing {@code /} ensures that endpoints values which are relative paths will correctly
   * append themselves to a base which has path components.
   *
   * <p><b>Correct:</b><br>
   * Base URL: http://example.com/api/<br>
   * Endpoint: foo/bar/<br>
   * Result: http://example.com/api/foo/bar/
   *
   * <p><b>Incorrect:</b><br>
   * Base URL: http://example.com/api<br>
   * Endpoint: foo/bar/<br>
   * Result: http://example.com/foo/bar/
   *
   * <p>This method enforces that {@code baseUrl} has a trailing {@code /}.
   *
   * <p><b>Endpoint values which contain a leading {@code /} are absolute.</b>
   *
   * <p>Absolute values retain only the host from {@code baseUrl} and ignore any specified path
   * components.
   *
   * <p>Base URL: http://example.com/api/<br>
   * Endpoint: /foo/bar/<br>
   * Result: http://example.com/foo/bar/
   *
   * <p>Base URL: http://example.com/<br>
   * Endpoint: /foo/bar/<br>
   * Result: http://example.com/foo/bar/
   *
   * <p><b>Endpoint values may be a full URL.</b>
   *
   * <p>Values which have a host replace the host of {@code baseUrl} and values also with a scheme
   * replace the scheme of {@code baseUrl}.
   *
   * <p>Base URL: http://example.com/<br>
   * Endpoint: https://github.com/square/retrofit/<br>
   * Result: https://github.com/square/retrofit/
   *
   * <p>Base URL: http://example.com<br>
   * Endpoint: //github.com/square/retrofit/<br>
   * Result: http://github.com/square/retrofit/ (note the scheme stays 'http')
   */
  public void setBaseUrl(HttpUrl baseUrl) {
    Objects.requireNonNull(baseUrl, "baseUrl == null");
    List<String> pathSegments = baseUrl.pathSegments();
    if (!"".equals(pathSegments.get(pathSegments.size() - 1))) {
      throw new IllegalArgumentException("baseUrl must end in /: " + baseUrl);
    }
    this.baseUrl = baseUrl;
  }

  /**
   * Returns a list of the factories tried when creating a {@linkplain #callAdapter(Type,
   * Annotation[])} call adapter}.
   */
  public List<CallAdapter.Factory> callAdapterFactories() {
    return callAdapterFactories;
  }

  /**
   * Returns the {@link CallAdapter} for {@code returnType} from the available {@linkplain
   * #callAdapterFactories() factories}.
   *
   * @throws IllegalArgumentException if no call adapter available for {@code type}.
   */
  public CallAdapter<?, ?> callAdapter(Type returnType, Annotation[] annotations) {
    return nextCallAdapter(null, returnType, annotations);
  }

  /**
   * Returns the {@link CallAdapter} for {@code returnType} from the available {@linkplain
   * #callAdapterFactories() factories} except {@code skipPast}.
   *
   * @throws IllegalArgumentException if no call adapter available for {@code type}.
   */
  public CallAdapter<?, ?> nextCallAdapter(
      @Nullable CallAdapter.Factory skipPast, Type returnType, Annotation[] annotations) {
    Objects.requireNonNull(returnType, "returnType == null");
    Objects.requireNonNull(annotations, "annotations == null");

    int start = callAdapterFactories.indexOf(skipPast) + 1;
    for (int i = start, count = callAdapterFactories.size(); i < count; i++) {
      CallAdapter<?, ?> adapter = callAdapterFactories.get(i).get(returnType, annotations, this);
      if (adapter != null) {
        return adapter;
      }
    }

    StringBuilder builder =
        new StringBuilder("Could not locate call adapter for ").append(returnType).append(".\n");
    if (skipPast != null) {
      builder.append("  Skipped:");
      for (int i = 0; i < start; i++) {
        builder.append("\n   * ").append(callAdapterFactories.get(i).getClass().getName());
      }
      builder.append('\n');
    }
    builder.append("  Tried:");
    for (int i = start, count = callAdapterFactories.size(); i < count; i++) {
      builder.append("\n   * ").append(callAdapterFactories.get(i).getClass().getName());
    }
    throw new IllegalArgumentException(builder.toString());
  }

  /**
   * Returns an unmodifiable list of the factories tried when creating a {@linkplain
   * #requestBodyConverter(Type, Annotation[], Annotation[]) request body converter}, a {@linkplain
   * #responseBodyConverter(Type, Annotation[]) response body converter}, or a {@linkplain
   * #stringConverter(Type, Annotation[]) string converter}.
   */
  public List<Converter.Factory> converterFactories() {
    return converterFactories;
  }

  /**
   * Returns a {@link Converter} for {@code type} to {@link RequestBody} from the available
   * {@linkplain #converterFactories() factories}.
   *
   * @throws IllegalArgumentException if no converter available for {@code type}.
   */
  public <T> Converter<T, RequestBody> requestBodyConverter(
      Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
    return nextRequestBodyConverter(null, type, parameterAnnotations, methodAnnotations);
  }

  /**
   * Returns a {@link Converter} for {@code type} to {@link RequestBody} from the available
   * {@linkplain #converterFactories() factories} except {@code skipPast}.
   *
   * @throws IllegalArgumentException if no converter available for {@code type}.
   */
  public <T> Converter<T, RequestBody> nextRequestBodyConverter(
      @Nullable Converter.Factory skipPast,
      Type type,
      Annotation[] parameterAnnotations,
      Annotation[] methodAnnotations) {
    Objects.requireNonNull(type, "type == null");
    Objects.requireNonNull(parameterAnnotations, "parameterAnnotations == null");
    Objects.requireNonNull(methodAnnotations, "methodAnnotations == null");

    int start = converterFactories.indexOf(skipPast) + 1;
    for (int i = start, count = converterFactories.size(); i < count; i++) {
      Converter.Factory factory = converterFactories.get(i);
      Converter<?, RequestBody> converter =
          factory.requestBodyConverter(type, parameterAnnotations, methodAnnotations, this);
      if (converter != null) {
        //noinspection unchecked
        return (Converter<T, RequestBody>) converter;
      }
    }

    StringBuilder builder =
        new StringBuilder("Could not locate RequestBody converter for ").append(type).append(".\n");
    if (skipPast != null) {
      builder.append("  Skipped:");
      for (int i = 0; i < start; i++) {
        builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
      }
      builder.append('\n');
    }
    builder.append("  Tried:");
    for (int i = start, count = converterFactories.size(); i < count; i++) {
      builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
    }
    throw new IllegalArgumentException(builder.toString());
  }

  /**
   * Returns a {@link Converter} for {@link ResponseBody} to {@code type} from the available
   * {@linkplain #converterFactories() factories}.
   *
   * @throws IllegalArgumentException if no converter available for {@code type}.
   */
  public <T> Converter<ResponseBody, T> responseBodyConverter(Type type, Annotation[] annotations) {
    return nextResponseBodyConverter(null, type, annotations);
  }

  /**
   * Returns a {@link Converter} for {@link ResponseBody} to {@code type} from the available
   * {@linkplain #converterFactories() factories} except {@code skipPast}.
   *
   * @throws IllegalArgumentException if no converter available for {@code type}.
   */
  public <T> Converter<ResponseBody, T> nextResponseBodyConverter(
      @Nullable Converter.Factory skipPast, Type type, Annotation[] annotations) {
    Objects.requireNonNull(type, "type == null");
    Objects.requireNonNull(annotations, "annotations == null");

    int start = converterFactories.indexOf(skipPast) + 1;
    for (int i = start, count = converterFactories.size(); i < count; i++) {
      Converter<ResponseBody, ?> converter =
          converterFactories.get(i).responseBodyConverter(type, annotations, this);
      if (converter != null) {
        //noinspection unchecked
        return (Converter<ResponseBody, T>) converter;
      }
    }

    StringBuilder builder =
        new StringBuilder("Could not locate ResponseBody converter for ")
            .append(type)
            .append(".\n");
    if (skipPast != null) {
      builder.append("  Skipped:");
      for (int i = 0; i < start; i++) {
        builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
      }
      builder.append('\n');
    }
    builder.append("  Tried:");
    for (int i = start, count = converterFactories.size(); i < count; i++) {
      builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
    }
    throw new IllegalArgumentException(builder.toString());
  }

  /**
   * Returns a {@link Converter} for {@code type} to {@link String} from the available {@linkplain
   * #converterFactories() factories}.
   */
  public <T> Converter<T, String> stringConverter(Type type, Annotation[] annotations) {
    Objects.requireNonNull(type, "type == null");
    Objects.requireNonNull(annotations, "annotations == null");

    for (int i = 0, count = converterFactories.size(); i < count; i++) {
      Converter<?, String> converter =
          converterFactories.get(i).stringConverter(type, annotations, this);
      if (converter != null) {
        //noinspection unchecked
        return (Converter<T, String>) converter;
      }
    }

    // Nothing matched. Resort to default converter which just calls toString().
    //noinspection unchecked
    return (Converter<T, String>) BuiltInConverters.ToStringConverter.INSTANCE;
  }

  /**
   * The executor used for {@link Callback} methods on a {@link Call}. This may be {@code null}, in
   * which case callbacks should be made synchronously on the background thread.
   */
  public @Nullable Executor callbackExecutor() {
    return callbackExecutor;
  }

  public Builder newBuilder() {
    return new Builder(this);
  }

  /**
   * Build a new {@link Retrofit}.
   *
   * <p>Calling {@link #baseUrl} is required before calling {@link #build()}. All other methods are
   * optional.
   */
  public static final class Builder {
    private final Platform platform;
    private @Nullable okhttp3.Call.Factory callFactory;
    private @Nullable HttpUrl baseUrl;
    private final List<Converter.Factory> converterFactories = new ArrayList<>();
    private final List<CallAdapter.Factory> callAdapterFactories = new ArrayList<>();
    private @Nullable Executor callbackExecutor;
    private boolean validateEagerly;
    private @NotNull Class<?> defaultAnnotationClass = POST.class;
    private @org.jetbrains.annotations.Nullable String singleParameterName = null;
    private @org.jetbrains.annotations.Nullable OtherServiceMethod.Factory<?> otherServiceMethodFactory = null;
    private @org.jetbrains.annotations.Nullable Function2<String,Method, String> handlerUrlListener = null;
    private @org.jetbrains.annotations.NotNull String methodDelimiter = "$";

    Builder(Platform platform) {
      this.platform = platform;
    }

    public Builder() {
      this(Platform.get());
    }

    Builder(Retrofit retrofit) {
      platform = Platform.get();
      callFactory = retrofit.callFactory;
      baseUrl = retrofit.baseUrl;

      // Do not add the default BuiltIntConverters and platform-aware converters added by build().
      for (int i = 1,
              size = retrofit.converterFactories.size() - platform.defaultConverterFactoriesSize();
          i < size;
          i++) {
        converterFactories.add(retrofit.converterFactories.get(i));
      }

      // Do not add the default, platform-aware call adapters added by build().
      for (int i = 0,
              size =
                  retrofit.callAdapterFactories.size() - platform.defaultCallAdapterFactoriesSize();
          i < size;
          i++) {
        callAdapterFactories.add(retrofit.callAdapterFactories.get(i));
      }

      callbackExecutor = retrofit.callbackExecutor;
      validateEagerly = retrofit.validateEagerly;
    }

    /**
     * The HTTP client used for requests.
     *
     * <p>This is a convenience method for calling {@link #callFactory}.
     */
    public Builder client(OkHttpClient client) {
      return callFactory(Objects.requireNonNull(client, "client == null"));
    }

    /**
     * Specify a custom call factory for creating {@link Call} instances.
     *
     * <p>Note: Calling {@link #client} automatically sets this value.
     */
    public Builder callFactory(okhttp3.Call.Factory factory) {
      this.callFactory = Objects.requireNonNull(factory, "factory == null");
      return this;
    }

    /**
     * Set the API base URL.
     *
     * @see #baseUrl(HttpUrl)
     */
    public Builder baseUrl(URL baseUrl) {
      Objects.requireNonNull(baseUrl, "baseUrl == null");
      return baseUrl(HttpUrl.get(baseUrl.toString()));
    }

    /**
     * Set the API base URL.
     *
     * @see #baseUrl(HttpUrl)
     */
    public Builder baseUrl(String baseUrl) {
      Objects.requireNonNull(baseUrl, "baseUrl == null");
      return baseUrl(HttpUrl.get(baseUrl));
    }

    /**
     * Set the API base URL.
     *
     * <p>The specified endpoint values (such as with {@link GET @GET}) are resolved against this
     * value using {@link HttpUrl#resolve(String)}. The behavior of this matches that of an {@code
     * <a href="">} link on a website resolving on the current URL.
     *
     * <p><b>Base URLs should always end in {@code /}.</b>
     *
     * <p>A trailing {@code /} ensures that endpoints values which are relative paths will correctly
     * append themselves to a base which has path components.
     *
     * <p><b>Correct:</b><br>
     * Base URL: http://example.com/api/<br>
     * Endpoint: foo/bar/<br>
     * Result: http://example.com/api/foo/bar/
     *
     * <p><b>Incorrect:</b><br>
     * Base URL: http://example.com/api<br>
     * Endpoint: foo/bar/<br>
     * Result: http://example.com/foo/bar/
     *
     * <p>This method enforces that {@code baseUrl} has a trailing {@code /}.
     *
     * <p><b>Endpoint values which contain a leading {@code /} are absolute.</b>
     *
     * <p>Absolute values retain only the host from {@code baseUrl} and ignore any specified path
     * components.
     *
     * <p>Base URL: http://example.com/api/<br>
     * Endpoint: /foo/bar/<br>
     * Result: http://example.com/foo/bar/
     *
     * <p>Base URL: http://example.com/<br>
     * Endpoint: /foo/bar/<br>
     * Result: http://example.com/foo/bar/
     *
     * <p><b>Endpoint values may be a full URL.</b>
     *
     * <p>Values which have a host replace the host of {@code baseUrl} and values also with a scheme
     * replace the scheme of {@code baseUrl}.
     *
     * <p>Base URL: http://example.com/<br>
     * Endpoint: https://github.com/square/retrofit/<br>
     * Result: https://github.com/square/retrofit/
     *
     * <p>Base URL: http://example.com<br>
     * Endpoint: //github.com/square/retrofit/<br>
     * Result: http://github.com/square/retrofit/ (note the scheme stays 'http')
     */
    public Builder baseUrl(HttpUrl baseUrl) {
      Objects.requireNonNull(baseUrl, "baseUrl == null");
      List<String> pathSegments = baseUrl.pathSegments();
      if (!"".equals(pathSegments.get(pathSegments.size() - 1))) {
        throw new IllegalArgumentException("baseUrl must end in /: " + baseUrl);
      }
      this.baseUrl = baseUrl;
      return this;
    }

    /**
     * 设置如果没有注解默认使用哪个注解,且参数的注解也会相应切换
     * 仅支持GET.class和POST.class,默认是POST.class
     * 如果是POST,则参数的默认注解是@Field,如果是GET,则参数的默认注解是@Query
     */
    public Builder defaultAnnotation(@NotNull Class<?> defaultAnnotationClass) {
      if (defaultAnnotationClass != POST.class && defaultAnnotationClass != GET.class)
        throw new IllegalArgumentException("defaultAnnotation must set GET.class or POST.class");
      this.defaultAnnotationClass = defaultAnnotationClass;
      return this;
    }

    /**
     * 设置是否使用入参整合,如果[isSingleParameter]为true就将所有入参合为一个并将[singleParameterName]设置为唯一参数的键值
     */
    public Builder setSingleParameter(String singleParameterName) {
      this.singleParameterName = singleParameterName;
      return this;
    }

    /**
     * 设置动态代理方法转对象的工厂,可以自定义Retrofit.Call的生成
     */
    public Builder setServiceMethodFactory(OtherServiceMethod.Factory<?> otherServiceMethodFactory) {
      this.otherServiceMethodFactory = otherServiceMethodFactory;
      return this;
    }

    /** Add converter factory for serialization and deserialization of objects. */
    public Builder addConverterFactory(Converter.Factory factory) {
      converterFactories.add(Objects.requireNonNull(factory, "factory == null"));
      return this;
    }

    /**
     * Add a call adapter factory for supporting service method return types other than {@link
     * Call}.
     */
    public Builder addCallAdapterFactory(CallAdapter.Factory factory) {
      callAdapterFactories.add(Objects.requireNonNull(factory, "factory == null"));
      return this;
    }

    /**
     * The executor on which {@link Callback} methods are invoked when returning {@link Call} from
     * your service method.
     *
     * <p>Note: {@code executor} is not used for {@linkplain #addCallAdapterFactory custom method
     * return types}.
     */
    public Builder callbackExecutor(Executor executor) {
      this.callbackExecutor = Objects.requireNonNull(executor, "executor == null");
      return this;
    }

    /** Returns a modifiable list of call adapter factories. */
    public List<CallAdapter.Factory> callAdapterFactories() {
      return this.callAdapterFactories;
    }

    /** Returns a modifiable list of converter factories. */
    public List<Converter.Factory> converterFactories() {
      return this.converterFactories;
    }

    /**
     * When calling {@link #create} on the resulting {@link Retrofit} instance, eagerly validate the
     * configuration of all methods in the supplied interface.
     */
    public Builder validateEagerly(boolean validateEagerly) {
      this.validateEagerly = validateEagerly;
      return this;
    }

    /**
     * 设置可以hook短url的回调,返回新的url,如果不处理可以直接将传入的返回
     * 例如:POST("a/b"),可以通过[handlerUrlListener={s,m->"v/$s"}]将其改为v/a/b
     * [handlerUrlListener]回调的String为原始url,Method为当前的方法对象,需要返回String的新url
     */
    public Builder setHandlerUrlListener(Function2<String, Method, String> handlerUrlListener) {
      this.handlerUrlListener = handlerUrlListener;
      return this;
    }

    /**
     * 设置不写POST和GET注解时,method的名字中的分隔符
     * 比如默认是$ :  a$b url转换为a/b
     *            可以改成 _   a_b转换为a/b
     */
    public Builder setMethodDelimiter(@org.jetbrains.annotations.NotNull String methodDelimiter) {
      this.methodDelimiter = methodDelimiter;
      return this;
    }

    /**
     * Create the {@link Retrofit} instance using the configured values.
     *
     * <p>Note: If neither {@link #client} nor {@link #callFactory} is called a default {@link
     * OkHttpClient} will be created and used.
     */
    public Retrofit build() {
      if (baseUrl == null) {
        throw new IllegalStateException("Base URL required.");
      }

      okhttp3.Call.Factory callFactory = this.callFactory;
      if (callFactory == null) {
        callFactory = new OkHttpClient();
      }

      Executor callbackExecutor = this.callbackExecutor;
      if (callbackExecutor == null) {
        callbackExecutor = platform.defaultCallbackExecutor();
      }

      // Make a defensive copy of the adapters and add the default Call adapter.
      List<CallAdapter.Factory> callAdapterFactories = new ArrayList<>(this.callAdapterFactories);
      callAdapterFactories.addAll(platform.defaultCallAdapterFactories(callbackExecutor));

      // Make a defensive copy of the converters.
      List<Converter.Factory> converterFactories =
          new ArrayList<>(
              1 + this.converterFactories.size() + platform.defaultConverterFactoriesSize());

      // Add the built-in converter factory first. This prevents overriding its behavior but also
      // ensures correct behavior when using converters that consume all types.
      converterFactories.add(new BuiltInConverters());
      converterFactories.addAll(this.converterFactories);
      converterFactories.addAll(platform.defaultConverterFactories());

      return new Retrofit(
              callFactory,
              baseUrl,
              unmodifiableList(converterFactories),
              unmodifiableList(callAdapterFactories),
              callbackExecutor,
              validateEagerly,
              defaultAnnotationClass,
              singleParameterName,
              otherServiceMethodFactory,
              handlerUrlListener,
              methodDelimiter);
    }
  }
}

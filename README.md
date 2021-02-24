Retrofit
========

A type-safe HTTP client for Android and Java(Kotlin).

For more information please see [the website][1].

使用说明:https://blog.csdn.net/qq_33505109/article/details/108767068

中文简要功能说明:
1.可以不写注解就完成方法的声明
2.程序运行中途可以修改BaseUrl
3.将方法的所有参数合并到一个参数(某些后端需求)

Brief function description in English:
1. You can complete the method declaration without writing comments
2. Baseurl can be modified in the middle of program running
3. Merge all parameters of the method into one parameter


Download
--------

Download [the latest JAR][2] or grab from Maven central at the coordinates `com.github.ltttttttttttt:retrofit:1.1.9`.

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

Retrofit requires at minimum Java 8+ or Android API 21+.


R8 / ProGuard
-------------

If you are using R8 the shrinking and obfuscation rules are included automatically.

ProGuard users must manually add the options from
[retrofit2.pro][proguard file].
You might also need [rules for OkHttp][okhttp proguard] and [Okio][okio proguard] which are dependencies of this library.


License
=======

    Copyright 2013 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


 [1]: https://square.github.io/retrofit/
 [2]: https://search.maven.org/remote_content?g=com.squareup.retrofit2&a=retrofit&v=LATEST
 [snap]: https://oss.sonatype.org/content/repositories/snapshots/
 [proguard file]: https://github.com/square/retrofit/blob/master/retrofit/src/main/resources/META-INF/proguard/retrofit2.pro
 [okhttp proguard]: https://square.github.io/okhttp/#r8-proguard
 [okio proguard]: https://square.github.io/okio/#r8-proguard

AWARE Framework Android Client
======================
[![Release](https://jitpack.io/v/denzilferreira/aware-client.svg)](https://jitpack.io/#denzilferreira/aware-client)

AWARE is an Android framework dedicated to instrument, infer, log and share mobile context information,
for application developers, researchers and smartphone users. AWARE captures hardware-, software-, and human-based data. It encapsulates analysis, machine learning and simplifies conducting user studies in naturalistic and laboratory settings. 

![User Studies](http://www.awareframework.com/wp-content/uploads/2014/05/aware_overview1.png)

The platform is scalable with plugins and can be integrated with other platforms using JSON, MQTT or MySQL.

![Arquitecture](http://www.awareframework.com/wp-content/uploads/2015/12/aware-architecture.png)

Getting started
===============
Add to the build.gradle inside your module to include AWARE's libraries

```Gradle
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    api "com.github.denzilferreira:aware-client:master-SNAPSHOT"
}
```

You can now refer to AWARE's functions inside your app.


Individuals: Record your own data
=================================
![Individuals](http://www.awareframework.com/wp-content/uploads/2014/05/personal.png)

No programming skills are required. The mobile application allows you to enable or disable sensors and plugins. The data is saved locally on your mobile phone. Privacy is enforced by design, so AWARE does not log personal information, such as phone numbers or contacts information. You can additionally install plugins that will further enhance the capabilities of your device, straight from the client.

Scientists: Run studies
=======================
![Scientists](http://www.awareframework.com/wp-content/uploads/2014/05/scientist.png)

Running a mobile related study has never been easier. Install AWARE on the participants phone, select the data you want to collect and that is it. If you use the AWARE dashboard, you can request your participants’ data, check their participation and remotely trigger mobile ESM (Experience Sampling Method) questionnaires, anytime and anywhere from the convenience of your Internet browser. The framework does not record the data you need? Check our tutorials to learn how to create your own plugins, or just contact us to help you with your study! Our research group is always willing to collaborate.

Developers: Make your apps smarter
==================================
![Developers](http://www.awareframework.com/wp-content/uploads/2014/05/developers.png)

Nothing is more stressful than to interrupt a mobile phone user at the most unfortunate moments. AWARE provides application developers with user’s context using AWARE’s API. AWARE is available as an Android library. User’s current context is shared at the operating system level, thus empowering richer context-aware applications for the end-users.

Open-source (Apache 2.0)
=========
Copyright (c) 2011 AWARE Mobile Context Instrumentation Middleware/Framework 
http://www.awareframework.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at 
http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
# Install Optimize SDK extension in your mobile application

> [!WARNING]
> The Optimize extension depends on the `Mobile Core` and `Edge network` extensions and requires these extensions to be installed in your mobile application.

Add the Mobile Core, Edge, Identity for Edge Network and Optimize dependencies in your app level `build.gradle` file under `dependencies` (**1**).

**Gradle file Example**
```java
dependencies {
    ....................................................
    
    implementation 'com.adobe.marketing.mobile:optimize:1.+'
    implementation 'com.adobe.marketing.mobile:edgeconsent:1.+'
    implementation 'com.adobe.marketing.mobile:edge:1.+'
    implementation 'com.adobe.marketing.mobile:edgeidentity:1.+'
    implementation 'com.adobe.marketing.mobile:assurance:1.+'
    implementation 'com.adobe.marketing.mobile:sdk-core:1.+'
    implementation 'com.adobe.marketing.mobile:userprofile:1.+'
    
    ....................................................
}
```

Click on `Sync Now` in the top right corner (**2**)to install the dependencies.

| ![Adding Gradle dependencies](../../assets/android-gradle-dependencies.png?raw=true) |
| :---: |
| **Adding Gradle dependencies** |

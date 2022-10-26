# Initialize the Adobe Experience Platform Mobile SDKs

Initialize the Experience Platform Mobile SDKs by adding the below code in your `Application` class.

> [!TIP]
> You can find your Environment File ID and also the mobile SDK initialization code in your tag property on Experience Platform Data Collection UI. Navigate to Environments (select your environment - Production, Staging, or Development), click <small>INSTALL<small>.

| ![SDK Initialization Code](../../assets/sdk-init-code.png?raw=true) |
| :---: |
| **SDK Initialization Code** |

**Application class Example**
<!-- tabs:start -->

#### **Android**
```kotlin
// MainApplication.kt

import com.adobe.marketing.mobile.Assurance
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.Lifecycle
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.optimize.Optimize

class MainApplication : Application() {

    companion object {
        const val LAUNCH_ENVIRONMENT_FILE_ID = ""
    }

    override fun onCreate() {
        super.onCreate()

        MobileCore.setApplication(this)
        MobileCore.setLogLevel(LoggingMode.VERBOSE)

        Identity.registerExtension()
        Lifecycle.registerExtension()
        Edge.registerExtension()
        Optimize.registerExtension()
        Assurance.registerExtension()

        MobileCore.configureWithAppID(LAUNCH_ENVIRONMENT_FILE_ID)
        MobileCore.start {
            print("Adobe mobile SDKs are successfully registered.")
        }
    }
}
```
<!-- tabs:end -->

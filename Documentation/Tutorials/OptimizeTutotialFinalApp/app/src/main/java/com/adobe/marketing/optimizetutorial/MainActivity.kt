/*
 Copyright 2021 Adobe. All rights reserved.
 This file is licensed to you under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under
 the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 OF ANY KIND, either express or implied. See the License for the specific language
 governing permissions and limitations under the License.
 */
package com.adobe.marketing.optimizetutorial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.optimizetutorial.ui.theme.OptimizeTheme
import com.adobe.marketing.optimizetutorial.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OptimizeTheme {
                MainScreen(viewModel)
            }
        }
    }

    //* Optimize Tutorial: CODE SECTION 3/10 BEGINS
    override fun onResume() {
        super.onResume()
        MobileCore.setApplication(application)
        MobileCore.lifecycleStart(null)
    }

    override fun onPause() {
        super.onPause()
        MobileCore.lifecyclePause()
    }
    // Optimize Tutorial: CODE SECTION 3 ENDS */
}



@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    OptimizeTheme {
//        MainScreen()
    }
}
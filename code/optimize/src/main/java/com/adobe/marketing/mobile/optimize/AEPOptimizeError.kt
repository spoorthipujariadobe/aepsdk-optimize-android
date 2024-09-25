/*
  Copyright 2024 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.optimize
import com.adobe.marketing.mobile.AdobeError
public class AEPOptimizeError(val type: String? = "", val status: Int? = 0, val title: String? = "", val detail: String? = "", var report: Map<String, Any>?, var adobeError: AdobeError?) {
    init {
        if (adobeError == null) {
            adobeError = when (status) {
                408 -> AdobeError.CALLBACK_TIMEOUT
                400, 403, 404 -> AdobeError.UNEXPECTED_ERROR
                429, 500, 503 -> AdobeError.UNEXPECTED_ERROR
                502, 504 -> AdobeError.UNEXPECTED_ERROR
                else -> AdobeError.UNEXPECTED_ERROR
            }
        }
    }

    object AEPOptimizeErrors {
        val TIMEOUT_ERROR: AEPOptimizeError = AEPOptimizeError(
            null,
            OptimizeConstants.ErrorData.Timeout.STATUS,
            OptimizeConstants.ErrorData.Timeout.TITLE,
            OptimizeConstants.ErrorData.Timeout.DETAIL,
            null,
            AdobeError.CALLBACK_TIMEOUT
        )

        val UNEXPECTED_ERROR: AEPOptimizeError = AEPOptimizeError(
            null,
            null,
            OptimizeConstants.ErrorData.Unexpected.TITLE,
            OptimizeConstants.ErrorData.Unexpected.DETAIL,
            null,
            AdobeError.UNEXPECTED_ERROR
        )
    }
}

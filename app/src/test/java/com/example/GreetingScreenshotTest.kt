package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.BusinessProfileEntity
import com.example.ui.screens.onboarding.OnboardingFlow
import com.example.ui.theme.ArroPosTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun welcome_step_screenshot() {
    composeTestRule.setContent {
      ArroPosTheme {
        OnboardingFlow(
          profile = BusinessProfileEntity(),
          currentStep = 1,
          onStepChange = {},
          onFinish = { _, _ -> },
          onOpenPrinterSetup = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/welcome.png")
  }
}

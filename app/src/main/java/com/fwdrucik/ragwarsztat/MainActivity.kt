package com.fwdrucik.ragwarsztat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.fwdrucik.ragwarsztat.theme.WarsztatRagTheme
import com.fwdrucik.ragwarsztat.ui.MainScreen
import com.fwdrucik.ragwarsztat.ui.RagViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RagViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WarsztatRagTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

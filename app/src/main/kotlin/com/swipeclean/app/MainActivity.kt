package com.swipeclean.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.swipeclean.app.data.MediaRepository
import com.swipeclean.app.data.TrashStorage
import com.swipeclean.app.ui.SwipeCleanAppRoot
import com.swipeclean.app.ui.SwipeViewModel
import com.swipeclean.app.ui.theme.SwipeCleanTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SwipeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = MediaRepository(applicationContext)
                val trashStorage = TrashStorage(applicationContext)
                return SwipeViewModel(repository, trashStorage) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwipeCleanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SwipeCleanAppRoot(viewModel = viewModel)
                }
            }
        }
    }
}

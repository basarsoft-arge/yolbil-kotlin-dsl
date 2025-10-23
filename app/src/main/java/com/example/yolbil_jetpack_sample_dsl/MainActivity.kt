package com.example.yolbil_jetpack_sample_dsl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.basarsoft.yolbil.ui.MapView
import com.example.yolbil_jetpack_sample_dsl.ui.theme.YolbiljetpacksampleTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YolbiljetpacksampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    YolbilMapScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun YolbilMapScreen(
    modifier: Modifier = Modifier,
    viewModel: YolbilViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // MapView
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        viewModel.initializeMapView(this)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )

            // Katman seçim butonları
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.showGoogleRaster() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Google Raster")
                }
                Button(
                    onClick = { viewModel.showBmsVector(context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("BMS Vector")
                }
            }

            // Navigasyon başlat
            Button(
                onClick = { viewModel.startNavigation() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = "Start Navigation")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    YolbiljetpacksampleTheme {
        YolbilMapScreen()
    }
}

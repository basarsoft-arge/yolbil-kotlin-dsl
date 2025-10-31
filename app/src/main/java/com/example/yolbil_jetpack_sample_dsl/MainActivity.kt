package com.example.yolbil_jetpack_sample_dsl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.basarsoft.yolbil.ui.MapView
import com.example.yolbil_jetpack_sample_dsl.ui.theme.YolbiljetpacksampleTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fine || coarse) {
            setContentForMap()
        } else {
            setContent { PermissionDeniedScreen() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // İlk kontrol
        if (isLocationPermissionGranted()) {
            setContentForMap()
        } else {
            // İzin yok → iste
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun setContentForMap() {
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

    private fun isLocationPermissionGranted(): Boolean {
        val fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
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

            viewModel.navigationInfo?.let { info ->
                NavigationInfoPanel(
                    remainingTime = info.remainingTime,
                    remainingDistance = info.remainingDistance,
                    eta = info.eta
                )
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

@Composable
fun NavigationInfoPanel(
    remainingTime: String,
    remainingDistance: String,
    eta: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .background(Color(0xFF1E3A56), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Kalan Süre: $remainingTime",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Kalan Mesafe: $remainingDistance",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Varış Saati: $eta",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun PermissionDeniedScreen() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Konum izni olmadan harita açılamaz.")
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    YolbiljetpacksampleTheme {
        YolbilMapScreen()
    }
}

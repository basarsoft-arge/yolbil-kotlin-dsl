package com.example.yolbil_jetpack_sample_dsl

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Button
import androidx.lifecycle.ViewModel
import com.basarsoft.inavi.libs.sensormanager.SensorManager
import com.basarsoft.yolbil.core.MapBounds
import com.basarsoft.yolbil.core.MapPos
import com.basarsoft.yolbil.datasources.HTTPTileDataSource
import com.basarsoft.yolbil.datasources.TileDownloadListener
import com.basarsoft.yolbil.datasources.YBOfflineStoredDataSource
import com.basarsoft.yolbil.layers.RasterTileLayer
import com.basarsoft.yolbil.layers.TileLoadListener
import com.basarsoft.yolbil.layers.VectorTileLayer
import com.basarsoft.yolbil.layers.VectorTileRenderOrder
import com.basarsoft.yolbil.location.GPSLocationSource
import com.basarsoft.yolbil.location.Location
import com.basarsoft.yolbil.location.LocationListener
import com.basarsoft.yolbil.projections.EPSG4326
import com.basarsoft.yolbil.styles.CompiledStyleSet
import com.basarsoft.yolbil.ui.MapEventListener
import com.basarsoft.yolbil.ui.MapInteractionInfo
import com.basarsoft.yolbil.ui.MapView
import com.basarsoft.yolbil.utils.AssetUtils
import com.basarsoft.yolbil.utils.ZippedAssetPackage
import com.basarsoft.yolbil.vectortiles.MBVectorTileDecoder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class YolbilViewModel @Inject constructor() : ViewModel() {

    // ---- BMS erişim bilgilerin
    private val appCode = "app_code"
    private val accId = "acc_id"

    private var mapView: MapView? = null

    var gpsLocationSource: GPSLocationSource? = null
    var lastLocation: Location? = null
    var focusPos: Button? = null
    var startNavigation: android.widget.Button? = null
    var isLocationFound: Boolean = false


    // Navigasyon sınıfın (senin paylaştığın sürümle uyumlu)
    private val navigationUsage: YolbilNavigationUsage = YolbilNavigationUsage()

    var startPos: MapPos? = null
    var endPos: MapPos? = null

    // ---- Katman geçişi için alanlar
    private var googleRasterLayer: RasterTileLayer? = null
    private var googleRasterCache: YBOfflineStoredDataSource? = null

    private var bmsVectorLayer: VectorTileLayer? = null
    private var bmsVectorDataSource: YBOfflineStoredDataSource? = null
    private var bmsVectorDecoder: MBVectorTileDecoder? = null

    @SuppressLint("MissingPermission")
    fun initializeMapView(mapView: MapView) {
        this.mapView = mapView

        // Başlangıç/varış örnekleri
        startPos = MapPos(32.8597, 39.9334) // Kızılay
        endPos   = MapPos(32.8547, 39.9250) // Anıtkabir

        // Projeksiyon & kamera
        mapView.options.baseProjection = EPSG4326()
        mapView.setFocusPos(MapPos(32.836262, 39.960160), 0f)
        mapView.setZoom(17.0f, 0.0f)

        Log.e("sensormanager", SensorManager.getModuleInfo())

        // --- GOOGLE RASTER TABANI (varsayılan)
        val googleHttp = HTTPTileDataSource(
            0, 18,
            "https://mt0.google.com/vt/lyrs=m&hl=tr&scale=4&apistyle=s.t%3A2%7Cs.e%3Al%7Cp.v%3Aoff&x={x}&y={y}&z={zoom}"
        )
        googleRasterCache = YBOfflineStoredDataSource(googleHttp, "/storage/emulated/0/.cachetile.db").apply {
            isCacheOnlyMode = false
        }

        val bounds = MapBounds(MapPos(32.836262, 39.960160), MapPos(32.836262, 39.960160))
        googleRasterCache?.startDownloadArea(bounds, 0, 10, object : TileDownloadListener() {
            override fun onDownloadProgress(progress: Float) {}
            override fun onDownloadCompleted() {}
        })

        googleRasterLayer = RasterTileLayer(googleRasterCache).also { layer ->
            layer.tileLoadListener = object : TileLoadListener() {
                override fun onVisibleTilesLoaded() {
                    Log.d("YolbilViewModel", "Visible tiles loaded")
                }
            }
        }
        mapView.layers.add(googleRasterLayer)

        // --- GPS
        gpsLocationSource = GPSLocationSource(mapView.context).also { it.startLocationUpdates() }
        gpsLocationSource!!.addListener(object : LocationListener() {
            override fun onLocationChange(location: Location) {
                if (!isLocationFound) {
                    lastLocation = location
                    isLocationFound = true
                }
            }
        })
    }


    /** Navigasyonu başlatır: önce rota kur, sonra beginNavigation */
    fun startNavigation() {
        createRoute()
        mapView?.isDeviceOrientationFocused = false
        navigationUsage.startNavigation()
    }

    fun createRoute() {
        val mv = mapView ?: run {
            Log.e("YolbilViewModel", "MapView not initialized")
            return
        }
        val start = startPos ?: return
        val end = endPos ?: return
        val gps = gpsLocationSource ?: return

        navigationUsage.fullExample(
            mapView = mv,
            start = start,
            end = end,
            isOffline = false,
            locationSource = gps
        )
    }

    fun stopNavigation() {
        navigationUsage.stopNavigation()
    }

    // ------------------------------------------------------------
    //                 KATMAN GEÇİŞİ (Google ↔ BMS)
    // ------------------------------------------------------------

    /** Google raster katmanını gösterir, BMS vector’ü kaldırır. */
    fun showGoogleRaster() {
        val mv = mapView ?: return
        bmsVectorLayer?.let { mv.layers.remove(it) }
        googleRasterLayer?.let {
            mv.layers.remove(it)
            mv.layers.insert(0, it) // altlığı en alta koy
        }
        bringRouteLayersToTop(mv, navigationUsage.routeLayers())
    }



    /** BMS vector tile katmanını gösterir, Google raster’ı kaldırır. */
    fun showBmsVector(context: Context) {
        val mv = mapView ?: return

        // Google raster varsa kaldır
        googleRasterLayer?.let { layer ->
            runCatching { mv.layers.remove(layer) }
        }

        // İlk kez yükleniyorsa decoder + datasource + layer oluştur
        if (bmsVectorLayer == null) {
            try {
                // 1) Stil paketini assets’ten yükle
                // assets içine "transport_style_final_package_latest_light.zip" dosyasını eklemelisin.
                val styleAsset = AssetUtils.loadAsset("transport_style_final_package_latest_light.zip")
                if (styleAsset == null) {
                    Log.e("YolbilViewModel", "Vector style asset bulunamadı!")
                    showGoogleRaster()
                    return
                }

                val assetPackage = ZippedAssetPackage(styleAsset)
                val compiledStyle = CompiledStyleSet(assetPackage, "transport_style") // style adın
                bmsVectorDecoder = MBVectorTileDecoder(compiledStyle).apply {
                    // Tema parametresi istersen:
                    setStyleParameter("selectedTheme", "light")
                }

                // 2) BMS vektör PBF kaynağı
                val vectorHttp = HTTPTileDataSource(
                    0, 15,
                    "https://bms.basarsoft.com.tr/Service/api/v1/VectorMap/Pbf?accId=$accId&appCode=$appCode&x={x}&y={y}&z={z}"
                )
                bmsVectorDataSource = YBOfflineStoredDataSource(
                    vectorHttp,
                    "/storage/emulated/0/.vector.cachetile.db"
                ).apply {
                    isCacheOnlyMode = false
                }

                // 3) Vector tile layer
                bmsVectorLayer = VectorTileLayer(bmsVectorDataSource, bmsVectorDecoder).apply {
                    labelRenderOrder = VectorTileRenderOrder.VECTOR_TILE_RENDER_ORDER_LAST
                    buildingRenderOrder = VectorTileRenderOrder.VECTOR_TILE_RENDER_ORDER_LAYER
                }
            } catch (e: Exception) {
                Log.e("YolbilViewModel", "BMS vector layer oluşturulamadı: ${e.message}")
                showGoogleRaster()
                return
            }

        }

        // BMS layer’ı ekle (ekli değilse)
        bmsVectorLayer?.let { layer ->
            mv.layers.remove(layer)
            mv.layers.add(layer)
            bringRouteLayersToTop(mv, navigationUsage.routeLayers())

        }
    }
}


private fun bringRouteLayersToTop(mv: MapView, lv: com.basarsoft.yolbil.layers.LayerVector?) {
    if (lv == null) return
    val n = lv.size().toInt()
    for (i in 0 until n) {
        val l = lv.get(i)
        mv.layers.remove(l)
        mv.layers.add(l) // en üste
    }
}

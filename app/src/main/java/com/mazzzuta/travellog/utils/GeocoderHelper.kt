package com.mazzzuta.travellog.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class GeocoderHelper(context: Context) {

    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun getPlaceName(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val address = getAddress(latitude, longitude) ?: return null
        val city = address.locality ?: address.subAdminArea ?: address.adminArea
        val country = address.countryName
        return listOfNotNull(city, country).joinToString(", ").ifBlank { null }
    }

    private suspend fun getAddress(lat: Double, lon: Double): Address? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    continuation.resume(addresses.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }
}
package com.mazzzuta.travellog.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class GeocoderHelper(private val context: Context) {

    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun getPlaceName(latitude: Double, longitude: Double): String? {
        if (!context.hasInternetConnection()) throw NoInternetException()
        if (!Geocoder.isPresent()) return null
        val address = withTimeoutOrNull(8_000) { getAddress(latitude, longitude) } ?: return null
        return formatPlaceName(address).ifBlank { null }
    }

    private suspend fun getAddress(lat: Double, lon: Double): Address? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (continuation.isActive) continuation.resume(addresses.firstOrNull()) }
                    override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resume(null) }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                awaitLegacyAddress { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun searchPlace(query: String): Triple<Double, Double, String>? {
        if (query.isBlank()) return null
        if (!context.hasInternetConnection()) throw NoInternetException()
        if (!Geocoder.isPresent()) return null
        val address = withTimeoutOrNull(8_000) { searchAddress(query) } ?: return null
        return Triple(address.latitude, address.longitude, formatPlaceName(address, query))
    }

    private suspend fun searchAddress(query: String): Address? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (continuation.isActive) continuation.resume(addresses.firstOrNull()) }
                    override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resume(null) }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                awaitLegacyAddress { geocoder.getFromLocationName(query, 1)?.firstOrNull() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }
}

// Старый Geocoder блокирует поток: отмена ожидания должна освободить форму, даже если системный вызов ещё не вернулся.
internal suspend fun awaitLegacyAddress(search: () -> Address?): Address? = suspendCancellableCoroutine { continuation ->
    val job = CoroutineScope(Dispatchers.IO).launch {
        try {
            val address = search()
            if (continuation.isActive) continuation.resume(address)
        } catch (e: CancellationException) { continuation.cancel(e) }
        catch (_: Exception) { if (continuation.isActive) continuation.resume(null) }
    }
    continuation.invokeOnCancellation { job.cancel() }
}

internal fun formatPlaceName(address: Address, query: String? = null): String {
    val city = address.locality ?: address.subAdminArea ?: address.adminArea
    val country = address.countryName
    val street = listOfNotNull(address.thoroughfare, address.subThoroughfare).joinToString(" ").ifBlank { null }
    val feature = address.featureName?.takeUnless { name ->
        name.isBlank() || listOf(city, country, address.adminArea, address.subThoroughfare).any { it.equals(name, ignoreCase = true) }
    }
    val detail = query?.trim()?.takeIf { it.isNotEmpty() } ?: street ?: feature ?: address.getAddressLine(0)
    return (detail.orEmpty().split(",") + listOfNotNull(city, country)).map { it.trim() }
        .filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.getDefault()) }.joinToString(", ")
}

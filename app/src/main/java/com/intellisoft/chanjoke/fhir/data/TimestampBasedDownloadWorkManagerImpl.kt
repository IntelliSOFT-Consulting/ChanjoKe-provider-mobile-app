//package com.intellisoft.chanjoke.fhir.data
//
//import com.google.android.fhir.sync.DownloadWorkManager
//import com.google.android.fhir.sync.SyncDataParams
//import com.google.android.fhir.sync.download.DownloadRequest
//import com.intellisoft.chanjoke.fhir.DemoDataStore
//import java.time.ZoneId
//import java.time.format.DateTimeFormatter
//import java.util.Date
//import java.util.LinkedList
//import java.util.Locale
//import org.hl7.fhir.exceptions.FHIRException
//import org.hl7.fhir.r4.model.Bundle
//import org.hl7.fhir.r4.model.ListResource
//import org.hl7.fhir.r4.model.OperationOutcome
//import org.hl7.fhir.r4.model.Reference
//import org.hl7.fhir.r4.model.Resource
//import org.hl7.fhir.r4.model.ResourceType
//
//class TimestampBasedDownloadWorkManagerImpl(private val dataStore: DemoDataStore) :
//    DownloadWorkManager {
//    private val resourceTypeList = ResourceType.values().map { it.name }
//    private val urls = LinkedList(
//        listOf(
//            "Patient?_sort=_lastUpdated",
//            "ImmunizationRecommendation?_count=1000",
//            "CarePlan?_count=100",
//            "Immunization?_count=1000",
//            "Practitioner?_count=100",
//            "ServiceRequest?_count=100",
//            "RelatedPerson",
//            "AdverseEvent?_count=1000",
//
//        )
//    )
//
//    override suspend fun getNextRequest(): DownloadRequest? {
//        var url = urls.poll() ?: return null
//
//        val resourceTypeToDownload =
//            ResourceType.fromCode(url.findAnyOf(resourceTypeList, ignoreCase = true)!!.second)
//        dataStore.getLastUpdateTimestamp(resourceTypeToDownload)?.let {
//            url = affixLastUpdatedTimestamp(url, it)
//        }
//        return DownloadRequest.of(url)
//    }
//
//
//
//    override suspend fun getSummaryRequestUrls(): Map<ResourceType, String> {
//        return urls.associate { url ->
//            val resourceType = ResourceType.fromCode(url.substringBefore("?"))
//            if (resourceType == ResourceType.Patient) {
//                resourceType to url.plus("&${SyncDataParams.SUMMARY_KEY}=${SyncDataParams.SUMMARY_COUNT_VALUE}")
//            } else {
//                resourceType to url
//            }
//        }
//    }
//
//
//    override suspend fun processResponse(response: Resource): Collection<Resource> {
//        // As per FHIR documentation :
//        // If the search fails (cannot be executed, not that there are no matches), the
//        // return value SHALL be a status code 4xx or 5xx with an OperationOutcome.
//        // See https://www.hl7.org/fhir/http.html#search for more details.
//        if (response is OperationOutcome) {
//            throw FHIRException(response.issueFirstRep.diagnostics)
//        }
//
//        // If the resource returned is a List containing Patients, extract Patient references and fetch
//        // all resources related to the patient using the $everything operation.
//        if (response is ListResource) {
//            for (entry in response.entry) {
//                val reference = Reference(entry.item.reference)
//                if (reference.referenceElement.resourceType.equals("Patient")) {
//                    val patientUrl = "${entry.item.reference}/\$everything"
//                    urls.add(patientUrl)
//                }
//            }
//        }
//
//        // If the resource returned is a Bundle, check to see if there is a "next" relation referenced
//        // in the Bundle.link component, if so, append the URL referenced to list of URLs to download.
//        if (response is Bundle) {
//            val nextUrl =
//                response.link.firstOrNull { component -> component.relation == "next" }?.url
//            if (nextUrl != null) {
//                urls.add(nextUrl)
//            }
//        }
//
//        // Finally, extract the downloaded resources from the bundle.
//        var bundleCollection: Collection<Resource> = mutableListOf()
//        if (response is Bundle && response.type == Bundle.BundleType.SEARCHSET) {
//            bundleCollection =
//                response.entry
//                    .map { it.resource }
//                    .also { extractAndSaveLastUpdateTimestampToFetchFutureUpdates(it) }
//        }
//        return bundleCollection
//    }
//
//    private suspend fun extractAndSaveLastUpdateTimestampToFetchFutureUpdates(
//        resources: List<Resource>,
//    ) {
//        resources
//            .groupBy { it.resourceType }
//            .entries
//            .map { map ->
//                dataStore.saveLastUpdatedTimestamp(
//                    map.key,
//                    map.value.maxOfOrNull { it.meta.lastUpdated }?.toTimeZoneString() ?: "",
//                )
//            }
//    }
//}
//
///**
// * Affixes the last updated timestamp to the request URL.
// *
// * If the request URL includes the `$everything` parameter, the last updated timestamp will be
// * attached using the `_since` parameter. Otherwise, the last updated timestamp will be attached
// * using the `_lastUpdated` parameter.
// */
//private fun affixLastUpdatedTimestamp(url: String, lastUpdated: String): String {
//    var downloadUrl = url
//
//    // Affix lastUpdate to a $everything query using _since as per:
//    // https://hl7.org/fhir/operation-patient-everything.html
//    if (downloadUrl.contains("\$everything")) {
//        downloadUrl = "$downloadUrl?_since=$lastUpdated"
//    }
//
//    // Affix lastUpdate to non-$everything queries as per:
//    // https://hl7.org/fhir/operation-patient-everything.html
//    if (!downloadUrl.contains("\$everything")) {
//        if (!downloadUrl.contains("ImmunizationRecommendation")) {
//            downloadUrl = "$downloadUrl&_lastUpdated=gt$lastUpdated"
//        }
//    }
//
//
////    if (!downloadUrl.contains("\$everything") && downloadUrl.contains("ServiceRequest")) {
////        downloadUrl = "$downloadUrl?&_lastUpdated=gt$lastUpdated"
////    }
//
//
//    // Do not modify any URL set by a server that specifies the token of the page to return.
//    if (downloadUrl.contains("&page_token")) {
//        downloadUrl = url
//    }
//
//    return downloadUrl
//}
//
//private fun Date.toTimeZoneString(): String {
//    val simpleDateFormat =
//        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
//            .withZone(ZoneId.systemDefault())
//    return simpleDateFormat.format(this.toInstant())
//}
package com.intellisoft.chanjoke.fhir.data

import com.intellisoft.chanjoke.fhir.DemoDataStore
import com.google.android.fhir.sync.DownloadWorkManager
import com.google.android.fhir.sync.Request
import com.google.android.fhir.sync.SyncDataParams
import com.intellisoft.chanjoke.utils.Constants
import com.intellisoft.chanjoke.utils.Constants.BASE_URL
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import org.hl7.fhir.exceptions.FHIRException
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.ListResource
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.ServiceRequest

class TimestampBasedDownloadWorkManagerImpl(private val dataStore: DemoDataStore) :
    DownloadWorkManager {
    private val resourceTypeList = ResourceType.values().map { it.name }
    private val urls = LinkedList(
        listOf(
            "Patient?_sort=_lastUpdated",
            "Practitioner?_count=1000",
            "ServiceRequest?_count=1000",
            "RelatedPerson",
            "AdverseEvent",
            "Immunization?_count=1000",
            "ImmunizationRecommendation?_count=1000"
        )
    )

    override suspend fun getNextRequest(): Request? {
        var url = urls.poll() ?: return null

        val resourceTypeToDownload =
            ResourceType.fromCode(url.findAnyOf(resourceTypeList, ignoreCase = true)!!.second)
        dataStore.getLastUpdateTimestamp(resourceTypeToDownload)?.let {
            url = affixLastUpdatedTimestamp(url, it)
        }
        return Request.of(url)
    }

    override suspend fun getSummaryRequestUrls(): Map<ResourceType, String> {
        return urls.associate { url ->
            val resourceType = ResourceType.fromCode(url.substringBefore("?"))
            val summaryParam = if (resourceType == ResourceType.Patient) {
                "&${SyncDataParams.SUMMARY_KEY}=${SyncDataParams.SUMMARY_COUNT_VALUE}"
            } else {
                ""
            }

            resourceType to url.plus(summaryParam)
        }
    }


    override suspend fun processResponse(response: Resource): Collection<Resource> {
        // As per FHIR documentation :
        // If the search fails (cannot be executed, not that there are no matches), the
        // return value SHALL be a status code 4xx or 5xx with an OperationOutcome.
        // See https://www.hl7.org/fhir/http.html#search for more details.
        if (response is OperationOutcome) {
            throw FHIRException(response.issueFirstRep.diagnostics)
        }

        // If the resource returned is a List containing Patients, extract Patient references and fetch
        // all resources related to the patient using the $everything operation.
        if (response is ListResource) {
            for (entry in response.entry) {
                val reference = Reference(entry.item.reference)
                if (reference.referenceElement.resourceType.equals("Patient")) {
                    val patientUrl = "${entry.item.reference}/\$everything"
                    urls.add(patientUrl)
                }
            }
        }

        // If the resource returned is a Bundle, check to see if there is a "next" relation referenced
        // in the Bundle.link component, if so, append the URL referenced to list of URLs to download.
        if (response is Bundle) {

//            for (entry in response.entry) {
//                val type = entry.resourceType.toString()
//                if (type == "ServiceRequest") {
//                    urls.add(entry.fullUrl)
//                }
//            }
            val nextUrl =
                response.link.firstOrNull { component -> component.relation == "next" }?.url
            if (nextUrl != null) {
                urls.add(nextUrl)
            }


        }

        // Finally, extract the downloaded resources from the bundle.
        var bundleCollection: Collection<Resource> = mutableListOf()
        if (response is Bundle && response.type == Bundle.BundleType.SEARCHSET) {
            bundleCollection =
                response.entry
                    .map { it.resource }
                    .also { extractAndSaveLastUpdateTimestampToFetchFutureUpdates(it) }
        }
        return bundleCollection
    }


    private suspend fun extractAndSaveLastUpdateTimestampToFetchFutureUpdates(
        resources: List<Resource>,
    ) {
        resources
            .groupBy { it.resourceType }
            .entries
            .map { map ->
                dataStore.saveLastUpdatedTimestamp(
                    map.key,
                    map.value.maxOfOrNull { it.meta.lastUpdated }?.toTimeZoneString() ?: "",
                )
            }

    }
}

/**
 * Affixes the last updated timestamp to the request URL.
 *
 * If the request URL includes the `$everything` parameter, the last updated timestamp will be
 * attached using the `_since` parameter. Otherwise, the last updated timestamp will be attached
 * using the `_lastUpdated` parameter.
 */
private fun affixLastUpdatedTimestamp(url: String, lastUpdated: String): String {
    var downloadUrl = url

    // Affix lastUpdate to a $everything query using _since as per:
    // https://hl7.org/fhir/operation-patient-everything.html
    if (downloadUrl.contains("\$everything")) {
        downloadUrl = "$downloadUrl?_since=$lastUpdated"
    }

    // Affix lastUpdate to non-$everything queries as per:
    // https://hl7.org/fhir/operation-patient-everything.html
    if (!downloadUrl.contains("\$everything") && !downloadUrl.contains("Practitioner")) {
        downloadUrl = "$downloadUrl&_lastUpdated=gt$lastUpdated"
    }
    if (!downloadUrl.contains("\$everything") && downloadUrl.contains("Immunization")) {
        downloadUrl = "$downloadUrl?&_lastUpdated=gt$lastUpdated"
    }
    if (!downloadUrl.contains("\$everything") && downloadUrl.contains("ImmunizationRecommendation")) {
        downloadUrl = "$downloadUrl?&_lastUpdated=gt$lastUpdated"
    }
//    if (!downloadUrl.contains("\$everything") && downloadUrl.contains("ServiceRequest")) {
//        downloadUrl = "$downloadUrl?&_lastUpdated=gt$lastUpdated"
//    }

    // Do not modify any URL set by a server that specifies the token of the page to return.
    if (downloadUrl.contains("&page_token")) {
        downloadUrl = url
    }

    return downloadUrl
}

private fun Date.toTimeZoneString(): String {
    val simpleDateFormat =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    return simpleDateFormat.format(this.toInstant())
}
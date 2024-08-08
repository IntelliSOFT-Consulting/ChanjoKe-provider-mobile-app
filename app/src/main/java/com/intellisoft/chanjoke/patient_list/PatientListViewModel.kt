/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellisoft.chanjoke.patient_list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.get
import com.google.android.fhir.logicalId
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.count
import com.google.android.fhir.search.search
import com.intellisoft.chanjoke.fhir.data.DbServiceRequest
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.Identifiers
import com.intellisoft.chanjoke.fhir.data.ServiceRequestPatient
import com.intellisoft.chanjoke.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.ServiceRequest
import timber.log.Timber
import java.sql.DataTruncation
import java.util.Date

/**
 * The ViewModel helper class for PatientItemRecyclerViewAdapter, that is responsible for preparing
 * data for UI.
 */
class PatientListViewModel(application: Application, private val fhirEngine: FhirEngine) :
    AndroidViewModel(application) {

    val liveSearchedPatients = MutableLiveData<List<PatientItem>>()
    val patientCount = MutableLiveData<Long>()

    private val _liveServiceRequests = MutableLiveData<MutableList<ServiceRequestPatient>>()
    val liveServiceRequests: LiveData<MutableList<ServiceRequestPatient>> get() = _liveServiceRequests


    init {
        updatePatientListAndPatientCount({ getSearchResults() }, { count() })
    }

    fun searchPatientsByName(nameQuery: String) {
        updatePatientListAndPatientCount({ getSearchResults(nameQuery) }, { count(nameQuery) })
    }

    /**
     * [updatePatientListAndPatientCount] calls the search and count lambda and updates the live data
     * values accordingly. It is initially called when this [ViewModel] is created. Later its called
     * by the client every time search query changes or data-sync is completed.
     */
    private fun updatePatientListAndPatientCount(
        search: suspend () -> List<PatientItem>,
        count: suspend () -> Long,
    ) {
        viewModelScope.launch {
            liveSearchedPatients.value = search()
            patientCount.value = count()
        }
    }

    /**
     * Returns count of all the [Patient] who match the filter criteria unlike [getSearchResults]
     * which only returns a fixed range.
     */
    private suspend fun count(nameQuery: String = ""): Long {
        return fhirEngine.count<Patient> {
            if (nameQuery.isNotEmpty()) {
                filter(
                    Patient.NAME,
                    {
                        modifier = StringFilterModifier.CONTAINS
                        value = nameQuery
                    },
                )
            }
        }
    }

    private suspend fun getSearchResults(nameQuery: String = ""): List<PatientItem> {
        var patients: MutableList<PatientItem> = mutableListOf()
        fhirEngine
            .search<Patient> {

                if (nameQuery.isNotEmpty()) {
                    filter(
                        Patient.NAME,
                        {
                            modifier = StringFilterModifier.CONTAINS
                            value = nameQuery
                        },
                    )
                }
                count = 100
                from = 0
            }
            .mapIndexed { index, fhirPatient -> fhirPatient.toPatientItem(index + 1) }
            .let {
                val sortedPatientItems = it.sortedByDescending { q ->
                    q.lastUpdated // Assuming lastUpdated is a property of PatientItem
                }

                patients.addAll(sortedPatientItems)
            }

        /**
         * TODO: Check on the best way to filter by the id or phone number
         */
        if (patients.isEmpty()) {
            patients = searchPatientsByPhoneOrIdentification(nameQuery)
        }

        return patients
    }

    private suspend fun searchPatientsByPhoneOrIdentification(searchString: String): MutableList<PatientItem> {
        val matchingPatients = mutableListOf<PatientItem>()
        val patients: MutableList<PatientItem> = mutableListOf()

        fhirEngine
            .search<Patient> {
                sort(Patient.BIRTHDATE, Order.DESCENDING)
                count = 100
                from = 0
            }
            .mapIndexed { index, fhirPatient -> fhirPatient.toPatientItem(index + 1) }
            .let {
                val sortedPatientItems = it.sortedBy { q ->
                    q.lastUpdated // Assuming lastUpdated is a property of PatientItem
                }

                patients.addAll(sortedPatientItems)
            }


        for (patient in patients) {
            // Check if the phone matches or is close
            if (patient.phone.contains(searchString) || isCloseMatch(patient.phone, searchString)) {
                matchingPatients.add(patient)
            }

            // Check if the identification matches or is close
            if (patient.identification.contains(searchString) || isCloseMatch(
                    patient.identification,
                    searchString
                )
            ) {
                matchingPatients.add(patient)
            }
        }
        val newList = matchingPatients.distinctBy { it.id }

        return newList.toMutableList()
    }

    fun isCloseMatch(original: String, search: String): Boolean {
        return original.toLowerCase().contains(search.toLowerCase())
    }

    fun retrieveLocations() = runBlocking {
        retrieveAllLocations()
    }

    private suspend fun retrieveAllLocations(): List<LocationItem> {
        val locations: MutableList<LocationItem> = mutableListOf()
        fhirEngine.search<Location> {
            sort(Location.NAME, Order.DESCENDING)
            count = 100
            from = 0
        }
            .mapIndexed { index, fhirPatient -> fhirPatient.toLocationItem(index + 1) }
            .let { locations.addAll(it) }

        return withContext(Dispatchers.IO) { locations }
    }

    private suspend fun retrieveActiveServiceRequest(): MutableList<ServiceRequestPatient> {
        val serviceRequests = ArrayList<ServiceRequestPatient>()

        fhirEngine.search<ServiceRequest> {
            sort(ServiceRequest.OCCURRENCE, Order.DESCENDING)
        }.map { createServiceRequestItem(it) }.let { q ->
            q.forEach { serviceRequest ->
                if (serviceRequest.status == "ACTIVE") {
                    serviceRequests.add(serviceRequest)
                }
            }
        }
        return withContext(Dispatchers.IO) { serviceRequests }
    }

    private suspend fun createServiceRequestItem(data: ServiceRequest): ServiceRequestPatient {

        val logicalId = if (data.hasId()) data.logicalId else ""
        val status = if (data.hasStatus()) data.status.toString() else ""
        val patientIdRef =
            if (data.hasSubject()) if (data.subject.hasReference()) data.subject.reference else "" else ""

        val patientId = patientIdRef.toString().replace("Patient/", "")
        var patientPhone = ""
        var systemId = ""
        var type = ""
        var gender = ""
        var name = ""
        var dob = LocalDate.now()
        val patient = getPatientDetails(patientId)
        if (patient != null) {

            name = patient.nameFirstRep.nameAsSingleString
            if (patient.hasIdentifier()) {
                patient.identifier.forEach {
                    if (it.hasType()) {
                        if (it.type.hasCoding()) {
                            if (it.type.codingFirstRep.code == "identification_type") {
                                systemId = it.value
                                type = it.type.codingFirstRep.display
                            }
                        }
                    }
                }
            }

            if (patient.hasTelecom()) {
                if (patient.telecom.isNotEmpty()) {
                    if (patient.telecom.first().hasValue()) {
                        patientPhone = patient.telecom.first().value
                    }
                }
            }
            gender = if (patient.hasGenderElement()) patient.genderElement.valueAsString else ""
            dob =
                if (patient.hasBirthDateElement()) {
                    val birthElement = patient.birthDateElement.valueAsString
                    val dobFormat = FormatterClass().convertDateFormat(birthElement)
                    if (dobFormat != null) {
                        val dobDate = FormatterClass().convertStringToDate(dobFormat, "MMM d yyyy")
                        if (dobDate != null) {
                            FormatterClass().convertDateToLocalDate(dobDate)
                        } else null
                    } else null

                } else null

        }

        val authoredOn = if (data.hasAuthoredOn()) data.authoredOn.toString() else ""
        return ServiceRequestPatient(
            logicalId = logicalId,
            status = status,
            patientId = patientId,
            patientName = name,
            patientNational = systemId,
            patientPhone = patientPhone,
            dob = dob.toString(),
            gender = gender,
            authoredOn = authoredOn
        )
    }

    private suspend fun getPatientDetails(patientId: String): Patient? {
        return try {
            fhirEngine.get(ResourceType.Patient, patientId) as Patient
        } catch (e: Exception) {
            // Log the exception if needed
            null
        }
    }


    fun loadActiveServiceRequest() {
        viewModelScope.launch {
            _liveServiceRequests.postValue(retrieveActiveServiceRequest())
        }
    }


    /** The Patient's details for display purposes. */
    data class PatientItem(
        val id: String,
        val resourceId: String,
        val name: String,
        val gender: String,
        val dob: LocalDate? = null,
        val identification: String,
        val phone: String,
        val city: String,
        val country: String,
        val isActive: Boolean,
        val html: String,
        var risk: String? = "",
        var lastUpdated: String,
        val contact_name: String?,
        val contact_phone: String?,
        val contact_gender: String?,
        val document: String,
        val number: String
    ) {
        override fun toString(): String = name
    }

    data class LocationItem(
        val id: String,
        val resourceId: String,
        val name: String,
    ) {
        override fun toString(): String = name
    }

    /** The Observation's details for display purposes. */


    /** The Observation's details for display purposes. */
    data class ObservationItem(
        val id: String,
        val code: String,
        val effective: String,
        val value: String,
        val dateTime: String? = null,
    ) {
        override fun toString(): String = code
    }

    data class ConditionItem(
        val id: String,
        val code: String,
        val effective: String,
        val value: String,
    ) {
        override fun toString(): String = code
    }

    class PatientListViewModelFactory(
        private val application: Application,
        private val fhirEngine: FhirEngine,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PatientListViewModel::class.java)) {
                return PatientListViewModel(application, fhirEngine) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

internal fun Patient.toPatientItem(position: Int): PatientListViewModel.PatientItem {
    // Show nothing if no values available for gender and date of birth.
    val formatterClass = FormatterClass()

    val patientId = if (hasIdElement()) idElement.idPart else ""
    val name = if (hasName()) {
        "${name[0].givenAsSingleString} ${name[0].family}"
    } else ""
    val gender = if (hasGenderElement()) genderElement.valueAsString else ""
    val dob =
        if (hasBirthDateElement()) {
            val birthElement = birthDateElement.valueAsString
            val dobFormat = formatterClass.convertDateFormat(birthElement)
            if (dobFormat != null) {
                val dobDate = formatterClass.convertStringToDate(dobFormat, "MMM d yyyy")
                if (dobDate != null) {
                    formatterClass.convertDateToLocalDate(dobDate)
                } else null
            } else null

        } else null

    val phone = if (hasTelecom()) telecom[0].value else ""
    val city = if (hasAddress()) address[0].city else ""
    val country = if (hasAddress()) address[0].country else ""
    val isActive = active
    val html: String = if (hasText()) text.div.valueAsString else ""

    val identification: String = if (hasIdentifier()) {
        if (identifier.isNotEmpty()) {
            if (identifier[0].hasValue()) {
                identifier[0].value
            } else "N/A"
        } else "N/A"
    } else "N/A"

    var lastUpdated = ""
    if (hasIdentifier()) {
        val id = identifier.find { it.system == "system-creation" }
        if (id != null) {
            lastUpdated = id.value
        }
    } else {
        lastUpdated = ""
    }


    var contact_name = ""
    var contact_phone = ""
    var contact_gender = ""
    var contact_type = ""
    if (hasContact()) {
        if (contactFirstRep.hasName()) contact_name =
            if (hasContact()) {
                if (contactFirstRep.hasName()) {
                    contactFirstRep.name.nameAsSingleString
                } else ""
            } else ""
        if (contactFirstRep.hasTelecom()) contact_phone =
            if (hasContact()) {
                if (contactFirstRep.hasTelecom()) {
                    if (contactFirstRep.telecomFirstRep.hasValue()) {
                        contactFirstRep.telecomFirstRep.value
                    } else ""
                } else ""
            } else ""
        if (contactFirstRep.hasGenderElement()) contact_gender =
            if (hasContact()) AppUtils().capitalizeFirstLetter(contactFirstRep.genderElement.valueAsString) else ""
        if (contactFirstRep.hasRelationship()) {
            if (contactFirstRep.relationshipFirstRep.hasCoding()) {
                if (contactFirstRep.relationshipFirstRep.hasText())
                    contact_type = contactFirstRep.relationshipFirstRep.text
            }
        }
    }

    // identification details

    var document = ""
    var number = ""

    if (hasIdentifier()) {
        identifier.forEach { identifier ->

            try {
                if (identifier.hasType()) {
                    if (identifier.type.hasCoding()) {
                        if (identifier.type.codingFirstRep.code == "identification_type") {
                            document = identifier.type.codingFirstRep.display
                            number = identifier.value
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val codeableConceptType = identifier.type
            if (codeableConceptType.hasText() && codeableConceptType.text.contains(
                    Identifiers.SYSTEM_GENERATED.name
                )
            ) {


            }
        }
    }

    return PatientListViewModel.PatientItem(
        id = position.toString(),
        resourceId = patientId,
        name = name,
        gender = gender ?: "",
        dob = dob,
        identification = identification,
        phone = phone ?: "",
        city = city ?: "",
        country = country ?: "",
        isActive = isActive,
        html = html,
        lastUpdated = lastUpdated,
        contact_name = contact_name,
        contact_phone = contact_phone,
        contact_gender = contact_type,
        document = document,
        number = number
    )
}

internal fun Location.toLocationItem(position: Int): PatientListViewModel.LocationItem {
    // Show nothing if no values available for gender and date of birth.
    val patientId = if (hasIdElement()) idElement.idPart else ""
    val name = if (hasName()) name.toString() else ""

    return PatientListViewModel.LocationItem(
        id = position.toString(),
        resourceId = patientId,
        name = name,
    )
}

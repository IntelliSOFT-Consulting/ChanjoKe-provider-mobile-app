package com.intellisoft.chanjoke.viewmodel


import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.icu.text.DateFormat
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.fhir.data.DbVaccineData
import com.intellisoft.chanjoke.utils.AppUtils
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.logicalId
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.count
import com.google.android.fhir.search.search
import com.google.gson.Gson
import com.intellisoft.chanjoke.fhir.data.AdverseEventData
import com.intellisoft.chanjoke.fhir.data.AdverseEventItem
import com.intellisoft.chanjoke.fhir.data.CareGiver
import com.intellisoft.chanjoke.fhir.data.Contraindication
import com.intellisoft.chanjoke.fhir.data.DbAppointmentData
import com.intellisoft.chanjoke.fhir.data.DbAppointmentDetails
import com.intellisoft.chanjoke.fhir.data.DbCarePlan
import com.intellisoft.chanjoke.fhir.data.DbCountyDetails
import com.intellisoft.chanjoke.fhir.data.DbPeriod
import com.intellisoft.chanjoke.fhir.data.DbRecommendationDetails
import com.intellisoft.chanjoke.fhir.data.DbServiceRequest
import com.intellisoft.chanjoke.fhir.data.DbTempData
import com.intellisoft.chanjoke.fhir.data.DbVaccineDetailsData
import com.intellisoft.chanjoke.fhir.data.DbVaccineNotDone
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.Identifiers
import com.intellisoft.chanjoke.fhir.data.ObservationDateValue
import com.intellisoft.chanjoke.fhir.data.PractitionerDetails
import com.intellisoft.chanjoke.patient_list.PatientListViewModel
import com.intellisoft.chanjoke.utils.Constants.AEFI_DATE
import com.intellisoft.chanjoke.utils.Constants.AEFI_TYPE
import com.intellisoft.chanjoke.vaccine.validations.BasicVaccine
import com.intellisoft.chanjoke.vaccine.validations.ImmunizationHandler
import com.intellisoft.chanjoke.vaccine.validations.NonRoutineVaccine
import com.intellisoft.chanjoke.vaccine.validations.RoutineVaccine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.hl7.fhir.r4.model.AdverseEvent
import org.hl7.fhir.r4.model.AllergyIntolerance
import org.hl7.fhir.r4.model.Appointment
import org.hl7.fhir.r4.model.CarePlan
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Immunization
import org.hl7.fhir.r4.model.ImmunizationRecommendation
import org.hl7.fhir.r4.model.Location
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Practitioner
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.RiskAssessment
import org.hl7.fhir.r4.model.ServiceRequest
import org.hl7.fhir.r4.model.ServiceRequest.ServiceRequestStatus
import org.hl7.fhir.r4.utils.NPMPackageGenerator.Category
import timber.log.Timber

/**
 * The ViewModel helper class for PatientItemRecyclerViewAdapter, that is responsible for preparing
 * data for UI.
 */
class PatientDetailsViewModel(
    application: Application,
    private val fhirEngine: FhirEngine,
    private val patientId: String,
) : AndroidViewModel(application) {
    val livePatientData = MutableLiveData<PatientData>()

    /** Emits list of [PatientDetailData]. */
    fun getPatientDetailData() {
        viewModelScope.launch { livePatientData.value = getPatientDetailDataModel() }
    }


    fun getPatientInfo() = runBlocking {
        getPatientDetailDataModel()
    }

    private suspend fun getPatientDetailDataModel(): PatientData {
        val searchResult =
            fhirEngine.search<Patient> {
                filter(Resource.RES_ID, { value = of(patientId) })
            }
        val formatterClass = FormatterClass()
        var name = ""
        var phone = ""
        var dob = ""
        var gender = ""
        var contact_name = ""
        var contact_phone = ""
        var contact_gender = ""
        var contact_type = ""
        var type = ""
        var systemId = ""
        var county = ""
        var subCounty = ""
        var ward = ""
        var trading = ""
        var estate = ""
        var logicalId = ""
        var firstName = ""
        var middleName = ""
        var lastName = ""
        val kins = mutableListOf<CareGiver>()
        searchResult.first().let {
            logicalId = it.logicalId
            name = if (it.hasName()) {
                // display name in order as fname, then others
                "${it.name[0].givenAsSingleString} ${it.name[0].family} "
            } else ""
            lastName = if (it.hasName()) it.nameFirstRep.family else ""

            val givenNames = if (it.hasName() && it.nameFirstRep.hasGiven()) {
                it.nameFirstRep.given.map { givenName -> givenName.valueAsString }
            } else {
                emptyList()
            }
            firstName = givenNames.getOrElse(0) { "" }
            middleName = givenNames.getOrElse(1) { "" }
            phone = ""
            if (it.hasTelecom()) {
                if (it.telecom.isNotEmpty()) {
                    if (it.telecom.first().hasValue()) {
                        phone = it.telecom.first().value
                    }
                }
            }

            if (it.hasBirthDateElement()) {
                if (it.birthDateElement.hasValue()) {
                    val birthDateElement =
                        formatterClass.convertChildDateFormat(it.birthDateElement.valueAsString)
                    if (birthDateElement != null) {
                        dob = birthDateElement
                    }
                }
            }

            if (it.hasContact()) {
                it.contact.forEach {
                    val name = it.name.nameAsSingleString
                    val phone = it.telecomFirstRep.value
                    val type = it.relationshipFirstRep.text
                    kins.add(CareGiver(phone = phone, name = name, type = type, nationalID = ""))
                }

                if (it.contactFirstRep.hasName()) contact_name =
                    if (it.hasContact()) {
                        if (it.contactFirstRep.hasName()) {
                            it.contactFirstRep.name.nameAsSingleString
                        } else ""
                    } else ""
                if (it.contactFirstRep.hasTelecom()) contact_phone =
                    if (it.hasContact()) {
                        if (it.contactFirstRep.hasTelecom()) {
                            if (it.contactFirstRep.telecomFirstRep.hasValue()) {
                                it.contactFirstRep.telecomFirstRep.value
                            } else ""
                        } else ""
                    } else ""
                if (it.contactFirstRep.hasGenderElement()) contact_gender =
                    if (it.hasContact()) AppUtils().capitalizeFirstLetter(it.contactFirstRep.genderElement.valueAsString) else ""
                if (it.contactFirstRep.hasRelationship()) {
                    if (it.contactFirstRep.relationshipFirstRep.hasCoding()) {
                        contact_type = it.contactFirstRep.relationshipFirstRep.text
                    }
                }
            }

            if (it.hasGenderElement()) gender = it.genderElement.valueAsString

            if (it.hasIdentifier()) {
                it.identifier.forEach { identifier ->

                    try {
                        if (identifier.hasType()) {
                            if (identifier.type.hasCoding()) {
                                if (identifier.type.codingFirstRep.code == "identification_type") {
                                    systemId = identifier.value
                                    type = identifier.type.codingFirstRep.display
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

            if (it.hasAddress()) {
                if (it.addressFirstRep.hasCity()) county = it.addressFirstRep.city
                if (it.addressFirstRep.hasDistrict()) subCounty =
                    it.addressFirstRep.district
                if (it.addressFirstRep.hasState()) ward = it.addressFirstRep.state
                if (it.addressFirstRep.hasLine()) {
                    if (it.addressFirstRep.line.size >= 2) {
                        trading = it.addressFirstRep.line[0].value
                        estate = it.addressFirstRep.line[1].value
                    }
                }
            }


        }

        FormatterClass().saveSharedPref(
            "patientDob",
            dob,
            getApplication<Application>().applicationContext
        )
        FormatterClass().saveSharedPref(
            "patientId",
            patientId,
            getApplication<Application>().applicationContext
        )


        return PatientData(
            logicalId = logicalId,
            name = name,
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            phone,
            dob,
            gender,
            contact_name = contact_name,
            contact_phone = contact_phone,
            contact_gender = contact_type,
            systemId = systemId,
            county = county,
            type = type,
            subCounty = subCounty,
            ward = ward,
            trading = trading,
            estate = estate,
            kins = kins
        )
    }

    data class PatientData(
        val logicalId: String,
        val name: String,
        val firstName: String,
        val middleName: String,
        val lastName: String,
        val phone: String,
        val dob: String,
        val gender: String,
        val contact_name: String?,
        val contact_phone: String?,
        val contact_gender: String?,
        val systemId: String?,
        val type: String?,
        val county: String?,
        val subCounty: String?,
        val ward: String?,
        val trading: String?,
        val estate: String?,
        val kins: List<CareGiver>?

    ) {
        override fun toString(): String = name
    }


    private val LocalDate.localizedString: String
        get() {
            val date = Date.from(atStartOfDay(ZoneId.systemDefault())?.toInstant())
            return if (isAndroidIcuSupported()) {
                DateFormat.getDateInstance(DateFormat.DEFAULT).format(date)
            } else {
                SimpleDateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
                    .format(date)
            }
        }

    // Android ICU is supported API level 24 onwards.
    private fun isAndroidIcuSupported() = true

    private fun getString(resId: Int) = getApplication<Application>().resources.getString(resId)

    private fun getLastContactedDate(riskAssessment: RiskAssessment?): String {
        riskAssessment?.let {
            if (it.hasOccurrence()) {
                return LocalDate.parse(
                    it.occurrenceDateTimeType.valueAsString,
                    DateTimeFormatter.ISO_DATE_TIME,
                )
                    .localizedString
            }
        }
        return getString(R.string.none)
    }


    fun recommendationList(status: String?) = runBlocking {
        getRecommendationList(status)
    }


    private suspend fun getRecommendationList(statusValue: String?): ArrayList<DbRecommendationDetails> {

        val dbRecommendationDetailsList = ArrayList<DbRecommendationDetails>()
        val immunizationRecommendationList = ArrayList<ImmunizationRecommendation>()
        fhirEngine
            .search<ImmunizationRecommendation> {
                filter(ImmunizationRecommendation.PATIENT, { value = "Patient/$patientId" })
                sort(Encounter.DATE, Order.DESCENDING)
            }
            .map { getRecommendationData(it) }
            .let { immunizationRecommendationList.addAll(it) }

        immunizationRecommendationList.forEach { immunizationRecommendation ->

            val recommendationList = immunizationRecommendation.recommendation
            recommendationList.forEach { recommendation ->

                var vaccineCode = ""
                var vaccineName = ""
                var targetDisease = ""
                var earliestDate = ""
                var latestDate = ""
                var description = ""
                var series = ""
                var doseNumber = ""
                var status = ""
                var nhdd = ""

                if (recommendation.hasVaccineCode()) {
                    if (recommendation.vaccineCode[0].hasCoding()) {
                        nhdd = recommendation.vaccineCode[0].codingFirstRep.code
                        vaccineCode = recommendation.vaccineCode[0].codingFirstRep.display
                    }
                    if (recommendation.vaccineCode[0].hasText()) {
                        vaccineName = recommendation.vaccineCode[0].text
                    }
                }
                if (recommendation.hasTargetDisease()) {
                    if (recommendation.targetDisease.hasText()) {
                        targetDisease = recommendation.targetDisease.text
                    }
                }
                if (recommendation.hasDateCriterion()) {
                    val dateCriterionList = recommendation.dateCriterion
                    dateCriterionList.forEach { dateCriterion ->

                        if (dateCriterion.hasCode()) {
                            if (dateCriterion.code.codingFirstRep.display == "Earliest-date-to-administer") {
                                earliestDate = dateCriterion.value.toString()
                            }
                            if (dateCriterion.code.codingFirstRep.display == "Latest-date-to-administer") {
                                latestDate = dateCriterion.value.toString()
                            }
                        }
                    }
                }
                if (recommendation.hasDescription()) {

                    if (recommendation.hasDescription()) {
                        description = recommendation.description
                    }
                    if (recommendation.hasSeries()) {
                        series = recommendation.series
                    }
                    if (recommendation.hasDoseNumberPositiveIntType()) {
                        doseNumber = recommendation.doseNumberPositiveIntType.value.toString()
                    }
                    if (recommendation.hasForecastStatus()) {
                        if (recommendation.forecastStatus.hasCoding()) {
                            if (recommendation.forecastStatus.codingFirstRep.hasDisplay()) {
                                status = recommendation.forecastStatus.codingFirstRep.display
                            }
                        }
                    }


                    val dbRecommendationDetails = DbRecommendationDetails(
                        vaccineCode = vaccineCode,
                        vaccineName = vaccineName,
                        targetDisease = targetDisease,
                        earliestDate = earliestDate,
                        latestDate = latestDate,
                        description = description,
                        series = series,
                        doseNumber = doseNumber,
                        status = status,
                        nhdd = nhdd,
                    )
                    dbRecommendationDetailsList.add(dbRecommendationDetails)
                }
            }
        }

        return dbRecommendationDetailsList

    }

    private fun getRecommendationData(it: ImmunizationRecommendation): ImmunizationRecommendation {
        return it
    }


    private fun createRecommendation(it: ImmunizationRecommendation): DbAppointmentDetails {

        var immunizationHandler = ImmunizationHandler()
        var appointmentId = ""

        if (it.hasId()) appointmentId = it.id.replace("ImmunizationRecommendation/", "")

        var date = ""

        if (it.hasRecommendation() && it.recommendation.isNotEmpty()) {
            if (it.recommendation[0].hasDateCriterion() &&
                it.recommendation[0].dateCriterion.isNotEmpty() &&
                it.recommendation[0].dateCriterion[0].hasValue()
            ) {
                val dateCriterion = it.recommendation[0].dateCriterion[0].value.toString()
                val dobFormat = FormatterClass().convertDateFormat(dateCriterion)
                if (dobFormat != null) {
                    date = dobFormat.toString()
                }
            }
        }
        var targetDisease = ""
        var doseNumber: String? = ""
        var appointmentStatus = ""
        var vaccineCode = ""
        var vaccineName = ""


        if (it.hasRecommendation()) {
            val recommendation = it.recommendation
            if (recommendation.isNotEmpty()) {
                //targetDisease
                val codeableConceptTargetDisease = recommendation[0].targetDisease
                if (codeableConceptTargetDisease.hasText()) {
                    targetDisease = codeableConceptTargetDisease.text
                }

                //appointment status
                val codeableConceptTargetStatus = recommendation[0].forecastStatus
                if (codeableConceptTargetStatus.hasText()) {
                    appointmentStatus = codeableConceptTargetStatus.text
                }

                //Dose number
                if (recommendation[0].hasDoseNumber()) {
                    doseNumber = recommendation[0].doseNumber.toString()
                }

                //Contraindicated vaccine code
                if (recommendation[0].hasContraindicatedVaccineCode()) {
                    vaccineCode = recommendation[0].contraindicatedVaccineCode[0].text

                    val vaccineDetails =
                        immunizationHandler.getRoutineVaccineDetailsBySeriesTargetName(targetDisease)

                    if (vaccineDetails != null) {

                        val seriesDoses = when (vaccineDetails) {
                            is RoutineVaccine -> {
                                vaccineDetails.vaccineList.filter { it.vaccineCode == vaccineCode }
                                    .firstOrNull()
                            }

                            is NonRoutineVaccine -> {
                                val nonRoutineVaccine =
                                    vaccineDetails.vaccineList.firstOrNull()
                                    { it.targetDisease == targetDisease }
                                nonRoutineVaccine?.vaccineList?.filter { it.vaccineCode == vaccineCode }
                                    ?.firstOrNull()
                            }

                            else -> {
                                null
                            }

                        }

                        if (seriesDoses != null) {
                            vaccineName = seriesDoses.vaccineName
                        }

                    }
                }

            }
        }





        return DbAppointmentDetails(
            appointmentId,
            date,
            doseNumber,
            targetDisease,
            vaccineName,
            appointmentStatus
        )


    }

    fun getAppointmentById(appointmentId: String) = runBlocking {
        getAppointmentByIdBac(appointmentId)
    }

    private suspend fun getAppointmentByIdBac(appointmentId: String): ArrayList<DbAppointmentData> {

        val id = appointmentId.replace("Appointment/", "")

        val searchResult = fhirEngine.search<Appointment> {
            filter(Appointment.RES_ID, { value = of(id) })
        }.map { createAppointment(it) }

        return ArrayList(searchResult)
    }


    fun getAppointmentList() = runBlocking {
        getAppointmentDetails()
    }

    private suspend fun getAppointmentDetails(): ArrayList<DbAppointmentData> {

        val appointmentList = ArrayList<DbAppointmentData>()

        fhirEngine
            .search<Appointment> {
                filter(Appointment.SUPPORTING_INFO, { value = "Patient/$patientId" })
                sort(Appointment.DATE, Order.DESCENDING)
            }
            .map { createAppointment(it) }
            .let { appointmentList.addAll(it) }

        return appointmentList
    }

    private suspend fun createAppointment(it: Appointment): DbAppointmentData {

        val recommendationList = getRecommendationList(null)

        val id = if (it.hasId()) it.id else ""
        val status = if (it.hasStatus()) it.status else ""
        val description = if (it.hasDescription()) it.description else ""
        val start = if (it.hasStart()) it.start else ""
        var dateScheduled = ""

        val startDate = FormatterClass().convertDateFormat(start.toString())
        if (startDate != null) {
            dateScheduled = startDate
        }

        var recommendationSavedList = ArrayList<DbAppointmentDetails>()
        val dbAppointmentDetails = DbAppointmentDetails(
            id,
            dateScheduled,
            "",
            "",
            description,
            status.toString()
        )
        recommendationSavedList.add(dbAppointmentDetails)



        return DbAppointmentData(
            id,
            description,
            "",
            null,
            dateScheduled,
            recommendationSavedList,
            status.toString()
        )


    }

    fun getVaccineListWithAefis() = runBlocking {
        getVaccineListDetails()
    }

    fun getUserDetails(patientId: String, patientName: String, context: Context) = runBlocking {
        getUserDetailsValue(patientId, patientName, context)
    }

    private suspend fun getUserDetailsValue(
        patientId: String,
        patientName: String,
        context: Context
    ): DbTempData? {

        val formatterClass = FormatterClass()

        var dob = ""
        var gender = ""

        val it = fhirEngine.search<Patient> {
            filter(Patient.RES_ID, { value = of(patientId) })
        }.firstOrNull()
        if (it != null) {

            if (it.hasBirthDateElement()) {
                if (it.birthDateElement.hasValue()) {
                    val birthDateElement =
                        formatterClass.convertChildDateFormat(it.birthDateElement.valueAsString)
                    if (birthDateElement != null) {
                        dob = birthDateElement
                    }
                }
            }

            if (it.hasGenderElement()) gender = it.genderElement.valueAsString

            val temp = DbTempData(
                name = patientName,
                dob = dob,
                gender = gender,
                age = "",
            )
            return temp
        }
        return null

    }

    fun loadServiceRequests(serviceRequestId: String) = runBlocking {
        getServiceRequests(serviceRequestId)
    }

    fun getCampaignList() = runBlocking {
        getCampaignListBack()
    }
    private suspend fun getCampaignListBack():ArrayList<DbCarePlan>{

        val dbCarePlanList = ArrayList<DbCarePlan>()

        fhirEngine
            .search<CarePlan> {
                sort(CarePlan.DATE, Order.DESCENDING)
            }
            .map { createCarePlan(it) }
            .let { dbCarePlanList.addAll(it) }

        return dbCarePlanList
    }

    private fun createCarePlan(it: CarePlan):DbCarePlan {

        val formatterClass = FormatterClass()

        val id = if (it.hasId()) it.id else ""
        val status = if (it.hasStatus()) it.status.display else ""
        val intent = if (it.hasIntent()) it.intent.display else ""
        val title = if (it.hasTitle()) it.title else ""
        val description = if (it.hasDescription()) it.description else ""
        val createdOn = if(it.hasCreated()) formatterClass.convertMillisToDateTime(it.created.time.toString()) else ""
        val startPeriod = if(it.hasPeriod()){
            if (it.period.hasStart()) {
                formatterClass.convertMillisToDateTime(it.period.start.time.toString())
            } else ""
        }else ""
        val endPeriod = if(it.hasPeriod()){
            if (it.period.hasEnd()) {
                formatterClass.convertMillisToDateTime(it.period.end.time.toString())
            } else ""
        }else ""

        var county = ""
        var subCounty = ""
        var ward = ""
        var facility = ""

        val categoryDetails = if (it.hasCategory()) it.categoryFirstRep else null
        if (categoryDetails != null && categoryDetails.hasCoding()){
            val countyDetails = categoryDetails.codingFirstRep
            if (countyDetails.hasCode() && countyDetails.hasDisplay()){
                val code = countyDetails.code
                val display = countyDetails.display

                if (code == "county"){
                    county = display
                }
                if (code == "subCounty"){
                    subCounty = display
                }
                if (code == "ward"){
                    ward = display
                }
                if (code == "facility"){
                    facility = display
                }
            }
        }

        return DbCarePlan(
            id, status, intent, title, description, createdOn,
            DbPeriod(startPeriod, endPeriod),
            DbCountyDetails(county, subCounty, ward, facility)
        )


    }


    fun getVaccineList() = runBlocking {
        getVaccineListDetailsOld()
    }

    private suspend fun getVaccineListDetailsOldOne(): ArrayList<DbVaccineData> {

        val vaccineList = ArrayList<DbVaccineData>()

        fhirEngine
            .search<AllergyIntolerance> {
                filter(AllergyIntolerance.PATIENT, { value = "Patient/$patientId" })
                sort(AllergyIntolerance.DATE, Order.DESCENDING)
            }
            .map { createAllergyIntoleranceItem(it) }
            .let { vaccineList.addAll(it) }


        return ArrayList(vaccineList)
    }

    private suspend fun getServiceRequests(serviceRequestId: String): ArrayList<DbServiceRequest> {

//        val vaccineList = ArrayList<DbServiceRequest>()

        val searchResult = fhirEngine.search<ServiceRequest> {
            filter(ServiceRequest.RES_ID, { value = of(serviceRequestId) })
        }.map { createServiceRequestItem(it) }

//        fhirEngine.search<ServiceRequest> {
//            sort(ServiceRequest.OCCURRENCE, Order.DESCENDING)
//        }.map { createServiceRequestItem(it) }.let { serviceRequests ->
//            serviceRequests.forEach { serviceRequest ->
//                if (serviceRequest.patientReference == "Patient/$patientId" && serviceRequest.status == "ACTIVE") {
//                    vaccineList.add(serviceRequest)
//                }
//            }
//        }


        return ArrayList(searchResult)
    }

    private suspend fun getVaccineListDetails(): ArrayList<DbVaccineData> {

        val vaccineList = ArrayList<DbVaccineData>()

        fhirEngine
            .search<AdverseEvent> {
                filter(AdverseEvent.SUBJECT, { value = "Patient/$patientId" })
                sort(AdverseEvent.DATE, Order.DESCENDING)
            }
            .map { createAdverseEventItem(it) }
            .let { vaccineList.addAll(it) }


        return ArrayList(vaccineList)
    }

    fun getAdverseEvent(patientId: String, encounterId: String) = runBlocking {
        getAdverseEventDetails(patientId, encounterId)
    }

    private suspend fun getAdverseEventDetails(
        patientId: String,
        encounterId: String
    ): AdverseEventItem? {
        return withContext(Dispatchers.IO) {
            // Search for adverse events related to the specified patient
            val adverseEvents = fhirEngine
                .search<AdverseEvent> {
                    filter(AdverseEvent.SUBJECT, { value = "Patient/$patientId" })
                    sort(AdverseEvent.DATE, Order.DESCENDING)
                }
                .map { createAdverseEventItemDetails(it) }

            // Find the adverse event that matches the encounterId
            adverseEvents.find { it.encounterId == encounterId }
        }
    }

    private fun createAdverseEventItemDetails(data: AdverseEvent): AdverseEventItem {
        val logicalId = if (data.hasEncounter()) data.encounter.reference else ""
        val encounterId = logicalId.toString().replace("Encounter/", "")
        val practId = if (data.hasRecorder()) data.recorder.reference else ""
        var practitionerId = practId.toString().replace("Practitioner/", "")
        val locId = if (data.hasLocation()) data.location.reference else ""
        val locDisplay =
            if (data.hasLocation()) if (data.location.hasDisplay()) data.location.display else "" else ""
        var locationId = locId.toString().replace("Location/", "")
        var name = ""
        var role = ""


        if (practitionerId.isNotEmpty()) {

            try {
                val myPair = getPractitionerName(practitionerId)
                name = myPair.first
                role = myPair.second
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


        // Create and return an AdverseEventItem instance
        return AdverseEventItem(
            encounterId,
            PractitionerDetails(name = name, role = role),
            locationId,
            locDisplay
        )
    }

    fun getLocationName(locationId: String) = runBlocking {
        getLocationNameInner(locationId)
    }

    private suspend fun getLocationNameInner(resId: String): String {
        try {

            val searchResult = fhirEngine.search<Location> {
                filter(Location.RES_ID, { value = of(resId) })
            }
            if (searchResult.isNotEmpty() && searchResult.first().hasName()) {
                return searchResult.first().name
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun getPractitionerName(locationId: String) = runBlocking {
        getPractitionerNameInner(locationId)
    }

    private suspend fun getPractitionerNameInner(resId: String): Pair<String, String> {
        var name = ""
        var role = ""

        try {
            val searchResult = fhirEngine.search<Practitioner> {
                filter(Practitioner.RES_ID, { value = of(resId) })
            }

            name = searchResult.first().name[0].nameAsSingleString
            if (searchResult.first().hasExtension()) {
                searchResult.first().extension.forEach {
                    if (it.hasUrl()) {
                        if (it.url.contains("http://example.org/fhir/StructureDefinition/role-group")) {
                            if (it.hasValue()) {
                                role = it.value.toString()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(name, role)

    }


    fun getImmunizationDataDetails(codeValue: String) =
        runBlocking { getImmunizationDetails(codeValue) }

    fun getAllImmunizationDetails() =
        runBlocking { getAllImmunizationDetailsData() }

    fun loadPractioner() {

    }

    fun loadContraindications(vaccineName: String, vaccineDetailsType: String) =
        runBlocking { loadContraindicationsInner(vaccineName, vaccineDetailsType) }

    private suspend fun getImmunizationDetails(codeValue: String): ArrayList<DbVaccineDetailsData> {
        val vaccineList = ArrayList<DbVaccineDetailsData>()

        fhirEngine
            .search<Immunization> {
                filter(Immunization.PATIENT, { value = "Patient/$patientId" })
                filter(
                    Immunization.VACCINE_CODE,
                    {
                        value = of(Coding().apply { code = codeValue })
                    })
                sort(Immunization.DATE, Order.DESCENDING)
            }
            .map { createVaccineItemDetails(it) }
            .let { vaccineList.addAll(it) }

        return vaccineList
    }

    private suspend fun getAllImmunizationDetailsData(): ArrayList<DbVaccineDetailsData> {
        val vaccineList = ArrayList<DbVaccineDetailsData>()

        fhirEngine
            .search<Immunization> {
                filter(Immunization.PATIENT, { value = "Patient/$patientId" })
                sort(Immunization.DATE, Order.DESCENDING)
            }
            .map { createVaccineItemDetails(it) }
            .let { q ->
                q.forEach {
                    if (it.status == "COMPLETED") {
                        vaccineList.add(it)
                    }
                }
            }


        return vaccineList
    }

    private suspend fun loadContraindicationsInner(
        vaccineNameValue: String,
        vaccineDetailsType: String
    ): ArrayList<Contraindication> {

        val contraindicationList = ArrayList<Contraindication>()

        val vaccineList = ArrayList<DbVaccineNotDone>()

        fhirEngine
            .search<Immunization> {
                filter(Immunization.PATIENT, { value = "Patient/$patientId" })
                sort(Immunization.DATE, Order.DESCENDING)
            }
            .map { createVaccineDetails(it) }
            .let { vaccineList.addAll(it) }

        vaccineList.forEach {

            val id = it.logicalId
            val vaccineName = it.vaccineName
            val vaccineCode = it.vaccineCode
            val nextDate = it.nextDate
            val statusReason = it.statusReason
            val status = it.status

            if (vaccineNameValue == vaccineName) {
                val contraindication = Contraindication(
                    id,
                    vaccineCode,
                    vaccineName,
                    nextDate,
                    statusReason,
                    status
                )
                contraindicationList.add(contraindication)
            }
        }

//        val newList = contraindicationList.filter { it.status == vaccineDetailsType }

        return ArrayList(contraindicationList)
    }

    private fun createVaccineDetails(immunization: Immunization): DbVaccineNotDone {

        var logicalId = ""
        var vaccineName = ""
        var vaccineCode = ""
        var nextDate = ""
        var statusReason = ""
        var status = ""

        if (immunization.hasId()) {
            logicalId = immunization.id
        }
        if (immunization.hasVaccineCode()) {
            if (immunization.vaccineCode.hasText()) {
                vaccineName = immunization.vaccineCode.text
            }
            if (immunization.vaccineCode.hasCoding()) {
                vaccineCode = immunization.vaccineCode.coding[0].code
            }
        }
        if (immunization.hasOccurrenceDateTimeType()) {
            val fhirDate = immunization.occurrenceDateTimeType.valueAsString
            val convertedDate = FormatterClass().convertDateFormat(fhirDate)
            if (convertedDate != null) {
                nextDate = convertedDate
            }
        }
        if (immunization.hasStatusReason()) {
//            statusReason = if (immunization.hasStatusReason() && immunization.statusReason.hasText())
//                immunization.statusReason.text else ""

            statusReason = if (immunization.hasStatusReason() &&
                immunization.statusReason.hasCoding() &&
                immunization.statusReason.codingFirstRep.hasDisplay()
            ) {
                immunization.statusReason.codingFirstRep.display
            } else ""

        }


        if (immunization.hasReasonCode()) {
            status =
                if (immunization.reasonCode[0].hasText()) immunization.reasonCode[0].text else ""
        }


        return DbVaccineNotDone(
            logicalId, vaccineCode, vaccineName, nextDate, statusReason, status
        )
    }

    private fun createVaccineItemDetails(immunization: Immunization): DbVaccineDetailsData {

        var logicalId = ""
        var vaccineName = ""
        var dosesAdministered = ""
        var seriesDosesString = ""
        var series = ""
        var status = ""
        var location = ""
        var practioner = ""

        if (immunization.hasId()) {
            logicalId = immunization.id
        }
        if (immunization.hasVaccineCode()) {
            if (immunization.vaccineCode.hasText()) vaccineName = immunization.vaccineCode.text
        }
        if (immunization.hasOccurrenceDateTimeType()) {
            val fhirDate = immunization.occurrenceDateTimeType.valueAsString
            val convertedDate = FormatterClass().convertDateFormat(fhirDate)
            if (convertedDate != null) {
                dosesAdministered = convertedDate
            }
        }
        if (immunization.hasProtocolApplied()) {
            if (immunization.protocolApplied.isNotEmpty() && immunization.protocolApplied[0].hasSeriesDoses()) {
                seriesDosesString = immunization.protocolApplied[0].seriesDoses.toString()

                series = immunization.protocolApplied[0].series
            }
        }


        if (immunization.hasStatus()) {
            status = immunization.statusElement.value.name
        }

        if (immunization.hasLocation() && immunization.location.hasReference()) {
            location = immunization.location.reference
        }
        if (immunization.hasPerformer() &&
            immunization.performer[0].hasActor() &&
            immunization.performer[0].actor.hasReference()
        ) {
            practioner = immunization.performer[0].actor.reference
        }
        val recorded = if (immunization.hasRecorded()) immunization.recorded.toString() else ""

        return DbVaccineDetailsData(
            logicalId,
            vaccineName,
            dosesAdministered,
            seriesDosesString,
            series,
            status,
            location,
            practioner,
            recorded
        )
    }

    private fun createContraItemDetails(data: ImmunizationRecommendation): Contraindication {

        var logicalId = ""
        var vaccineName = ""
        var vaccineCode = ""
        var nextDate = ""
        var contraDetail = ""
        var status = ""

        if (data.hasId()) {
            logicalId = data.id
        }
        if (data.hasRecommendation()) {

            if (data.recommendation[0].hasContraindicatedVaccineCode()) vaccineCode =
                data.recommendationFirstRep.contraindicatedVaccineCodeFirstRep.text
            if (data.recommendation[0].hasDateCriterion()) nextDate =
                data.recommendationFirstRep.dateCriterionFirstRep.value.toString()
            if (data.recommendation[0].hasForecastReason()) contraDetail =
                data.recommendationFirstRep.forecastReasonFirstRep.text
            if (data.recommendation[0].hasForecastStatus()) status =
                data.recommendationFirstRep.forecastStatus.text
            if (data.recommendation[0].hasTargetDisease()) vaccineName =
                data.recommendationFirstRep.targetDisease.text

        }


        return Contraindication(
            logicalId, vaccineCode, vaccineName, nextDate, contraDetail, status
        )
    }

    private suspend fun getVaccineListDetailsOld(): ArrayList<DbVaccineData> {

        val vaccineList = ArrayList<DbVaccineData>()

        fhirEngine
            .search<Immunization> {
                filter(Immunization.PATIENT, { value = "Patient/$patientId" })
                sort(Immunization.DATE, Order.DESCENDING)
            }
            .map { createVaccineItem(it) }
            .let { vaccineList.addAll(it) }

//        val newVaccineList = vaccineList.filterNot {
//            it.status == "NOTDONE"
//        }

        return ArrayList(vaccineList)
    }

    private fun createAllergyIntoleranceItem(data: AllergyIntolerance): DbVaccineData {

        var vaccineName = ""
        var doseNumberValue = ""
        val logicalId = if (data.hasEncounter()) data.encounter.reference else ""
        var dateScheduled = ""
        var status = ""

        val ref = logicalId.toString().replace("Encounter/", "")

        if (data.hasNote()) {
            status = if (data.noteFirstRep.hasText()) data.noteFirstRep.text else ""
        }

        return DbVaccineData(
            ref,
            null,
            vaccineName,
            doseNumberValue,
            dateScheduled,
            status
        )
    }

    private fun createServiceRequestItem(data: ServiceRequest): DbServiceRequest {

        val logicalId = if (data.hasId()) data.logicalId else ""
        val status = if (data.hasStatus()) data.status.toString() else ""
        val intent = if (data.hasIntent()) data.intent.toString() else ""
        val priority = if (data.hasPriority()) data.priority.toString() else ""
        val authoredOn = if (data.hasAuthoredOn()) data.authoredOn.toString() else ""
        val patientIdRef =
            if (data.hasSubject()) if (data.subject.hasReference()) data.subject.reference else "" else ""

        val vaccineCode =
            if (data.hasReasonCode()) if (data.reasonCodeFirstRep.hasCoding()) if (data.reasonCodeFirstRep.codingFirstRep.hasCode()) data.reasonCodeFirstRep.codingFirstRep.code else "" else "" else ""

        val vaccineName =
            if (data.hasReasonCode()) if (data.reasonCodeFirstRep.hasCoding()) if (data.reasonCodeFirstRep.codingFirstRep.hasDisplay()) data.reasonCodeFirstRep.codingFirstRep.display else "" else "" else ""
        val referringCHP = ""
        val detailsGiven =
            if (data.hasNote()) if (data.noteFirstRep.hasText()) data.noteFirstRep.text else "" else ""
        val referralDate =
            if (data.hasOccurrencePeriod()) if (data.occurrencePeriod.hasStart()) data.occurrencePeriod.start.toString() else "" else ""
        val scheduledDate =
            if (data.hasOccurrencePeriod()) if (data.occurrencePeriod.hasEnd()) data.occurrencePeriod.end.toString() else "" else ""
        val dateAdministered = "-"
        val healthFacility =
            if (data.hasPerformer()) if (data.performerFirstRep.hasDisplay()) data.performerFirstRep.display else "" else ""
        Timber.e("Status posted here ******$status***")
        return DbServiceRequest(
            logicalId,
            status,
            intent,
            priority,
            patientIdRef,
            authoredOn,
            vaccineName,
            vaccineCode,
            referringCHP,
            detailsGiven,
            referralDate,
            scheduledDate,
            dateAdministered,
            healthFacility
        )
    }

    private fun createAdverseEventItem(data: AdverseEvent): DbVaccineData {

        var vaccineName = ""
        var doseNumberValue = ""
        val logicalId = if (data.hasEncounter()) data.encounter.reference else ""
        var dateScheduled = ""
        var status = ""

        val ref = logicalId.toString().replace("Encounter/", "")

        if (data.hasEvent()) {
            status = if (data.event.hasText()) data.event.text else ""
        }

        return DbVaccineData(
            ref,
            null,
            vaccineName,
            doseNumberValue,
            dateScheduled,
            status
        )
    }

    private fun createVaccineItem(immunization: Immunization): DbVaccineData {

        val immunizationHandler = ImmunizationHandler()

        var vaccineName = ""
        var doseNumberValue = ""
        val logicalId = if (immunization.hasEncounter()) immunization.encounter.reference else ""
        var dateScheduled = ""
        var dateRecorded = ""
        var status = ""

        val ref = logicalId.toString().replace("Encounter/", "")

        if (immunization.hasVaccineCode()) {
            if (immunization.vaccineCode.hasText()) vaccineName = immunization.vaccineCode.text
        }

        if (immunization.hasOccurrenceDateTimeType()) {
            val fhirDate = immunization.occurrenceDateTimeType.valueAsString
            val convertedDate = FormatterClass().convertDateFormat(fhirDate)
            if (convertedDate != null) {
                dateScheduled = convertedDate
            }
        }
        if (immunization.hasRecorded()) {
            val fhirDate = immunization.recorded.toString()
            val convertedDate = FormatterClass().convertDateFormat(fhirDate)
            if (convertedDate != null) {
                dateRecorded = convertedDate
            }
        }
        if (immunization.hasProtocolApplied()) {
            if (immunization.protocolApplied.isNotEmpty() && immunization.protocolApplied[0].hasSeriesDoses()) doseNumberValue =
                immunization.protocolApplied[0].seriesDoses.toString()
        }
        if (immunization.hasStatus()) {
            status = immunization.statusElement.value.name
        }


        if (status == "NOTDONE") {
            if (immunization.hasReasonCode()) {
                if (immunization.reasonCode.isNotEmpty() && immunization.reasonCode[0].hasText()) {
                    status = immunization.reasonCode[0].text
                }
            }
        }


        /**
         * 1. Get the vaccine name, get series number, get the previous series number, get the previous Basic Vaccine
         * 2. From the Previous Basic Vaccine, get the date administered
         * 3. Calculate the next vaccination date and display it
         */
        var previousBasicVaccine: BasicVaccine? = null
        val basicVaccine = immunizationHandler.getVaccineDetailsByBasicVaccineName(vaccineName)
        if (basicVaccine != null) {
            val doseNumber = basicVaccine.doseNumber
            val seriesVaccine = immunizationHandler.getRoutineSeriesByBasicVaccine(basicVaccine)
            if (seriesVaccine != null) {
                previousBasicVaccine =
                    immunizationHandler.getPreviousBasicVaccineInSeries(seriesVaccine, doseNumber)
            }
        }

        return DbVaccineData(
            ref,
            previousBasicVaccine,
            vaccineName,
            doseNumberValue,
            dateScheduled,
            status,
            dateRecorded
        )
    }

    private suspend fun createEncounterAefiItem(
        encounter: Encounter,
        resources: Resources
    ): AdverseEventData {

        val type = generateObservationByCode(encounter.logicalId, AEFI_TYPE) ?: ""
        val date = generateObservationByCode(encounter.logicalId, AEFI_DATE) ?: ""
        return AdverseEventData(
            encounter.logicalId,
            type,
            date,
        )
    }

    private suspend fun generateObservationByCode(encounterId: String, codeValue: String): String? {
        var data = ""
        fhirEngine
            .search<Observation> {
                filter(Observation.SUBJECT, { value = "Patient/$patientId" })
                filter(Observation.ENCOUNTER, { value = "Encounter/$encounterId" })
                filter(
                    Observation.CODE,
                    {
                        value = of(Coding().apply {
                            system = "http://loinc.org"
                            code = codeValue
                        })
                    })
                sort(Observation.DATE, Order.ASCENDING)
            }
            .map { createObservationItem(it, getApplication<Application>().resources) }
            .firstOrNull()?.let {
                data = it.value
            }
        return data
    }

    fun loadImmunizationAefis(logicalId: List<DbVaccineData>) = runBlocking {
        loadInternalImmunizationAefis(logicalId)
    }

    private suspend fun loadInternalImmunizationAefis(list: List<DbVaccineData>): List<AdverseEventData> {

        val encounterList = ArrayList<AdverseEventData>()
        fhirEngine
            .search<Encounter> {
                filter(
                    Encounter.SUBJECT,
                    { value = "Patient/$patientId" })
                sort(Encounter.DATE, Order.DESCENDING)
            }
            .map {
                createEncounterAefiItem(
                    it,
                    getApplication<Application>().resources
                )
            }
            .forEach { j ->
                if (list.any { it.logicalId == j.logicalId }) {
                    encounterList.add(j)
                }
            }

        return encounterList.reversed()
    }

    fun getObservationByCode(
        patientId: String,
        encounterId: String?,
        code: String
    ) = runBlocking {
        getObservationDataByCode(patientId, encounterId, code)
    }


    fun getObservationByEncounter(
        patientId: String,
        encounterId: String,
    ) = runBlocking {
        getObservationDataByEncounterId(patientId, encounterId)
    }


    private suspend fun getObservationDataByCode(
        patientId: String,
        encounterId: String?,
        codeValue: String
    ): ObservationDateValue {
        var date = ""
        var dataValue = ""
        fhirEngine
            .search<Observation> {
                filter(Observation.SUBJECT, { value = "Patient/$patientId" })
                if (encounterId != null) filter(
                    Observation.ENCOUNTER,
                    { value = "Encounter/$encounterId" })
                filter(
                    Observation.CODE,
                    {
                        value = of(Coding().apply {
                            system = "http://loinc.org"
                            code = codeValue
                        })
                    })
                sort(Observation.DATE, Order.ASCENDING)
            }
            .map { createObservationItem(it, getApplication<Application>().resources) }
            .firstOrNull()?.let {
                date = it.effective
                dataValue = it.value
            }

        return ObservationDateValue(
            date,
            dataValue,
        )

    }

    private suspend fun getObservationDataByEncounterId(
        patientId: String,
        encounterId: String,
    ): List<Observation> {
        val obs = ArrayList<Observation>()
        fhirEngine
            .search<Observation> {
                filter(Observation.SUBJECT, { value = "Patient/$patientId" })
                filter(
                    Observation.ENCOUNTER,
                    { value = "Encounter/$encounterId" })
                sort(Observation.DATE, Order.ASCENDING)
            }
//            .map { createObservationItem(it, getApplication<Application>().resources) }
            .let {
                obs.addAll(it)
            }
        return obs

    }

    private fun createObservationItem(
        observation: Observation,
        resources: Resources
    ): PatientListViewModel.ObservationItem {
        val observationCode = observation.code.codingFirstRep.code ?: ""


        // Show nothing if no values available for datetime and value quantity.
        val value =
            when {
                observation.hasValueQuantity() -> {
                    observation.valueQuantity.value.toString()
                }

                observation.hasValueCodeableConcept() -> {
                    observation.valueCodeableConcept.coding.firstOrNull()?.display ?: ""
                }

                observation.hasNote() -> {
                    observation.note.firstOrNull()?.author
                }

                observation.hasValueDateTimeType() -> {
                    formatDateToHumanReadable(observation.valueDateTimeType.value.toString())

                }

                observation.hasValueStringType() -> {
                    observation.valueStringType.value.toString()
                }

                else -> {
                    observation.code.text ?: observation.code.codingFirstRep.display
                }
            }
        val valueUnit =
            if (observation.hasValueQuantity()) {
                observation.valueQuantity.unit ?: observation.valueQuantity.code
            } else {
                ""
            }
        val valueString = "$value $valueUnit"
        val dateTimeString = if (observation.hasIssued()) observation.issued.toString() else ""


        return PatientListViewModel.ObservationItem(
            observation.logicalId,
            observationCode,
            "$dateTimeString",
            "$valueString",
        )
    }

    private fun formatDateToHumanReadable(date: String): String? {
        return FormatterClass().convertDateFormat(date)

    }

    fun createAefiEncounter(context: Context, patientId: String, currentAge: String) {

        viewModelScope.launch {
            recordData(context, patientId, currentAge)
        }
    }

    suspend fun recordData(context: Context, patientId: String, currentAge: String) {
        try {
            val modifiedString = currentAge.toLowerCase().replace(' ', '_')
            val subjectReference = Reference("Patient/$patientId")
            val resource = Encounter();
            resource.subject = subjectReference
            resource.id = modifiedString
            fhirEngine.create(resource)
            Timber.tag("TAG").e("Created an Encounter with ID %s", currentAge)
        } catch (e: Exception) {
            e.printStackTrace()
            Timber.tag("TAG").e("Created an Encounter with Exception %s", e.message)
        }
    }

    fun generateCurrentCount(weekNo: String, patientId: String) = runBlocking {
        counterAllergies(weekNo, patientId)
    }


    private suspend fun counterAllergiesOld(weekNo: String, patientId: String): String {
        var counter = 0
        fhirEngine
            .search<AllergyIntolerance> {
                filter(AllergyIntolerance.PATIENT, { value = "Patient/$patientId" })
                sort(AllergyIntolerance.DATE, Order.DESCENDING)
            }
            .map { createAllergyIntoleranceItem(it) }
            .forEach { q ->
                if (q.status.contains(weekNo)) {
                    counter++
                }
            }

        return "$counter"
    }

    private suspend fun counterAllergies(weekNo: String, patientId: String): String {
        var counter = 0
        fhirEngine
            .search<AdverseEvent> {
                filter(AdverseEvent.SUBJECT, { value = "Patient/$patientId" })
                sort(AdverseEvent.DATE, Order.DESCENDING)
            }
            .map { createAdverseEventItem(it) }
            .forEach { q ->
                if (q.status.contains(weekNo)) {
                    counter++
                }
            }

        return "$counter"
    }

    fun updateServiceRequestStatus(serviceRequestId: String) = runBlocking {
        val serviceRequest =
            fhirEngine.get(ResourceType.ServiceRequest, serviceRequestId) as ServiceRequest
        val sr = ServiceRequest()
        sr.id = serviceRequestId
        sr.subject = serviceRequest.subject
        sr.status = ServiceRequestStatus.COMPLETED
        sr.intent = serviceRequest.intent
        sr.category = serviceRequest.category
        sr.priority = serviceRequest.priority
        sr.authoredOn = serviceRequest.authoredOn
        sr.setOccurrence(serviceRequest.occurrencePeriod)
        sr.requester = serviceRequest.requester
        sr.performer = serviceRequest.performer
        sr.reasonCode = serviceRequest.reasonCode
        sr.note = serviceRequest.note

        fhirEngine.update(sr)
    }

}

class PatientDetailsViewModelFactory(
    private val application: Application,
    private val fhirEngine: FhirEngine,
    private val patientId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PatientDetailsViewModel::class.java)) {
            "Unknown ViewModel class"
        }
        return PatientDetailsViewModel(application, fhirEngine, patientId) as T
    }
}


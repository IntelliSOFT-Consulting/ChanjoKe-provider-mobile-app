/*
 * Copyright 2022-2023 Google LLC
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

package com.intellisoft.chanjoke.vaccine

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.fhir.data.DbCodeValue
import com.intellisoft.chanjoke.patient_list.PatientListViewModel
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.logicalId
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.search
import com.intellisoft.chanjoke.fhir.data.DbAppointmentDetails
import com.intellisoft.chanjoke.fhir.data.DbVaccineAdmin
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails
import com.intellisoft.chanjoke.fhir.data.Reasons
import com.intellisoft.chanjoke.vaccine.validations.ImmunizationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.UUID
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.AllergyIntolerance
import org.hl7.fhir.r4.model.Annotation
import org.hl7.fhir.r4.model.Appointment
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Condition
import org.hl7.fhir.r4.model.Encounter
import org.hl7.fhir.r4.model.Immunization
import org.hl7.fhir.r4.model.Immunization.ImmunizationStatus
import org.hl7.fhir.r4.model.ImmunizationRecommendation
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.PositiveIntType
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.SimpleQuantity
import org.hl7.fhir.r4.model.StringType
import org.hl7.fhir.r4.model.Type
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Date

/** ViewModel for patient registration screen {@link AddPatientFragment}. */
class AdministerVaccineViewModel(
    application: Application,
    private val state: SavedStateHandle
) :
    AndroidViewModel(application) {

    val questionnaire: String
        get() = getQuestionnaireJson()

    val isResourcesSaved = MutableLiveData<Boolean>()

    private val questionnaireResource: Questionnaire
        get() =
            FhirContext.forCached(FhirVersionEnum.R4).newJsonParser().parseResource(questionnaire)
                    as Questionnaire

    private var questionnaireJson: String? = null
    private var fhirEngine: FhirEngine = FhirApplication.fhirEngine(application.applicationContext)

    fun saveScreenerEncounter(questionnaireResponse: QuestionnaireResponse, patientId: String) {
        viewModelScope.launch {
            val bundle = ResourceMapper.extract(questionnaireResource, questionnaireResponse)
            val subjectReference = Reference("Patient/$patientId")
            val encounterId = generateUuid()
//      if (isRequiredFieldMissing(bundle)) {
//        isResourcesSaved.value = false
//        return@launch
//      }

            val context = FhirContext.forR4()
            val questionnaire =
                context.newJsonParser().encodeResourceToString(questionnaireResponse)

            println(questionnaire)

            saveResources(bundle, subjectReference, encounterId, patientId)

            isResourcesSaved.value = true
        }
    }

    private suspend fun saveResources(
        bundle: Bundle,
        subjectReference: Reference,
        encounterId: String,
        patientId: String,
    ) {

        val encounterReference = Reference("Encounter/$encounterId")
        bundle.entry.forEach {

            when (val resource = it.resource) {
                is Observation -> {
                    if (resource.hasCode()) {
                        val uuid = generateUuid()
                        resource.id = uuid
                        resource.subject = subjectReference
                        resource.encounter = encounterReference
                        resource.issued = Date()
                        saveResourceToDatabase(resource, "Obs " + uuid)
                    }
                }

                is Condition -> {
                    if (resource.hasCode()) {
                        val uuid = generateUuid()
                        resource.id = uuid
                        resource.subject = subjectReference
                        resource.encounter = encounterReference
                        saveResourceToDatabase(resource, "cond " + uuid)
                    }
                }

                is Encounter -> {
                    resource.subject = subjectReference
                    resource.id = encounterId
                    /**
                     * Check for AEFIs should be partOf
                     * */
                    if (FormatterClass().getSharedPref(
                            "vaccinationFlow",
                            getApplication<Application>().applicationContext
                        ) == "addAefi"
                    ) {
                        val ref = FormatterClass().getSharedPref(
                            "encounter_logical_id",
                            getApplication<Application>().applicationContext
                        )
                        val age = FormatterClass().getSharedPref(
                            "current_age",
                            getApplication<Application>().applicationContext
                        )
//                        val parentReference = Reference("Encounter/$ref")
//                        resource.partOf = parentReference

                        //Create Adverse effects
                        createAdverseEffects(encounterId, patientId, age.toString())
                    }

                    saveResourceToDatabase(resource, "enc " + encounterId)

                    val vaccinationFlow = FormatterClass().getSharedPref(
                        "vaccinationFlow",
                        getApplication<Application>().applicationContext
                    )
                    if (
                        vaccinationFlow == "createVaccineDetails" ||
                        vaccinationFlow == "updateVaccineDetails" ||
                        vaccinationFlow == "recommendVaccineDetails"
                    ) {
                        generateImmunizationRecord(encounterId, patientId)
                    }
                }
            }
        }
    }

    private suspend fun createAdverseEffects(encounterId: String, patientId: String, age: String) {

        val encounterReference = Reference("Encounter/$encounterId")
        val patientReference = Reference("Patient/$patientId")

        val allergyIntolerance = AllergyIntolerance()
        allergyIntolerance.id = generateUuid()
        allergyIntolerance.encounter = encounterReference
        allergyIntolerance.patient = patientReference
        allergyIntolerance.noteFirstRep.text = age

        /**
         * TODO: Add more details for the allergy intolerance
         */

        saveResourceToDatabase(allergyIntolerance, "intolerance ")

    }

    fun createManualImmunizationResource(
        immunizationList: List<String>,
        encounterId: String,
        patientId: String,
        context: Context,
        dateValue: String? = null,
        status: ImmunizationStatus
    ) {
        CoroutineScope(Dispatchers.IO).launch {

            val formatterClass = FormatterClass()
            val immunizationHandler = ImmunizationHandler()
            val immunizationDataList = ArrayList<Immunization>()

            val job = Job()
            CoroutineScope(Dispatchers.IO + job).launch {

                for (vaccineNameValue in immunizationList) {

                    val vaccineBasicVaccine =
                        ImmunizationHandler().getVaccineDetailsByBasicVaccineName(vaccineNameValue)

                    if (vaccineBasicVaccine != null) {
                        val seriesVaccine =
                            immunizationHandler.getSeriesByBasicVaccine(vaccineBasicVaccine)
                        val targetDisease = seriesVaccine?.targetDisease

                        val job1 = Job()
                        CoroutineScope(Dispatchers.IO + job1).launch {
                            //Save resources to Shared preference
                            if (targetDisease != null) {
                                formatterClass.saveStockValue(
                                    vaccineNameValue,
                                    targetDisease,
                                    getApplication<Application>().applicationContext
                                )
                            }
                        }.join()

                        val date = if(dateValue != null) {
                            formatterClass.convertStringToDate(dateValue, "MMM d yyyy") ?: Date()
                        }else{
                            Date()
                        }

                        val immunization = createImmunizationResource(
                            encounterId,
                            patientId,
                            status,
                            date
                        )

                        immunizationDataList.add(immunization)

                        //Create Immunization Recommendation
                        formatterClass.saveSharedPref("immunizationId",immunization.id,context)
                        formatterClass.saveSharedPref("administeredProduct",vaccineNameValue,context)
                        formatterClass.saveSharedPref("patientId",patientId,context)
                        formatterClass.saveSharedPref("immunizationDate",date.toString(),context)

                        saveResourceToDatabase(immunization, "immunization")
                    }
                }
            }.join()

            for (immunization in immunizationDataList) {

                if (immunization.hasVaccineCode()){
                    if (immunization.vaccineCode.hasCoding()){
                        if (immunization.vaccineCode.codingFirstRep.hasDisplay()){
                            formatterClass.saveSharedPref(
                                "administeredProduct",
                                immunization.vaccineCode.codingFirstRep.display,
                                context)
                        }
                    }
                }
                if (immunization.hasOccurrenceDateTimeType()){
                    val occurrenceDateTime = immunization.occurrenceDateTimeType.value.toString()
                    formatterClass.saveSharedPref("immunizationDate",occurrenceDateTime.toString(),context)
                }

                createImmunizationRecommendation(context)

            }

        }
    }



    fun createImmunizationRecommendation(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val formatterClass = FormatterClass()
            val workflowVaccinationType = formatterClass.getSharedPref("workflowVaccinationType", context)

            val type = if (workflowVaccinationType != null && workflowVaccinationType == "NON-ROUTINE") {
//                createNextNonRoutineImmunization()
                "NON-ROUTINE"
            }else{
                "ROUTINE"
            }
            createNextImmunization(type,context)

        }
    }

    private suspend fun createNextImmunization(type:String, context: Context) {

        val formatterClass = FormatterClass()

        /**
         * TODO: Check if the Vaccine exists
         */

        //Vaccine code
        val administeredProduct = formatterClass.getSharedPref(
            "administeredProduct", getApplication<Application>().applicationContext
        )
        val patientId = formatterClass.getSharedPref(
            "patientId", getApplication<Application>().applicationContext
        )
        val immunizationDate = formatterClass.getSharedPref(
            "immunizationDate", getApplication<Application>().applicationContext
        )

        /**
         * Get the current administered product and generate the next vaccine
         */
        if (administeredProduct != null && patientId != null && immunizationDate != null) {

            //Make sure the format is the same as the immunizationDate
            val date = formatterClass.convertStringToDate(immunizationDate, "EEE MMM dd HH:mm:ss 'GMT'Z yyyy")

            val immunizationHandler = ImmunizationHandler()
            val vaccineBasicVaccine =
                ImmunizationHandler().getVaccineDetailsByBasicVaccineName(administeredProduct)

            val nextBasicVaccine = vaccineBasicVaccine?.let {
                immunizationHandler.getNextDoseDetails(
                    it
                )
            }

            val seriesVaccine =
                vaccineBasicVaccine?.let { immunizationHandler.getRoutineSeriesByBasicVaccine(it) }

            val targetDisease = seriesVaccine?.targetDisease
            val seriesDosesNumber = seriesVaccine?.seriesDoses

            val nhdd = seriesVaccine?.NHDD

            val vaccineName = nextBasicVaccine?.vaccineName
            val vaccineCode = nextBasicVaccine?.vaccineCode
            val doseNumber = nextBasicVaccine?.doseNumber

            val job = Job()
            CoroutineScope(Dispatchers.IO + job).launch {
                //Save resources to Shared preference
                if (vaccineName != null && targetDisease != null) {
                    FormatterClass().saveStockValue(
                        vaccineName,
                        targetDisease,
                        getApplication<Application>().applicationContext
                    )
                }
            }.join()

            //Generate the next immunisation recommendation
            if (nextBasicVaccine != null && date != null) {

                val administrativeWeeksSincePreviousList =
                    nextBasicVaccine.administrativeWeeksSincePrevious
                val administrativeWeeksSinceDob = nextBasicVaccine.administrativeWeeksSinceDOB

                //Check if the above list is more than one.

                val nextImmunizationDate = if (administrativeWeeksSincePreviousList.isNotEmpty()) {
                    //This is not the first vaccine, check on administrative weeks after birth
                    val weeksToAdd = administrativeWeeksSincePreviousList[0]
                    formatterClass.getNextDate(date, weeksToAdd)
                } else {
                    formatterClass.getNextDate(date, administrativeWeeksSinceDob.toDouble())
                }
                val localDate = formatterClass.convertDateToLocalExactDate(nextImmunizationDate)

                val immunizationNextDateFormat = formatterClass.convertDateToString(nextImmunizationDate.toString())
                formatterClass.saveSharedPref("immunizationNextDate",
                    immunizationNextDateFormat,context)


                /**
                 * Get the immunization recommendation
                 */
                val immunizationRecommendationList = ArrayList<ImmunizationRecommendation>()
                fhirEngine
                    .search<ImmunizationRecommendation> {
                        filter(ImmunizationRecommendation.PATIENT, { value = "Patient/$patientId" })
//                        filter(ImmunizationRecommendation.TARGET_DISEASE, {
//                            value = of(patientId)
//                        })
                        sort(Encounter.DATE, Order.DESCENDING)
                    }
                    .map { getRecommendationData(it) }
                    .let { immunizationRecommendationList.addAll(it)}

                /**
                 * These will include immunization recommendations that have been created before
                 *
                 * a.) ROUTINE: Check if there's an immunization recommendation for the next vaccine
                 * if it exists, update it with the new date. If not leave it as it is
                 * All routine will have a recommendation with the new date
                 *
                 * b.) NON-ROUTINE: Check if there's an immunization recommendation for the next vaccine'
                 * if it exists, update it with the new date. If not leave create a new one
                 *
                 * 1.) Get the immunization recommendation
                 */

                val immunizationRecommendation = immunizationRecommendationList.firstOrNull()
                if (immunizationRecommendation != null) {

                    val recommendationList = immunizationRecommendation.recommendation

                    //We will only update the date criterion
                    recommendationList.map { recommendation ->

                        if (recommendation.hasVaccineCode() &&
                            recommendation.vaccineCodeFirstRep.hasCoding() &&
                            recommendation.vaccineCodeFirstRep.hasCoding() &&
                            recommendation.vaccineCodeFirstRep.codingFirstRep.hasDisplay() &&
                            recommendation.vaccineCodeFirstRep.codingFirstRep.display == vaccineCode){
                            if (recommendation.hasDateCriterion()) {

                                val dateCriterion = getNewDateCriterion(localDate)
                                recommendation.dateCriterion = dateCriterion
                            }else{
                                recommendation
                            }
                        }else{
                            recommendation
                        }
                    }

                    updateResourceToDatabase(immunizationRecommendation, "ImmunizationRecommendation Update")

                }


            }

        }

    }

    private fun getNewDateCriterion(localDate: LocalDate):
            ArrayList<ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent>{
        //From selectedDate, calculate the next date plus administrativeWeeksSinceDOB
        val dateCriterionList = ArrayList<ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent>()

        val formatterClass = FormatterClass()

        val earliestAdministerDate = formatterClass.convertLocalDateToDate(localDate)

        val latestAdministerDate = formatterClass.calculateDateAfterWeeksAsString(localDate, 2)

        val earliestAdministerLocalDate = formatterClass.convertStringToDate(earliestAdministerDate, "yyyy-MM-dd")
        val latestAdministerLocalDate = formatterClass.convertStringToDate(latestAdministerDate, "yyyy-MM-dd")

        Log.e("----->","<-----")
        println("earliestAdministerLocalDate $earliestAdministerLocalDate")
        println("latestAdministerLocalDate $latestAdministerLocalDate")
        Log.e("----->","<-----")

        if (earliestAdministerLocalDate != null && latestAdministerLocalDate != null){

            val earlyAdministerDate = DbVaccineAdmin(earliestAdministerLocalDate, "Earliest-date-to-administer")
            val lateAdministerDate = DbVaccineAdmin(latestAdministerLocalDate, "Latest-date-to-administer")

            val administerTimeList = ArrayList<DbVaccineAdmin>()
            administerTimeList.addAll(
                mutableListOf(earlyAdministerDate, lateAdministerDate)
            )

            /**
             * Date Criterion
             * TODO: Add earliest date to administered date
             * TODO: Add latest date to administered date
             */
            administerTimeList.forEach { administerTime ->
                val dateCriterion = ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent()

                val code = CodeableConcept()
                val codeCoding = Coding()
                codeCoding.system = "http://snomed.info/sct"
                codeCoding.code = administerTime.type
                codeCoding.display = administerTime.type
                code.coding = listOf(codeCoding)
                dateCriterion.code = code
                dateCriterion.value = administerTime.dateAdministered
                dateCriterionList.add(dateCriterion)
            }

        }
        return dateCriterionList
    }

    private suspend fun createNextNonRoutineImmunization() {

        val formatterClass = FormatterClass()

        /**
         * TODO: Check if the Vaccine exists
         */

        //Vaccine code
        val administeredProduct = formatterClass.getSharedPref(
            "administeredProduct", getApplication<Application>().applicationContext
        )
        val patientId = formatterClass.getSharedPref(
            "patientId", getApplication<Application>().applicationContext
        )
        val immunizationDate = formatterClass.getSharedPref(
            "immunizationDate", getApplication<Application>().applicationContext
        )

        /**
         * Get the current administered product and generate the next vaccine
         */
        if (administeredProduct != null && patientId != null && immunizationDate != null) {

            //Make sure the format is the same as the immunizationDate
            val date = formatterClass.convertStringToDate(immunizationDate, "EEE MMM dd HH:mm:ss 'GMT'Z yyyy")

            val immunizationHandler = ImmunizationHandler()
            val vaccineBasicVaccine =
                ImmunizationHandler().getVaccineDetailsByBasicVaccineName(administeredProduct)

            val nextBasicVaccine = vaccineBasicVaccine?.let {
                immunizationHandler.getNextDoseDetails(
                    it
                )
            }

            val seriesVaccine =
                nextBasicVaccine?.let { immunizationHandler.getSeriesByBasicVaccine(it) }

            val targetDisease = seriesVaccine?.targetDisease

            val vaccineName = nextBasicVaccine?.vaccineName
            val vaccineCode = nextBasicVaccine?.vaccineCode

            val job = Job()
            CoroutineScope(Dispatchers.IO + job).launch {
                //Save resources to Shared preference
                if (vaccineName != null && targetDisease != null) {
                    FormatterClass().saveStockValue(
                        vaccineName,
                        targetDisease,
                        getApplication<Application>().applicationContext
                    )
                }
            }.join()

            //Generate the next immunisation recommendation
            if (nextBasicVaccine != null && date != null) {

                val administrativeWeeksSincePreviousList = nextBasicVaccine.administrativeWeeksSincePrevious
                val administrativeWeeksSinceDob = nextBasicVaccine.administrativeWeeksSinceDOB
//

                //Check if the above list is more than one.

                val nextImmunizationDate = if (administrativeWeeksSincePreviousList.isNotEmpty()) {
                    //This is not the first vaccine, check on administrative weeks after birth
                    val weeksToAdd = administrativeWeeksSincePreviousList[0]
                    formatterClass.getNextDate(date, weeksToAdd)
                } else {
                    formatterClass.getNextDate(date, administrativeWeeksSinceDob.toDouble())
                }
                val localDate = formatterClass.convertDateToLocalDate(nextImmunizationDate)
                val localDateString = formatterClass.convertLocalDateToDate(localDate)

                val earliestAdministerLocalDate = formatterClass.convertStringToDate(localDateString, "yyyy-MM-dd")

                /**
                 * Get the immunization recommendation
                 */

                val immunizationRecommendation = createImmunizationRecommendationResource(
                    patientId,
                    earliestAdministerLocalDate,
                    "Due",
                    "Next Immunization date",
                    null
                )

                saveResourceToDatabase(immunizationRecommendation, "ImmmRec")

            }


        }


    }



    //Create an immunization resource
    private fun createImmunizationResource(
        encounterId: String?,
        patientId: String,
        immunisationStatus: ImmunizationStatus,
        date: Date?
    ): Immunization {

        val immunization = Immunization()
        val immunizationId = generateUuid()
        if (encounterId != null){
            val encounterReference = Reference("Encounter/$encounterId")
            immunization.encounter = encounterReference
        }
        val patientReference = Reference("Patient/$patientId")

        immunization.patient = patientReference
        immunization.id = immunizationId

        FormatterClass().saveSharedPref(
            "immunizationId",
            immunizationId,
            getApplication<Application>().applicationContext
        )
        immunization.status = immunisationStatus

        val annotationList = ArrayList<Annotation>()

        val practitionerFacility = FormatterClass().getSharedPref("practitionerFacility", getApplication<Application>().applicationContext)
        if (practitionerFacility != null){
            val facilityDetails = practitionerFacility.replace("Location/","")
            val locationReference = Reference("Location/$facilityDetails")
            immunization.location = locationReference

            val annotation = Annotation()
            annotation.text = practitionerFacility
            annotationList.add(annotation)
        }
        //Include a location
        val location = FormatterClass().getSharedPref("selectedFacility", getApplication<Application>().applicationContext)
        if (location != null) {
            val annotation = Annotation()
            annotation.text = location
            annotationList.add(annotation)
        }
        if (annotationList.isNotEmpty()) immunization.note = annotationList


        //Date administered

        /**
         * Occurrence will be the date the vaccine was administered or not-done as selected
         * recorded is the current date
         */

        immunization.occurrenceDateTimeType.value = date
        immunization.recorded = Date()

        //Target Disease
        val targetDisease = FormatterClass().getSharedPref(
            "vaccinationTargetDisease",
            getApplication<Application>().applicationContext
        )
        val protocolList = Immunization().protocolApplied
        val immunizationProtocolAppliedComponent =
            Immunization.ImmunizationProtocolAppliedComponent()

        val diseaseTargetCodeableConceptList = immunizationProtocolAppliedComponent.targetDisease
        val diseaseTargetCodeableConcept = CodeableConcept()
        diseaseTargetCodeableConcept.text = targetDisease
        diseaseTargetCodeableConceptList.add(diseaseTargetCodeableConcept)
        immunizationProtocolAppliedComponent.targetDisease = diseaseTargetCodeableConceptList

        //Series - Name of vaccine series
        val vaccinationSeries = FormatterClass().getSharedPref(
            "vaccinationSeriesDoses",
            getApplication<Application>().applicationContext
        )
        if (vaccinationSeries != null) {
            immunizationProtocolAppliedComponent.series = vaccinationSeries
        }

        //Add Performer
        val fhirPractitionerId = FormatterClass().getSharedPref(
            "fhirPractitionerId",
            getApplication<Application>().applicationContext
        )
        if (fhirPractitionerId != null){
            val practitionerReference = Reference("Practitioner/$fhirPractitionerId")
            val immunizationPerformerComponentList = ArrayList<Immunization.ImmunizationPerformerComponent>()
            val immunizationPerformerComponent = Immunization.ImmunizationPerformerComponent()
            immunizationPerformerComponent.actor = practitionerReference
            immunizationPerformerComponentList.add(immunizationPerformerComponent)

            immunization.performer = immunizationPerformerComponentList
        }



        //Dose number - Recommended number of doses for immunity
        val vaccinationDoseNumber = FormatterClass().getSharedPref(
            "vaccinationDoseNumber",
            getApplication<Application>().applicationContext
        )
        if (vaccinationDoseNumber != null) {
            val stringType = StringType()
            stringType.value = vaccinationDoseNumber
            immunizationProtocolAppliedComponent.seriesDoses = stringType
        }

        protocolList.add(immunizationProtocolAppliedComponent)

        immunization.protocolApplied = protocolList

        //Dose Quantity is the amount of vaccine administered
        val dosage = FormatterClass().getSharedPref(
            "vaccinationDoseQuantity",
            getApplication<Application>().applicationContext
        )
        if (dosage != null) {
            val nonDosage = FormatterClass().removeNonNumeric(dosage)
            val bigDecimalValue = BigDecimal(nonDosage)
            val simpleQuantity = SimpleQuantity()
            simpleQuantity.value = bigDecimalValue
            immunization.doseQuantity = simpleQuantity
        }

        //Administration Method
        val vaccinationAdministrationMethod = FormatterClass().getSharedPref(
            "vaccinationAdministrationMethod", getApplication<Application>().applicationContext
        )
        if (vaccinationAdministrationMethod != null) {
            val codeableConcept = CodeableConcept()
            codeableConcept.text = vaccinationAdministrationMethod
            codeableConcept.id = generateUuid()

            immunization.site = codeableConcept
        }

        //Batch number
        val vaccinationBatchNumber = FormatterClass().getSharedPref(
            "vaccinationBatchNumber", getApplication<Application>().applicationContext
        )
        if (vaccinationBatchNumber != null) {
            immunization.lotNumber = vaccinationBatchNumber
        }

        //Expiration date
        val vaccinationExpirationDate = FormatterClass().getSharedPref(
            "vaccinationExpirationDate", getApplication<Application>().applicationContext
        )
        if (vaccinationExpirationDate != null && vaccinationExpirationDate != "") {
            val dateExp = FormatterClass().convertStringToDate(
                vaccinationExpirationDate, "YYYY-MM-DD"
            )
            if (dateExp != null) {
                immunization.expirationDate = dateExp
            }

        }
        //Vaccine code
        val administeredProduct = FormatterClass().getSharedPref(
            "administeredProduct", getApplication<Application>().applicationContext
        )
        //Get info on the vaccine
        if (administeredProduct != null) {
            val basicVaccine =
                ImmunizationHandler().getVaccineDetailsByBasicVaccineName(administeredProduct)
            if (basicVaccine != null) {
                val vaccineCodeConcept = CodeableConcept()
                vaccineCodeConcept.text = basicVaccine.vaccineName

                val codingList = ArrayList<Coding>()
                val vaccineCoding = Coding()
                vaccineCoding.code = basicVaccine.vaccineCode
                vaccineCoding.display = basicVaccine.vaccineName
                codingList.add(vaccineCoding)
                vaccineCodeConcept.coding = codingList

                immunization.vaccineCode = vaccineCodeConcept
            }
        }


        return immunization


    }
    private suspend fun updateResourceToDatabase(resource: Resource, resourceType: String){
        Log.e("----Update", "----$resourceType")
        fhirEngine.update(resource)
    }
    private fun getRecommendationData(it: ImmunizationRecommendation): ImmunizationRecommendation {
        return it
    }

    fun createManualContraindication(
        administrationFlowTitle: String?,
        immunizationList: List<String>,
        patientId: String,
        nextImmunizationDate: Date?,
        status: String,
        immunizationId: String?,
        foreCastReason: String,
        context: Context
    ) {
        CoroutineScope(Dispatchers.IO).launch {

            val immunizationHandler = ImmunizationHandler()

            for (vaccineNameValue in immunizationList) {

                val vaccineBasicVaccine =
                    ImmunizationHandler().getVaccineDetailsByBasicVaccineName(vaccineNameValue)

                if (vaccineBasicVaccine != null) {
                    val seriesVaccine =
                        immunizationHandler.getSeriesByBasicVaccine(vaccineBasicVaccine)
                    val targetDisease = seriesVaccine?.targetDisease

                    val job = Job()
                    CoroutineScope(Dispatchers.IO + job).launch {
                        //Save resources to Shared preference
                        if (targetDisease != null) {
                            FormatterClass().saveStockValue(
                                vaccineNameValue,
                                targetDisease,
                                getApplication<Application>().applicationContext
                            )
                        }
                    }.join()

                    /**
                     * Update the status with the typed one
                     * -> For not administered, create an immunization resource
                     *
                     */

                    if (administrationFlowTitle != null){

                        if (administrationFlowTitle == NavigationDetails.NOT_ADMINISTER_VACCINE.name ||
                            administrationFlowTitle == NavigationDetails.CONTRAINDICATIONS.name){

                            val immunization = createImmunizationResource(
                                null,
                                patientId,
                                ImmunizationStatus.NOTDONE,
                                nextImmunizationDate
                            )
                            val codeableConcept = CodeableConcept()
                            codeableConcept.text = "Reasons for not administering"

                            val codingList = ArrayList<Coding>()
                            val coding = Coding()
                            coding.code = "371900001"
                            coding.display = foreCastReason
                            coding.system = "http://snomed.info/sct"
                            codingList.add(coding)

                            codeableConcept.coding = codingList

                            immunization.statusReason = codeableConcept

                            //Reason Code
                            val reasonCodeValue = if (administrationFlowTitle == NavigationDetails.CONTRAINDICATIONS.name){
                                Reasons.CONTRAINDICATE.name
                            }else{
                                Reasons.NOT_ADMINISTERED.name
                            }

                            val reasonCodeValueList = ArrayList<CodeableConcept>()

                            val reasonCode = CodeableConcept()
                            reasonCode.text = reasonCodeValue
                            reasonCode.id = generateUuid()

                            val reasonCodeList = ArrayList<Coding>()
                            val reasonCodeCoding = Coding()
                            reasonCodeCoding.code = "371900001371900001"
                            reasonCodeCoding.display = reasonCodeValue
                            reasonCodeCoding.system = "http://snomed.info/sct"
                            reasonCodeList.add(reasonCodeCoding)

                            reasonCode.coding = reasonCodeList

                            reasonCodeValueList.add(reasonCode)

                            immunization.reasonCode = reasonCodeValueList

                            saveResourceToDatabase(immunization, "Imm")

                            createImmunizationRecommendation(context)

                        }




                    }

//                    val recommendation = createImmunizationRecommendationResource(
//                        patientId,
//                        nextImmunizationDate,
//                        status,
//                        foreCastReason,
//                        immunizationId
//                    )
//                    saveResourceToDatabase(recommendation, "ImmRec")

                }


            }


        }
    }

    //Create an immunizationRecommendation resource
    private fun createImmunizationRecommendationResource(
        patientId: String,
        recommendedDate: Date?,
        status: String,
        statusReason: String?,
        immunizationId: String?,
    ): ImmunizationRecommendation {

        val immunizationHandler = ImmunizationHandler()
        val immunizationRecommendation = ImmunizationRecommendation()
        val patientReference = Reference("Patient/$patientId")
        val id = generateUuid()
        immunizationRecommendation.patient = patientReference
        immunizationRecommendation.id = id
        immunizationRecommendation.date = Date()

        //Recommendation
        val recommendationList =
            ArrayList<ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent>()
        val immunizationRequest =
            ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent()

        //Target Disease
        val targetDisease = FormatterClass().getSharedPref(
            "vaccinationTargetDisease",
            getApplication<Application>().applicationContext
        )
        val codeableConceptTargetDisease = CodeableConcept()
        codeableConceptTargetDisease.text = targetDisease
        immunizationRequest.targetDisease = codeableConceptTargetDisease


        //Status
        val codeableConceptStatus = CodeableConcept()
        codeableConceptStatus.text = status
        immunizationRequest.forecastStatus = codeableConceptStatus

        //Status Reason
        if (statusReason != null) {
            val codeableConceptStatusReason = CodeableConcept()
            codeableConceptStatusReason.text = statusReason
            val forecastList = ArrayList<CodeableConcept>()
            forecastList.add(codeableConceptStatusReason)

            immunizationRequest.forecastReason = forecastList
        }

        //Recommended date
        if (recommendedDate != null) {
            val dateCriterionList =
                ArrayList<ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent>()
            val dateCriterion =
                ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent()

            val code = CodeableConcept()
            val codeCoding = Coding()
            codeCoding.system = "http://snomed.info/sct"
            codeCoding.code = "Earliest-date-to-administer"
            codeCoding.display = "Earliest-date-to-administer"
            code.coding = listOf(codeCoding)
            dateCriterion.code = code
            dateCriterion.value = recommendedDate

            dateCriterionList.add(dateCriterion)

            immunizationRequest.dateCriterion = dateCriterionList
        }

        //Dose Number
        val vaccinationDoseNumber = FormatterClass().getSharedPref(
            "vaccinationDoseNumber",
            getApplication<Application>().applicationContext
        )
        if (vaccinationDoseNumber != null) {
            val doseNumberType = StringType()
            doseNumberType.value = vaccinationDoseNumber
            immunizationRequest.doseNumber = doseNumberType
        }

        //SeriesDoses
        val vaccinationSeriesDoses = FormatterClass().getSharedPref(
            "vaccinationSeriesDoses",
            getApplication<Application>().applicationContext
        )
        if (vaccinationSeriesDoses != null) {
            val seriesDoseType = StringType()
            seriesDoseType.value = vaccinationSeriesDoses
            immunizationRequest.seriesDoses = seriesDoseType
        }

        //Supporting immunisation
        if (immunizationId != null) {
            val immunizationReferenceList = ArrayList<Reference>()
            val immunizationReference = Reference()
            immunizationReference.reference = "Immunization/$immunizationId"
            immunizationReference.display = "Immunization"
            immunizationReferenceList.add(immunizationReference)
            immunizationRequest.supportingImmunization = immunizationReferenceList
        }

        if (status == "Contraindicated" || status == "Due") {
            //Administered vaccine
            val administeredProduct = FormatterClass().getSharedPref(
                "administeredProduct",
                getApplication<Application>().applicationContext
            )

            if (administeredProduct != null) {
                val baseVaccineDetails =
                    immunizationHandler.getVaccineDetailsByBasicVaccineName(administeredProduct)
                if (baseVaccineDetails != null) {

                    val vaccineCode = baseVaccineDetails.vaccineCode
                    val vaccineName = baseVaccineDetails.vaccineName

                    val seriesVaccine = immunizationHandler.getSeriesByBasicVaccine(baseVaccineDetails)
                    val nhdd = seriesVaccine?.NHDD ?: ""

                    val contraindicationCodeableConceptList = ArrayList<CodeableConcept>()

                    val codeableConceptContraindicatedVaccineCode = CodeableConcept()
                    codeableConceptContraindicatedVaccineCode.id = generateUuid()

                    codeableConceptContraindicatedVaccineCode.text = vaccineName

                    val codeableConceptContraindicatedVaccineCodeList = ArrayList<Coding>()
                    val codeableConceptContraindicatedVaccineCodeCoding = Coding()
                    codeableConceptContraindicatedVaccineCodeCoding.code = nhdd.toString()
                    codeableConceptContraindicatedVaccineCodeCoding.display = vaccineCode
                    codeableConceptContraindicatedVaccineCodeCoding.system = "http://snomed.info/sct"
                    codeableConceptContraindicatedVaccineCodeList.add(codeableConceptContraindicatedVaccineCodeCoding)
                    codeableConceptContraindicatedVaccineCode.coding = codeableConceptContraindicatedVaccineCodeList

                    contraindicationCodeableConceptList.add(codeableConceptContraindicatedVaccineCode)



                    //Contraindicated vaccine code
                    immunizationRequest.contraindicatedVaccineCode =
                        contraindicationCodeableConceptList

                    //Vaccine code
                    immunizationRequest.vaccineCode = contraindicationCodeableConceptList

                }
            }
        }

        //Supporting Patient Information
        /**
         * TODO: Check on this
         */

        recommendationList.add(immunizationRequest)
        immunizationRecommendation.recommendation = recommendationList
        return immunizationRecommendation
    }

    //Generate immunisation record
    suspend fun generateImmunizationRecord(
        encounterId: String,
        patientId: String
    ) {

        val fomatterClass = FormatterClass()
        val immunizationHandler = ImmunizationHandler()

        //Check if request is for creating immunisation
        val vaccinationFlow = FormatterClass().getSharedPref(
            "vaccinationFlow",
            getApplication<Application>().applicationContext
        )
        if (vaccinationFlow == "createVaccineDetails") {
            //Request is to create immunisation record

            //Get the vaccines informations
            val vaccinationTargetDisease = fomatterClass.getSharedPref(
                "vaccinationTargetDisease",
                getApplication<Application>().applicationContext
            )
            val administeredProduct = fomatterClass.getSharedPref(
                "administeredProduct",
                getApplication<Application>().applicationContext
            )

            if (administeredProduct != null && vaccinationTargetDisease != null) {

                val job = Job()
                CoroutineScope(Dispatchers.IO + job).launch {
                    //Save resources to Shared preference
                    FormatterClass().saveStockValue(
                        administeredProduct,
                        vaccinationTargetDisease,
                        getApplication<Application>().applicationContext
                    )
                }.join()

                //Check answer to If user wants to administer vaccine
                val status = observationFromCode(
                    "11-1122",
                    patientId,
                    encounterId
                )
                val value = status.value.replace(" ", "")
                if (value == "Yes" || value == "No") {
                    /**
                     * User choose to administer vaccine, send immunisation status as Completed
                     * Generate immunization resource
                     * No need to create a Recommendation. It's only created for the next vaccine in the series
                     */
                    val date = Date()

                    var immunizationStatus = ImmunizationStatus.COMPLETED
                    if (value == "No") immunizationStatus = ImmunizationStatus.NOTDONE

                    val immunization = createImmunizationResource(
                        encounterId,
                        patientId,
                        immunizationStatus, date
                    )

                    saveResourceToDatabase(immunization, "Imm")

//                    //Generate the next immunization
//                    createNextImmunization(immunization)

                    if (value == "Yes") {
                        //Navigate to Stock Management
                        FormatterClass().saveSharedPref(
                            "isVaccineAdministered",
                            "stockManagement",
                            getApplication<Application>().applicationContext
                        )
                    }


                } else {
                    /**
                     * User did not select Yes administer vaccine
                     * This could be contraindications
                     * No need to create an immunization
                     * Generate Recommendation
                     */

                    //If it was a contraindication

                    //Get Next Date
                    val dateTime = observationFromCode(
                        "833-23",
                        patientId,
                        encounterId
                    )
                    val nextDateStr = dateTime.dateTime

                    if (nextDateStr != null) {
                        val nextDate = FormatterClass().convertStringToDate(
                            nextDateStr,
                            "yyyy-MM-dd'T'HH:mm:ssXXX"
                        )

                        //Contraindication reasons
                        val statusReason = observationFromCode(
                            "321-12",
                            patientId,
                            encounterId
                        )
                        val statusReasonStr = statusReason.value

                        val recommendation = createImmunizationRecommendationResource(
                            patientId,
                            nextDate,
                            "Contraindicated",
                            statusReasonStr,
                            null
                        )

                        saveResourceToDatabase(recommendation, "ImmRec")
                    }


                }
            }


        } else if (vaccinationFlow == "updateVaccineDetails") {
            /**
             * The request is from the update history -> vaccine details screens,
             * Get the type of vaccine from observation , save to shared pref and create immunisation
             */

            /**
             * TODO: Check on the new Vaccines
             */

            //Administered Product
            val vaccineType = observationFromCode(
                "222-11",
                patientId,
                encounterId
            )
            val administeredProduct = vaccineType.value.trim()

            //Target Disease
            val disease = observationFromCode(
                "882-22",
                patientId,
                encounterId
            )
            val targetDisease = disease.value.trim()

            val job = Job()
            CoroutineScope(Dispatchers.IO + job).launch {
                //Save resources to Shared preference
                FormatterClass().saveStockValue(
                    administeredProduct,
                    targetDisease,
                    getApplication<Application>().applicationContext
                )
            }.join()

            //Date of last dose
            val lastDoseDate = observationFromCode(
                "111-8",
                patientId,
                encounterId
            )
            val lastDateDose = lastDoseDate.dateTime
            if (lastDateDose != null) {
                val convertedDate = fomatterClass.convertDateFormat(lastDateDose)
                if (convertedDate != null) {
                    val date = FormatterClass().convertStringToDate(convertedDate, "MMM d yyyy")

                    //Vaccine details have been saved
                    val immunization = date?.let {
                        createImmunizationResource(
                            encounterId,
                            patientId,
                            ImmunizationStatus.COMPLETED,
                            it
                        )
                    }
                    if (immunization != null) {
                        saveResourceToDatabase(immunization, "update")
                    }
                }


            }


            /**
             * TODO: Check if you should create recommendations from the historical data
             */

//            //Generate the next immunization
//            createNextImmunization(immunization)

        } else if (vaccinationFlow == "recommendVaccineDetails") {

//            /**
//             * TODO: UPDATE THIS FLOW TO USE THE NEW FLOW
//             */
//
//            val patientDetailsViewModel = PatientDetailsViewModel(getApplication(),fhirEngine, patientId)
//            val missingVaccineList = FormatterClass().getEligibleVaccines(getApplication<Application>().applicationContext, patientDetailsViewModel)
//            Log.e("***","*** missingVaccine 1 $missingVaccineList")
//
//            missingVaccineList.forEach {
//                FormatterClass().saveSharedPref("vaccinationTargetDisease", it, getApplication<Application>().applicationContext)
//                val vaccineDetails = VaccinationManager().getVaccineDetails(it)
//
//                if (vaccineDetails != null){
//                    FormatterClass().generateStockValue(vaccineDetails, getApplication<Application>().applicationContext)
//
//                    //Get weeks after Dob that we should create
//                    val dob = FormatterClass().getSharedPref("patientDob", getApplication<Application>().applicationContext)
//                    val weeksAfterDob = vaccineDetails.timeToAdminister
//                    val dobDate = FormatterClass().convertStringToDate(dob.toString(), "yyyy-MM-dd")
//
//                    val dobLocalDate = dobDate?.let { it1 ->
//                        FormatterClass().convertDateToLocalDate(
//                            it1
//                        )
//                    }
//
//                    val nextDateStr = dobLocalDate?.let { it1 ->
//                        FormatterClass().calculateDateAfterWeeksAsString(
//                            it1, weeksAfterDob)
//                    }
//
//                    val nextDate = FormatterClass().convertStringToDate(nextDateStr.toString(), "yyyy-MM-dd")
//
//
//                    //Vaccine details have been saved
//                    val recommendation = createImmunizationRecommendationResource(patientId,
//                        nextDate,
//                        "Due",
//                        null,
//                        null)
//                    saveResourceToDatabase(recommendation, "RecImm")
//
//                }
//            }


        }


    }


    private suspend fun observationFromCode(
        codeValue: String,
        patientId: String,
        encounterId: String
    ): DbCodeValue {

        val observations = mutableListOf<PatientListViewModel.ObservationItem>()
        fhirEngine
            .search<Observation> {
                filter(Observation.CODE, {
                    value = of(Coding().apply {
                        code = codeValue
                    })
                })
                filter(Observation.SUBJECT, { value = "Patient/$patientId" })
                filter(Observation.ENCOUNTER, { value = "Encounter/$encounterId" })
            }
            .take(1)
            .map { createObservationItem(it, getApplication<Application>().resources) }
            .let { observations.addAll(it) }

        //Return limited results
        var code = ""
        var value = ""
        var dateTime = ""
        observations.forEach {
            code = it.code
            value = it.value
            if (it.dateTime != null) {
                dateTime = it.dateTime
            }
        }


        return DbCodeValue(code, value, dateTime)

    }

    fun createObservationItem(
        observation: Observation,
        resources: Resources
    ): PatientListViewModel.ObservationItem {


        // Show nothing if no values available for datetime and value quantity.
        var issuedDate = ""
        if (observation.hasValueDateTimeType()) {
            issuedDate = observation.valueDateTimeType.valueAsString
        }

        val id = observation.logicalId
        val text = observation.code.text ?: observation.code.codingFirstRep.display
        val code = observation.code.coding[0].code
        val value =
            if (observation.hasValueQuantity()) {
                observation.valueQuantity.value.toString()
            } else if (observation.hasValueCodeableConcept()) {
                observation.valueCodeableConcept.coding.firstOrNull()?.display ?: ""
            } else if (observation.hasValueStringType()) {
                observation.valueStringType.asStringValue().toString() ?: ""
            } else {
                ""
            }
        val valueUnit =
            if (observation.hasValueQuantity()) {
                observation.valueQuantity.unit ?: observation.valueQuantity.code
            } else {
                ""
            }
        val valueString = "$value $valueUnit"


        return PatientListViewModel.ObservationItem(
            id,
            code,
            text,
            valueString,
            issuedDate
        )
    }

    //Create an appointment
    fun createAppointment(pairRecommendation: Triple<ArrayList<DbAppointmentDetails>, String, String>) {
        CoroutineScope(Dispatchers.IO).launch { generateAppointment(pairRecommendation) }
    }

    private suspend fun generateAppointment(pairRecommendation: Triple<ArrayList<DbAppointmentDetails>, String, String>) {

        val immunizationHandler = ImmunizationHandler()
        val formatterClass = FormatterClass()
        val patientId = formatterClass.getSharedPref(
            "patientId",
            getApplication<Application>().applicationContext
        )

        val patientReference = Reference("Patient/$patientId")

//        val immunizationRecommendationList = ArrayList<ImmunizationRecommendation>()

        val recommendationList = pairRecommendation.first
        val title = pairRecommendation.third
        recommendationList.forEach {

            val vaccineName = it.vaccineName
            val dateScheduled = it.dateScheduled

            val dobFormat = FormatterClass().convertDateFormat(dateScheduled)
            val selectedDate = if (dobFormat != null) {
                FormatterClass().convertStringToDate(dobFormat, "MMM d yyyy")
            } else {
                null
            }
            val basicVaccine = immunizationHandler.getVaccineDetailsByBasicVaccineName(vaccineName)
            if (basicVaccine != null) {
                val vaccineCode = basicVaccine.vaccineCode
                //This works for Routine and non-routine alone
                val seriesVaccine = immunizationHandler.getSeriesByBasicVaccine(basicVaccine)
                if (seriesVaccine != null && patientId != null && selectedDate != null) {
                    val targetDisease = seriesVaccine.targetDisease
                    val administeredProduct = basicVaccine.vaccineName

                    FormatterClass().saveStockValue(
                        administeredProduct,
                        targetDisease,
                        getApplication<Application>().applicationContext
                    )

                    /**
                     * TODO: Create Appointment
                     */

                    val appointment = Appointment()

                    val id = generateUuid()
                    appointment.id = id

                    //Status
                    appointment.setStatus(Appointment.AppointmentStatus.BOOKED)

                    //desc as the vaccine code
                    appointment.description = vaccineName

                    /**
                     * TODO: Change the patient resource from this
                     */
                    val supportingInfoList = ArrayList<Reference>()
                    supportingInfoList.add(patientReference)
                    appointment.supportingInformation = supportingInfoList

                    //Created
                    appointment.created = Date()

                    if (dobFormat != null){
                        val dobDate = FormatterClass()
                            .convertStringToDate(dobFormat, "MMM d yyyy")
                        if (dobDate != null) {
                            appointment.start = dobDate

                            val localDate = FormatterClass().convertDateToLocalDate(dobDate)
                            //Update recommendation list
                            updateRecommendations(patientId, vaccineCode, localDate)
                        }
                    }



                    saveResourceToDatabase(appointment, "appointment")

                }
            }
        }
    }

    private suspend fun updateRecommendations(patientId: String, vaccineCode:String, localDate:LocalDate) {
        /**
         * Get the immunization recommendation
         */
        val immunizationRecommendationList = ArrayList<ImmunizationRecommendation>()
        fhirEngine
            .search<ImmunizationRecommendation> {
                filter(ImmunizationRecommendation.PATIENT, { value = "Patient/$patientId" })
//                        filter(ImmunizationRecommendation.TARGET_DISEASE, {
//                            value = of(patientId)
//                        })
                sort(Encounter.DATE, Order.DESCENDING)
            }
            .map { getRecommendationData(it) }
            .let { immunizationRecommendationList.addAll(it)}

        /**
         * These will include immunization recommendations that have been created before
         *
         * a.) ROUTINE: Check if there's an immunization recommendation for the next vaccine
         * if it exists, update it with the new date. If not leave it as it is
         * All routine will have a recommendation with the new date
         *
         * b.) NON-ROUTINE: Check if there's an immunization recommendation for the next vaccine'
         * if it exists, update it with the new date. If not leave create a new one
         *
         * 1.) Get the immunization recommendation
         */

        val immunizationRecommendation = immunizationRecommendationList.firstOrNull()
        if (immunizationRecommendation != null) {

            val recommendationList = immunizationRecommendation.recommendation

            //We will only update the date criterion
            recommendationList.map { recommendation ->

                if (recommendation.hasVaccineCode() &&
                    recommendation.vaccineCodeFirstRep.hasCoding() &&
                    recommendation.vaccineCodeFirstRep.hasCoding() &&
                    recommendation.vaccineCodeFirstRep.codingFirstRep.hasDisplay() &&
                    recommendation.vaccineCodeFirstRep.codingFirstRep.display == vaccineCode){
                    if (recommendation.hasDateCriterion()) {

                        val dateCriterion = getNewDateCriterion(localDate)
                        recommendation.dateCriterion = dateCriterion
                    }else{
                        recommendation
                    }
                }else{
                    recommendation
                }
            }

            updateResourceToDatabase(immunizationRecommendation, "ImmunizationRecommendation Update")

        }

    }

    private suspend fun saveResourceToDatabase(resource: Resource, type: String) {

        Log.e("----", "----$type")
        fhirEngine.create(resource)

    }


    //  private fun isRequiredFieldMissing(bundle: Bundle): Boolean {
//    bundle.entry.forEach {
//      val resource = it.resource
//      when (resource) {
//        is Observation -> {
//          if (resource.hasValueQuantity() && !resource.valueQuantity.hasValueElement()) {
//            return true
//          }
//        }
//        // TODO check other resources inputs
//      }
//    }
//    return false
//  }
    private fun getQuestionnaireJson(): String {
        questionnaireJson?.let {
            return it!!
        }
        questionnaireJson =
            readFileFromAssets(state[AdministerVaccineFragment.QUESTIONNAIRE_FILE_PATH_KEY]!!)
        return questionnaireJson!!
    }

    private fun readFileFromAssets(filename: String): String {
        return getApplication<Application>().assets.open(filename).bufferedReader().use {
            it.readText()
        }
    }

    private fun generateUuid(): String {
        return UUID.randomUUID().toString()
    }
}

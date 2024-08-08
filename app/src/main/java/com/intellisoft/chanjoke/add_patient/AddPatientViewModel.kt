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

package com.intellisoft.chanjoke.add_patient

import android.app.Application
import android.content.Context
import android.provider.Settings.Global.getString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import com.google.android.fhir.search.search
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.fhir.data.CompletePatient
import com.intellisoft.chanjoke.fhir.data.DbRoutineVaccineData
import com.intellisoft.chanjoke.fhir.data.DbVaccineAdmin
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.Identifiers
import com.intellisoft.chanjoke.fhir.data.PatientIdentification
import com.intellisoft.chanjoke.patient_list.toPatientItem
import com.intellisoft.chanjoke.vaccine.validations.BasicVaccine
import com.intellisoft.chanjoke.vaccine.validations.ImmunizationHandler
import com.intellisoft.chanjoke.vaccine.validations.RoutineVaccine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.ContactPoint
import org.hl7.fhir.r4.model.Enumerations
import org.hl7.fhir.r4.model.HumanName
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.ImmunizationRecommendation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.PositiveIntType
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.RelatedPerson
import org.hl7.fhir.r4.model.ResourceType
import org.hl7.fhir.r4.model.StringType
import org.json.JSONObject
import timber.log.Timber
import java.util.Date

/** ViewModel for patient registration screen {@link AddPatientFragment}. */
class AddPatientViewModel(application: Application, private val state: SavedStateHandle) :
    AndroidViewModel(application) {

    private var _questionnaireJson: String? = null
    val questionnaireJson: String
        get() = fetchQuestionnaireJson()

    val isPatientSaved = MutableLiveData<Boolean>()

    private val questionnaire: Questionnaire
        get() =
            FhirContext.forCached(FhirVersionEnum.R4).newJsonParser()
                .parseResource(questionnaireJson)
                    as Questionnaire

    private var fhirEngine: FhirEngine = FhirApplication.fhirEngine(application.applicationContext)

    /**
     * Saves patient registration questionnaire response into the application database.
     *
     * @param questionnaireResponse patient registration questionnaire response
     */
    fun savePatient(questionnaireResponse: QuestionnaireResponse, context: Context) {
        viewModelScope.launch {
            if (
                QuestionnaireResponseValidator.validateQuestionnaireResponse(
                    questionnaire,
                    questionnaireResponse,
                    getApplication(),
                )
                    .values
                    .flatten()
                    .any { it is Invalid }
            ) {
                isPatientSaved.value = false
                return@launch
            }

            val entry =
                ResourceMapper.extract(
                    questionnaire,
                    questionnaireResponse,
                )
                    .entryFirstRep
            if (entry.resource !is Patient) {
                return@launch
            }

            val patientId = generateUuid()
            val patient = entry.resource as Patient
            val cc = FhirContext.forR4()
            val questionnaire = cc.newJsonParser().encodeResourceToString(questionnaireResponse)
            Timber.e("Data **** $questionnaire")
            patient.addressFirstRep.city = generatePatientAddress(questionnaire, "PR-address-city")
            patient.addressFirstRep.district = generateSubCounty(questionnaire, true)
            patient.addressFirstRep.state = generateSubCounty(questionnaire, false)
            patient.id = patientId

            /**
             * TODO: Add a system generated Identifier, the value should have the Facility's KMFL code
             */
            val identifier = Identifier()

            val typeCodeableConcept = CodeableConcept()

            val codingList = ArrayList<Coding>()
            val coding = Coding()
            coding.system = "http://hl7.org/fhir/administrative-identifier"
            coding.code = Identifiers.SYSTEM_GENERATED.name.lowercase().replace("-", "")
            coding.display =
                Identifiers.SYSTEM_GENERATED.name.lowercase().replace("-", " ").uppercase()
            codingList.add(coding)
            typeCodeableConcept.coding = codingList
            typeCodeableConcept.text = Identifiers.SYSTEM_GENERATED.name

            identifier.value = FormatterClass().generateRandomCode()
            identifier.system = "identification"
            identifier.type = typeCodeableConcept

            patient.identifier.add(identifier)

            fhirEngine.create(patient)

            /**
             * Utilized patient's id for navigation
             * */


            FormatterClass().saveSharedPref("patientId", patientId, context)
            FormatterClass().saveSharedPref("isRegistration", "true", context)

            isPatientSaved.value = true
        }
    }

    private fun generateSubCounty(questionnaire: String?, isSubCounty: Boolean): String? {
        var city = ""
        if (questionnaire != null) {
            city = ""
            val jsonObject = JSONObject(questionnaire)
            // Retrieve County value dynamically using the linkId

            val county =
                if (isSubCounty) getValueFromJsonWithList(
                    jsonObject,
                    FormatterClass().generateSubCounties()
                ) else getValueFromJsonWithList(
                    jsonObject,
                    FormatterClass().generateWardCounties()
                )


            println("County: $county")
            if (county != null) {
                city = county
            }
        }

        return city
    }

    private fun getValueFromJsonWithList(
        json: JSONObject,
        generateSubCounties: List<String>
    ): String? {
        val stack = mutableListOf<JSONObject>()
        stack.add(json)

        while (stack.isNotEmpty()) {
            val currentObject = stack.removeAt(stack.size - 1)

            // Check if the "linkId" matches any item in the generateSubCounties list
            val linkId = currentObject.optString("linkId", null)
            if (linkId != null && generateSubCounties.contains(linkId)) {
                Timber.e("Patient Resource is Here  Answer $currentObject")

                // Extract relevant information from the current object
                val answerArray = currentObject.optJSONArray("answer")
                if (answerArray != null && answerArray.length() > 0) {
                    val answerObject = answerArray.getJSONObject(0)
                    val valueReferenceObject = answerObject.optJSONObject("valueCoding")
                    if (valueReferenceObject != null) {
                        // Extract the "display" value from the "valueCoding" object
                        val answer = valueReferenceObject.optString("display", null)

                        Timber.e("Patient Resource is Here  Answer Actual $answer")
                        return answer
                    }
                }
            }

            // If the current object doesn't match the condition, explore its "item" array
            val items = currentObject.optJSONArray("item")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    stack.add(item)
                }
            }
        }

        // Return null if the target value is not found
        return null
    }


    private fun generatePatientAddress(questionnaire: String?, linkId: String): String? {
        var city = ""
        if (questionnaire != null) {
            city = ""
            val jsonObject = JSONObject(questionnaire)
            // Retrieve County value dynamically using the linkId
            val county = getValueFromJson(jsonObject, linkId)

            println("County: $county")
            if (county != null) {
                city = county
            }
        }

        return city
    }

    private fun getValueFromJson(json: JSONObject, targetLinkId: String): String? {
        val stack = mutableListOf<JSONObject>()
        stack.add(json)

        while (stack.isNotEmpty()) {
            val currentObject = stack.removeAt(stack.size - 1)

            if (currentObject.has("linkId") && currentObject.getString("linkId") == targetLinkId) {

                Timber.e("Patient Resource is Here  Answer $currentObject")
                val answerArray = currentObject.optJSONArray("answer")
                if (answerArray != null && answerArray.length() > 0) {
                    val answerObject = answerArray.getJSONObject(0)
                    val valueReferenceObject = answerObject.optJSONObject("valueCoding")
                    if (valueReferenceObject != null) {
                        val answer = valueReferenceObject.optString("display", null)

                        Timber.e("Patient Resource is Here  Answer Actual $answer")
                        return answer
                    }
                }
            }

            val items = currentObject.optJSONArray("item")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    stack.add(item)
                }
            }
        }

        return null
    }

    private fun fetchQuestionnaireJson(): String {
        _questionnaireJson?.let {
            return it
        }
        _questionnaireJson =
            readFileFromAssets(state[AddPatientFragment.QUESTIONNAIRE_FILE_PATH_KEY]!!)
        return _questionnaireJson!!
    }

    private fun readFileFromAssets(filename: String): String {
        return getApplication<Application>().assets.open(filename).bufferedReader().use {
            it.readText()
        }
    }

    private fun generateUuid(): String {
        return UUID.randomUUID().toString()
    }

    fun saveCareGiver(questionnaireResponse: QuestionnaireResponse, patientId: String) {
        viewModelScope.launch {
            if (
                QuestionnaireResponseValidator.validateQuestionnaireResponse(
                    questionnaire,
                    questionnaireResponse,
                    getApplication(),
                )
                    .values
                    .flatten()
                    .any { it is Invalid }
            ) {
                isPatientSaved.value = false
                return@launch
            }

            val entry =
                ResourceMapper.extract(
                    questionnaire,
                    questionnaireResponse,
                )
                    .entryFirstRep
            if (entry.resource !is RelatedPerson) {
                return@launch
            }
            val patient = entry.resource as RelatedPerson
            patient.id = generateUuid()
            patient.patient = Reference("Patient/$patientId")
            fhirEngine.create(patient)
            updateContactDetails(patientId, patient)
            isPatientSaved.value = true


        }
    }

    private suspend fun updateContactDetails(patientId: String, care: RelatedPerson) {
        val pp = fhirEngine.get(ResourceType.Patient, patientId) as Patient
        var lname = HumanName()
        lname.family = care.name[0].family
        lname.addGiven(care.name[0].givenAsSingleString)

        pp.addContact().apply {
            name = lname
            gender = care.gender
            telecom = care.telecom
        }
        fhirEngine.update(pp)
    }


    fun saveCustomPatient(
        context: Context,
        payload: CompletePatient,
        practitioner: String?,
        boolean: Boolean
    ) {
        viewModelScope.launch {

            val givens: MutableList<StringType> = mutableListOf()
            if (payload.personal.firstname.isNotEmpty()) {
                val string = StringType(payload.personal.firstname)
                givens.add(string)
            }

            if (payload.personal.middlename.isNotEmpty()) {
                val string = StringType(payload.personal.middlename)
                givens.add(string)
            }

            val names: MutableList<HumanName> = mutableListOf()
            val identifier: MutableList<Identifier> = mutableListOf()
            val contacts: MutableList<ContactPoint> = mutableListOf()
            val contacts1: MutableList<ContactPoint> = mutableListOf()
            val relatives: MutableList<Patient.ContactComponent> = mutableListOf()
            val generalPractitioner: MutableList<Reference> = mutableListOf()
            val name = HumanName()
            name.family = payload.personal.lastname
            name.given = givens
            names.add(name)

            /*Estimated dob*/


            val identifierSystem1x = Identifier()
            val typeCodeableConcept1x = CodeableConcept()

            val codingList1x = ArrayList<Coding>()
            val coding1x = Coding()
            coding1x.system = "estimated-age"
            coding1x.code = "estimated_age"
            coding1x.display = "Estimated Age"
            codingList1x.add(coding1x)
            typeCodeableConcept1x.coding = codingList1x
            typeCodeableConcept1x.text = payload.personal.estimate.toString()

            identifierSystem1x.value = payload.personal.estimate.toString()
            identifierSystem1x.system = "estimated-age"
            identifierSystem1x.type = typeCodeableConcept1x
            identifier.add(identifierSystem1x)


            /* Identification Document*/


            val identifierSystem1 = Identifier()
            val typeCodeableConcept1 = CodeableConcept()

            val codingList1 = ArrayList<Coding>()
            val coding1 = Coding()
            coding1.system = payload.personal.identification
            coding1.code = "identification_type"
            coding1.display = payload.personal.identification
            codingList1.add(coding1)
            typeCodeableConcept1.coding = codingList1
            typeCodeableConcept1.text = payload.personal.identificationNumber

            identifierSystem1.value = payload.personal.identificationNumber
            identifierSystem1.system = "http://hl7.org/fhir/administrative-identifier"
            identifierSystem1.type = typeCodeableConcept1
            identifier.add(identifierSystem1)

            /*Creation Time*/

            val identifierSystem0 = Identifier()

            val typeCodeableConcept0 = CodeableConcept()

            val codingList0 = ArrayList<Coding>()
            val coding0 = Coding()
            coding0.system = "system-creation"
            coding0.code = "system_creation"
            coding0.display = "System Creation"
            codingList0.add(coding0)
            typeCodeableConcept0.coding = codingList0
            typeCodeableConcept0.text = FormatterClass().formatCurrentDateTime(Date())

            identifierSystem0.value = FormatterClass().formatCurrentDateTime(Date())
            identifierSystem0.system = "system-creation"
            identifierSystem0.type = typeCodeableConcept0
            identifier.add(identifierSystem0)


            /*System Generated Identifier*/

            val identifierSystem = Identifier()

            val typeCodeableConcept = CodeableConcept()

            val codingList = ArrayList<Coding>()
            val coding = Coding()
            coding.system = "http://hl7.org/fhir/administrative-identifier"
            coding.code = Identifiers.SYSTEM_GENERATED.name.lowercase().replace("-", "")
            coding.display =
                Identifiers.SYSTEM_GENERATED.name.lowercase().replace("-", " ").uppercase()
            codingList.add(coding)
            typeCodeableConcept.coding = codingList
            typeCodeableConcept.text = Identifiers.SYSTEM_GENERATED.name

            identifierSystem.value = FormatterClass().generateRandomCode()
            identifierSystem.system = "system-generated"
            identifierSystem.type = typeCodeableConcept
            identifier.add(identifierSystem)



            payload.caregivers.forEach {
                val rName = HumanName()
                rName.family = it.name

                val contact1 = ContactPoint()
                contact1.system = ContactPoint.ContactPointSystem.PHONE
                contact1.value = it.phone
                val relative = Patient.ContactComponent()
                contacts1.add(contact1)
                val coding = Coding()
                coding.system = "http://hl7.org/fhir/ValueSet/patient-contactrelationship"
                coding.code = it.type
                coding.display = it.type
                val code = CodeableConcept()
                code.text = it.type
                code.addCoding(coding)

                relative.name = rName
                relative.telecom = contacts1
                relative.id
                relative.addRelationship(code)
                relatives.add(relative)
            }

            val contact = ContactPoint()
            contact.system = ContactPoint.ContactPointSystem.PHONE
            contact.value = payload.personal.telephone

            contacts.add(contact)
            val patient = Patient()
            patient.identifier = identifier
            patient.name = names
            patient.gender =
                if (payload.personal.gender == "Male") Enumerations.AdministrativeGender.MALE else Enumerations.AdministrativeGender.FEMALE
            patient.birthDate =
                FormatterClass().convertStringToDate(payload.personal.dateOfBirth, "yyyy-MM-dd")
            patient.addressFirstRep.city = payload.administrative.county
            patient.addressFirstRep.district = payload.administrative.subCounty
            patient.addressFirstRep.state = payload.administrative.ward
            patient.addressFirstRep.addLine(payload.administrative.trading)
            patient.addressFirstRep.addLine(payload.administrative.estate)
            patient.telecom = contacts
            patient.contact = relatives

            if (practitioner != null) {
                val subjectReference = Reference("Practitioner/$practitioner")
                generalPractitioner.add(subjectReference)
                patient.generalPractitioner = generalPractitioner
            }
            patient.active = true

            var patientId = generateUuid()
            if (boolean) {
                patientId = FormatterClass().getSharedPref("patientId", context).toString()
                patient.id = patientId
                fhirEngine.update(patient)
                FormatterClass().deleteSharedPref("isUpdate", context)
            } else {

                patient.id = patientId
                fhirEngine.create(patient)

                try {
                    val birthDateElement = FormatterClass().formatCurrentDateTime(patient.birthDate)

                    FormatterClass().getFormattedAge(
                        birthDateElement,
                        context.resources,
                        context
                    )

                    createImmunizationRecommendationResource(context)

                } catch (e: Exception) {
                    println(e)
                }

            }

            FormatterClass().saveSharedPref("patientId", patientId, context)
            FormatterClass().saveSharedPref("isRegistration", "true", context)

            isPatientSaved.value = true
        }

    }

    /**
     * TODO: MOVE THIS TO A SEPARATE CLASS
     */
    private fun createImmunizationRecommendationResource(context: Context) {
        CoroutineScope(Dispatchers.IO).launch { createImmunizationBacRecommendationResource(context) }
    }

    private suspend fun createImmunizationBacRecommendationResource(context: Context) {

        /**
         * Create all the immunization recommendations
         */
        val formatterClass = FormatterClass()
        val immunizationHandler = ImmunizationHandler()
        val patientId = formatterClass.getSharedPref(
            "patientId",
            getApplication<Application>().applicationContext
        )
        val patientYears = formatterClass.getSharedPref(
            "patientYears",
            getApplication<Application>().applicationContext
        )
        val patientWeeks = formatterClass.getSharedPref(
            "patientWeeks",
            getApplication<Application>().applicationContext
        )

        val patientDob = formatterClass.getSharedPref(
            "patientDob",
            getApplication<Application>().applicationContext
        )


        if (patientYears != null && patientId != null && patientWeeks != null && patientDob != null) {
            val patientYearsInt = patientYears.toIntOrNull()
            val patientWeeksInt = patientWeeks.toIntOrNull()

            //Convert to date
            val dobFormat = FormatterClass().convertDateFormat(patientDob)
            val selectedDate = if (dobFormat != null) {
                FormatterClass().convertStringToDate(dobFormat, "MMM d yyyy")
            } else {
                null
            }


            if (patientYearsInt != null &&
                patientYearsInt < 6 &&
                patientWeeksInt != null &&
                selectedDate != null) {

                val id = generateUuid()

                val (Pair1, Pair2) = immunizationHandler.getVaccineDataList()
                val immunizationRecommendation = ImmunizationRecommendation()

                immunizationRecommendation.id = id
                immunizationRecommendation.patient = Reference("Patient/$patientId")
                immunizationRecommendation.date = Date()

                val recommendationList1 = createImmunizationRecommendation(selectedDate, Pair1, context)
                val recommendationList2 = createImmunizationRecommendation(selectedDate, Pair2, context)

                val recommendationList = recommendationList1 + recommendationList2

                immunizationRecommendation.recommendation = recommendationList

                fhirEngine.create(immunizationRecommendation)

            }
        }

    }

    private fun createImmunizationRecommendation(
        selectedDate: Date,
        pair: DbRoutineVaccineData,
        context: Context
    ): ArrayList<ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent> {

        val immunizationHandler = ImmunizationHandler()

        val sharedPreferences = context.getSharedPreferences(context.getString(R.string.vaccineList),
            Context.MODE_PRIVATE
        )

        val routineVaccineList = pair.vaccineList
        val type = pair.type

        val formatterClass = FormatterClass()
        val recommendationList =
            ArrayList<ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent>()

        routineVaccineList.forEach { routineVaccine: RoutineVaccine ->

            val diseaseCode = routineVaccine.diseaseCode
            val targetDiseaseValue = routineVaccine.targetDisease
            val seriesDoses = routineVaccine.seriesDoses
            val NHDD = routineVaccine.NHDD
            val vaccineList = routineVaccine.vaccineList


            vaccineList.forEach { basicVaccine: BasicVaccine ->

                val recommendation =
                    ImmunizationRecommendation.ImmunizationRecommendationRecommendationComponent()

                val vaccineCodeValue = basicVaccine.vaccineCode
                val vaccineName = basicVaccine.vaccineName
                val administrativeMethod = basicVaccine.administrativeMethod
                val administrativeWeeksSinceDOB = basicVaccine.administrativeWeeksSinceDOB
                val administrativeWeeksSincePrevious = basicVaccine.administrativeWeeksSincePrevious
                val doseQuantity = basicVaccine.doseQuantity
                val doseNumber = basicVaccine.doseNumber

                val administrativeWeeksSinceDOBLong = administrativeWeeksSinceDOB.toLong()
                val localDate = formatterClass.convertDateToLocalDate(selectedDate)

                //From selectedDate, calculate the next date plus administrativeWeeksSinceDOB
                val earliestAdministerDate = formatterClass.calculateDateAfterWeeksAsString(
                    localDate,
                    administrativeWeeksSinceDOBLong
                )
                val latestAdministerDate = formatterClass.calculateDateAfterWeeksAsString(
                    localDate,
                    (administrativeWeeksSinceDOBLong + 2)
                )

                val earliestAdministerLocalDate =
                    formatterClass.convertStringToDate(earliestAdministerDate, "yyyy-MM-dd")
                val latestAdministerLocalDate =
                    formatterClass.convertStringToDate(latestAdministerDate, "yyyy-MM-dd")

                val dateCriterionList =
                    ArrayList<ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent>()

                if (earliestAdministerLocalDate != null && latestAdministerLocalDate != null) {

                    val earlyAdministerDate =
                        DbVaccineAdmin(earliestAdministerLocalDate, "Earliest-date-to-administer")
                    val lateAdministerDate =
                        DbVaccineAdmin(latestAdministerLocalDate, "Latest-date-to-administer")


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
                        val dateCriterion =
                            ImmunizationRecommendation.ImmunizationRecommendationRecommendationDateCriterionComponent()

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

                recommendation.dateCriterion = dateCriterionList

                //Vaccine code
                val vaccineCodeList = ArrayList<CodeableConcept>()
                val vaccineCode = CodeableConcept()
                val coding = Coding()
                coding.system = "http://snomed.info/sct"
                coding.code = NHDD.toString()
                coding.display = vaccineCodeValue
                vaccineCode.coding = listOf(coding)
                vaccineCode.text = vaccineName
                vaccineCodeList.add(vaccineCode)

                recommendation.vaccineCode = vaccineCodeList

                val doseNumberType = PositiveIntType()
                doseNumberType.value = doseNumber.toInt()
                recommendation.doseNumber = doseNumberType

                //Target Disease
                val targetDisease = CodeableConcept()
                val targetDiseaseCoding = Coding()
                targetDiseaseCoding.system = "http://snomed.info/sct"
                targetDiseaseCoding.code = targetDiseaseValue
                targetDiseaseCoding.display = targetDiseaseValue
                targetDisease.coding = listOf(targetDiseaseCoding)
                targetDisease.text = targetDiseaseValue
                recommendation.targetDisease = targetDisease

                //Forecast Status
                val forecastStatus = CodeableConcept()
                val forecastStatusCoding = Coding()
                forecastStatusCoding.system = "http://snomed.info/sct"
                forecastStatusCoding.code = "due"
                forecastStatusCoding.display = "due"
                forecastStatus.coding = listOf(forecastStatusCoding)
                recommendation.forecastStatus = forecastStatus

                recommendation.description = type.toLowerCase()

                val routineKeyList = sharedPreferences.getString("routineList", null)
                val expandableListTitle = routineKeyList!!.split(",").toList()

                expandableListTitle.forEach { keyValue ->

                    val weekNo = formatterClass.getVaccineScheduleValue(keyValue)
                    val weekNoList = sharedPreferences.getStringSet(weekNo, null)
                    val vaccineNoList = weekNoList?.toList()
                    if (vaccineNoList != null) {
                        if (vaccineNoList.contains(vaccineName)){
                            recommendation.series = weekNo
                        }
                    }

                }



                recommendationList.add(recommendation)

            }


        }

        return recommendationList
    }


    fun loadRegisteredClients() = runBlocking {

        loadRegisteredClientsRefined()

    }

    private suspend fun loadRegisteredClientsRefined(): MutableList<PatientIdentification> {
        val documents = mutableListOf<PatientIdentification>()
        fhirEngine
            .search<Patient> {
                count = 1000
                from = 0
            }
            .mapIndexed { index, fhirPatient -> fhirPatient.toPatientItem(index + 1) }
            .let {
                it.forEach { q ->

                    documents.add(
                        PatientIdentification(
                            document = q.document,
                            number = q.number
                        )
                    )
                }
            }

        return documents

    }
}

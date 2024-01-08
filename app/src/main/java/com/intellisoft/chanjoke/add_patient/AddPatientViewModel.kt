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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.patient_list.PatientListViewModel
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.datacapture.mapping.ResourceMapper
import com.google.android.fhir.datacapture.validation.Invalid
import com.google.android.fhir.datacapture.validation.QuestionnaireResponseValidator
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.Identifiers
import java.util.UUID
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.HumanName
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Questionnaire
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.RelatedPerson
import org.hl7.fhir.r4.model.ResourceType
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
            patient.addressFirstRep.district = generateSubCounty(questionnaire)
//            patient.addressFirstRep.state = generatePatientAddress(questionnaire, "PR-address-ward")
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

    private fun generateSubCounty(questionnaire: String?): String? {
        var city = ""
        if (questionnaire != null) {
            city = ""
            val jsonObject = JSONObject(questionnaire)
            // Retrieve County value dynamically using the linkId
            val county =
                getValueFromJsonWithList(jsonObject, FormatterClass().generateSubCounties())

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

    internal fun Patient.toPatientItem(position: Int): PatientListViewModel.PatientItem {
        // Show nothing if no values available for gender and date of birth.
        val patientId = if (hasIdElement()) idElement.idPart else ""
        val name = if (hasName()) name[0].nameAsSingleString else ""
        val gender = if (hasGenderElement()) genderElement.valueAsString else ""
        val dob =
            if (hasBirthDateElement()) {
                LocalDate.parse(birthDateElement.valueAsString, DateTimeFormatter.ISO_DATE)
            } else {
                null
            }
        val phone = if (hasTelecom()) telecom[0].value else ""
        val city = if (hasAddress()) address[0].city else ""
        val country = if (hasAddress()) address[0].country else ""
        val isActive = active
        val html: String = if (hasText()) text.div.valueAsString else ""
        val identification: String = if (hasIdentifier()) identifier[0].value else "N/A"

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
        )
    }
}

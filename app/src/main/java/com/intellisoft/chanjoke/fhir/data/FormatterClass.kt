package com.intellisoft.chanjoke.fhir.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.utils.AppUtils
import com.intellisoft.chanjoke.vaccine.validations.BasicVaccine
import com.intellisoft.chanjoke.vaccine.validations.ImmunizationHandler
import com.intellisoft.chanjoke.vaccine.validations.NonRoutineVaccine
import com.intellisoft.chanjoke.vaccine.validations.PregnancyVaccine
import com.intellisoft.chanjoke.vaccine.validations.RoutineVaccine

import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log
import kotlin.math.round
import kotlin.random.Random

class FormatterClass {

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    private val dateInverseFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val dateInverseFormatSeconds: SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    private val inputDateFormats = arrayOf(
        "yyyy-MM-dd",
        "MM/dd/yyyy",
        "yyyyMMdd",
        "dd-MM-yyyy",
        "yyyy/MM/dd",
        "MM-dd-yyyy",
        "dd/MM/yyyy",
        "MMM d yyyy",
        "yyyyMMddHHmmss",
        "yyyy-MM-dd HH:mm:ss",
        "EEE, dd MMM yyyy HH:mm:ss Z",  // Example: "Mon, 25 Dec 2023 12:30:45 +0000"
        "yyyy-MM-dd'T'HH:mm:ssXXX",     // ISO 8601 with time zone offset (e.g., "2023-11-29T15:44:00+03:00")
        "EEE MMM dd HH:mm:ss zzz yyyy", // Example: "Sun Jan 01 00:00:00 GMT+03:00 2023"

        // Add more formats as needed
    )

    @SuppressLint("SimpleDateFormat")
    fun convertDateToString(inputDateString: String):String{
        try {
            // Define the input date format
            val inputFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy")

            // Parse the input date string to a Date object
            val date = inputFormat.parse(inputDateString)

            // Define the output date format
            val outputFormat = SimpleDateFormat("MMM dd yyyy")

            // Format the Date object to the desired output string
            val outputDateString: String? = date?.let { outputFormat.format(it) }
            if (outputDateString != null){
                return outputDateString
            }

        }catch (e:Exception){
            return ""
        }
        return ""

    }

    fun editDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var lastValue = i
            for (j in 1..s2.length) {
                val newValue = if (s1[i - 1] == s2[j - 1]) costs[j - 1] else minOf(costs[j - 1] + 1, lastValue + 1, costs[j] + 1)
                costs[j - 1] = lastValue
                lastValue = newValue
            }
            costs[s2.length] = lastValue
        }
        return costs[s2.length]
    }

    fun isSimilar(str1: String, str2: String, threshold: Int = 3): Boolean {
        return editDistance(str1, str2) <= threshold
    }

    fun saveSharedPref(key: String, value: String, context: Context) {
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences(context.getString(R.string.app_name), MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(key, value);
        editor.apply();
    }

    fun getSharedPref(key: String, context: Context): String? {
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences(context.getString(R.string.app_name), MODE_PRIVATE)
        return sharedPreferences.getString(key, null)

    }

    fun deleteSharedPref(key: String, context: Context) {
        val sharedPreferences: SharedPreferences =
            context.getSharedPreferences(context.getString(R.string.app_name), MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove(key);
        editor.apply();

    }

    fun convertStringToDate(dateString: String, format: String): Date? {
        return try {
            val dateFormat = SimpleDateFormat(format, Locale.getDefault())
            dateFormat.parse(dateString) ?: Date()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    }



    fun convertDateToLocalDate(date: Date): LocalDate {
        val instant = date.toInstant()
        val localDate = instant.atZone(ZoneOffset.UTC).toLocalDate()

        return localDate.plusDays(1)

    }
    fun convertDateToLocalExactDate(date: Date): LocalDate {
        val instant = date.toInstant()

        return instant.atZone(ZoneOffset.UTC).toLocalDate()

    }

    fun convertLocalDateToDate(date: LocalDate?): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd") // Define your desired date format
        return if (date != null) {
            date.format(formatter)
        } else ""
    }

    fun removeNonNumeric(input: String): String {
        // Regex pattern to match numeric values (with optional decimal part)
        val numericPattern = Regex("[0-9]+(\\.[0-9]+)?")

        // Find the numeric part in the input string
        val matchResult = numericPattern.find(input)

        // Extract the numeric value or return an empty string if not found
        return matchResult?.value ?: ""
    }

    fun convertDateFormat(inputDate: String): String? {
        // If none of the formats match, return an error message or handle it as needed
        return mainConvertDate("MMM d yyyy", inputDate)
    }

    fun convertChildDateFormat(inputDate: String): String? {
        // Define the input date formats to check
        return mainConvertDate("yyyy-MM-dd", inputDate)
    }

    fun convertViewDateFormats(inputDate: String): String? {
        return mainConvertDate("dd-MM-yyyy", inputDate)
    }
    fun convertViewFormats(inputDate: String): String? {
        return mainConvertDate("MMM d yyyy", inputDate)
    }

    private fun mainConvertDate(pattern: String, inputDate: String): String? {
        // Try parsing the input date with each format
        for (format in inputDateFormats) {
            try {
                val dateFormat = SimpleDateFormat(format, Locale.getDefault())
                dateFormat.isLenient = false // Set lenient to false
                val parsedDate = dateFormat.parse(inputDate)

                // If parsing succeeds, format and return the date in the desired format
                parsedDate?.let {
                    return SimpleDateFormat(pattern, Locale.getDefault()).format(it)
                }
            } catch (e: ParseException) {
                // Continue to the next format if parsing fails
            }
        }

        // If none of the formats match, return an error message or handle it as needed
        return null
    }

    fun convertDateFormatWithDesiredFormat(inputDate: String, finalFormat: String): String? {
        // Define the input date formats to check

        // Try parsing the input date with each format
        for (format in inputDateFormats) {
            try {
                val dateFormat = SimpleDateFormat(format, Locale.getDefault())
                dateFormat.isLenient = false // Set lenient to false
                val parsedDate = dateFormat.parse(inputDate)

                // If parsing succeeds, format and return the date in the desired format
                parsedDate?.let {
                    return SimpleDateFormat(finalFormat, Locale.getDefault()).format(it)
                }
            } catch (e: ParseException) {
                // Continue to the next format if parsing fails
            }
        }

        // If none of the formats match, return an error message or handle it as needed
        return null
    }


    fun calculateDateAfterWeeksAsString(localDate: LocalDate, weeksAfterDob: Long): String {
        val calculatedDate = localDate.plusWeeks(weeksAfterDob)
        return calculatedDate.toString()
    }

    fun clearVaccineShared(context: Context) {

        //Clear the vaccines
        val vaccinationListToClear = listOf(
            "vaccinationBrand",
            "vaccinationDoseNumber",
            "vaccinationBatchNumber",
            "questionnaireJson",
            "vaccinationSite",
            "vaccinationTargetDisease",
            "vaccinationExpirationDate",
            "vaccinationDoseQuantity",
            "vaccinationFlow",
            "vaccinationSeriesDoses",
            "vaccinationManufacturer",
            "immunizationId",
            "selectedVaccineName",
            "selectedUnContraindicatedVaccine",
//            "selectedVaccinationVenue" ,
//            "isSelectedVaccinationVenue" ,
            "administeredProduct",

            "appointmentListData",
            "appointmentDateScheduled",
            "appointmentVaccineTitle",
            "appointmentFlow",
            "patientGender",
            "workflowVaccinationType",
        )
        vaccinationListToClear.forEach {
            deleteSharedPref(it, context)
        }
    }

    fun clientInfoShared(context: Context) {
        val vaccinationListToClear = listOf(
            "patientId",
            "patientDob",
            "appointmentId"
        )
        vaccinationListToClear.forEach {
            deleteSharedPref(it, context)
        }
    }

    fun practionerInfoShared(context: Context) {
        val vaccinationListToClear = listOf(
            "practitionerFullNames",
            "practitionerIdNumber",
            "practitionerRole",
            "fhirPractitionerId",
            "practitionerId",
            "access_token",
            "refresh_token",
            "refresh_expires_in",
            "expires_in"
        )
        vaccinationListToClear.forEach {
            deleteSharedPref(it, context)
        }
    }


    fun saveStockValue(
        administeredProduct: String,
        targetDisease: String,
        context: Context
    ): ArrayList<DbVaccineStockDetails> {
        val stockList = ArrayList<DbVaccineStockDetails>()

        val immunizationHandler = ImmunizationHandler()

        val baseVaccineDetails =
            immunizationHandler.getVaccineDetailsByBasicVaccineName(administeredProduct)
        val vaccineDetails =
            immunizationHandler.getRoutineVaccineDetailsBySeriesTargetName(targetDisease)

        if (vaccineDetails != null && baseVaccineDetails != null) {

            var seriesDoses = ""

            seriesDoses = when (vaccineDetails) {
                is RoutineVaccine -> {
                    "${vaccineDetails.seriesDoses}"
                }

                is NonRoutineVaccine -> {
                    val nonRoutineVaccine =
                        vaccineDetails.vaccineList.firstOrNull() { it.targetDisease == targetDisease }
                    "${nonRoutineVaccine?.seriesDoses}"
                }

                is PregnancyVaccine -> {
                    "${vaccineDetails.seriesDoses}"
                }

                else -> {
                    ""
                }
            }

            stockList.addAll(
                listOf(
                    DbVaccineStockDetails("vaccinationTargetDisease", targetDisease),
                    DbVaccineStockDetails("administeredProduct", administeredProduct),

                    DbVaccineStockDetails(
                        "vaccinationSeriesDoses",
                        seriesDoses
                    ),

                    DbVaccineStockDetails(
                        "vaccinationDoseQuantity",
                        baseVaccineDetails.doseQuantity
                    ),
                    DbVaccineStockDetails("vaccinationDoseNumber", baseVaccineDetails.doseNumber),
                    DbVaccineStockDetails("vaccinationBrand", baseVaccineDetails.vaccineName),
                    DbVaccineStockDetails(
                        "vaccinationSite",
                        baseVaccineDetails.administrativeMethod
                    ),

                    DbVaccineStockDetails("vaccinationExpirationDate", ""),
                    DbVaccineStockDetails("vaccinationBatchNumber", ""),
                    DbVaccineStockDetails("vaccinationManufacturer", "")
                )
            )

            //Save to shared pref
            stockList.forEach {
                saveSharedPref(it.name, it.value, context)
            }

        }
        return stockList


    }

    fun formatString(input: String): String {
        val words = input.split("(?=[A-Z])".toRegex())
        val result = words.joinToString(" ") { it.capitalize() }
        return result
    }


    fun getFormattedAge(
        dob: String?,
        resources: Resources,
        context: Context
    ): String {
        if (dob == null) return ""

        val dobFormat = convertDateFormat(dob)
        if (dobFormat != null) {
            val dobDate = convertStringToDate(dobFormat, "MMM d yyyy")
            if (dobDate != null) {
                val finalDate = convertDateToLocalDate(dobDate)
                val period = Period.between(finalDate, LocalDate.now())

                val years = period.years
                val months = period.months
                val days = period.days

                /**
                 * Convert to weeks
                 */
                // Calculate the total number of days in the period
                val totalDays = period.toTotalMonths() * 30 + period.days

                // Calculate the number of weeks
                val totalWeeks = totalDays / 7


                saveSharedPref("patientYears", years.toString(), context)
                saveSharedPref("patientMonth", months.toString(), context)
                saveSharedPref("patientDays", days.toString(), context)
                saveSharedPref("patientWeeks", totalWeeks.toString(), context)

                val convertedDob = convertChildDateFormat(dob)
                saveSharedPref("patientDob", convertedDob.toString(), context)

                val ageStringBuilder = StringBuilder()

                if (years > 0) {
                    ageStringBuilder.append(
                        resources.getQuantityString(
                            R.plurals.ageYear,
                            years,
                            years
                        )
                    )
                    if (months > 0 || days > 0) {
                        ageStringBuilder.append(", ")
                    }
                }

                if (months > 0) {
                    ageStringBuilder.append(
                        resources.getQuantityString(
                            R.plurals.ageMonth,
                            months,
                            months
                        )
                    )
                    if (days > 0) {
                        ageStringBuilder.append(", ")
                    }
                }

                if (days > 0) {
                    ageStringBuilder.append(
                        resources.getQuantityString(
                            R.plurals.ageDay,
                            days,
                            days
                        )
                    )
                }

                return ageStringBuilder.toString()
            }
        }

        return ""
    }


    fun getFormattedAgeYears(
        dob: String?,
        resources: Resources,
    ): Int {
        if (dob == null) return 0

        val dobFormat = convertDateFormat(dob)
        if (dobFormat != null) {
            val dobDate = convertStringToDate(dobFormat, "MMM d yyyy")
            if (dobDate != null) {
                val finalDate = convertDateToLocalDate(dobDate)
                val period = Period.between(finalDate, LocalDate.now())
                return period.years
            }
        }

        return 0
    }


    fun generateRandomCode(): String {
        // Get current date
        val currentDate = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("ddMMyyyy")
        val formattedDate = dateFormat.format(currentDate)

        /**
         * The code works as follows,
         * The first Code represents the year, month, date represented as per in the alphabetical order
         * The date is added as is
         * The last 4 letters are random number
         */

        // Extract year, month, and day
        val year = formattedDate.substring(4)
        val month = formattedDate.substring(2, 4)
        val day = formattedDate.substring(0, 2)

        // Generate the first three characters
        val firstChar = ('A'.toInt() + year.toInt() % 10).toChar()
        val secondChar = ('A'.toInt() + month.toInt()).toChar()
        val thirdChar = day

        // Generate the next four characters
        val randomChars = generateRandomChars(4)

        // Combine all parts to form the final code
        return "$firstChar$secondChar$thirdChar$randomChars"
    }

    fun generateRandomChars(n: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..n)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    fun calculateWeeksFromDate(dateString: String): Int? {
        val currentDate = LocalDate.now()
        val givenDate = LocalDate.parse(dateString)

        // Calculate the difference in weeks
        val weeksDifference = ChronoUnit.WEEKS.between(givenDate, currentDate)

        return weeksDifference.toString().toIntOrNull()
    }


    fun getNextDate(date: Date, weeksToAdd: Double): Date {

        // Create a Calendar instance and set it to the current date
        val calendar = Calendar.getInstance()
        calendar.time = date

        // Add the calculated milliseconds to the current date
        calendar.add(Calendar.WEEK_OF_YEAR, weeksToAdd.toInt())

        // Get the new date after adding weeks
        return calendar.time
    }

    fun daysBetweenTodayAndGivenDate(inputDate: String): Long? {

        try {
            val dobFormat = convertDateFormat(inputDate)

            // Parse the input date
            if (dobFormat != null) {
                val dateFormat = SimpleDateFormat("MMM d yyyy", Locale.getDefault())
                val parsedDate = dateFormat.parse(dobFormat)

                // Get the current date
                val currentDate = Calendar.getInstance().time

                // Calculate the difference in days
                if (parsedDate != null) {
                    val diffInMillis = abs(parsedDate.time - currentDate.time)
                    return diffInMillis / (24 * 60 * 60 * 1000)
                }
            }

        } catch (e: Exception) {
            // Handle parsing errors or other exceptions
            e.printStackTrace()
        }

        // Return null if there's an error
        return null
    }

    fun generateSubCounties(): List<String> {
        return listOf(
            "PR-address-sub-county-mombasa",
            "PR-address-sub-county-kwale",
            "PR-address-sub-county-kilifi",
            "PR-address-sub-county-tana-river",
            "PR-address-sub-county-lamu",
            "PR-address-sub-county-taita-taveta",
            "PR-address-sub-county-garissa",
            "PR-address-sub-county-wajir",
            "PR-address-sub-county-mandera",
            "PR-address-sub-county-marsabit",
            "PR-address-sub-county-isolo",
            "PR-address-sub-county-meru",
            "PR-address-sub-county-tharaka-nithi",
            "PR-address-sub-county-embu",
            "PR-address-sub-county-kitui",
            "PR-address-sub-county-machakos",
            "PR-address-sub-county-makueni",
            "PR-address-sub-county-nyandarua",
            "PR-address-sub-county-nyeri",
            "PR-address-sub-county-kirinyaga",
            "PR-address-sub-county-murang'a",
            "PR-address-sub-county-kiambu",
            "PR-address-sub-county-turkana",
            "PR-address-sub-county-west-pokot",
            "PR-address-sub-county-samburu",
            "PR-address-sub-county-trans-nzoia",
            "PR-address-sub-county-uasin-gishu",
            "PR-address-sub-county-elgeyo-marakwet",
            "PR-address-sub-county-nandi",
            "PR-address-sub-county-baringo",
            "PR-address-sub-county-laikipia",
            "PR-address-sub-county-nakuru",
            "PR-address-sub-county-narok",
            "PR-address-sub-county-kajiado",
            "PR-address-sub-county-kericho",
            "PR-address-sub-county-bomet",
            "PR-address-sub-county-kakamega",
            "PR-address-sub-county-vihiga",
            "PR-address-sub-county-bungoma",
            "PR-address-sub-county-busia",
            "PR-address-sub-county-siaya",
            "PR-address-sub-county-kisumu",
            "PR-address-sub-county-homa-bay",
            "PR-address-sub-county-migori",
            "PR-address-sub-county-kisii",
            "PR-address-sub-county-nyamira",
            "PR-address-sub-county-nairobi"
        )
    }

    fun generateWardCounties(): List<String> {
        return listOf(
            "PR-address-ward-changamwe",
            "PR-address-ward-jomvu",
            "PR-address-ward-kisauni",
            "PR-address-ward-nyali",
            "PR-address-ward-likoni",
            "PR-address-ward-mvita",
            "PR-address-ward-msambweni",
            "PR-address-ward-lungalunga",
            "PR-address-ward-matuga",
            "PR-address-ward-kinango",
            "PR-address-ward-kilifi-north",
            "PR-address-ward-kilifi-south",
            "PR-address-ward-kaloleni",
            "PR-address-ward-rabai",
            "PR-address-ward-ganze",
            "PR-address-ward-malindi",
            "PR-address-ward-magarini",
            "PR-address-ward-garsen",
            "PR-address-ward-galole",
            "PR-address-ward-bura",
            "PR-address-ward-lamu-east",
            "PR-address-ward-lamu-west",
            "PR-address-ward-taveta",
            "PR-address-ward-wundanyi",
            "PR-address-ward-mwatate",
            "PR-address-ward-voi",
            "PR-address-ward-garissa-township",
            "PR-address-ward-balambala",
            "PR-address-ward-lagdera",
            "PR-address-ward-dadaab",
            "PR-address-ward-fafi",
            "PR-address-ward-ijara",
            "PR-address-ward-wajir-north",
            "PR-address-ward-wajir-east",
            "PR-address-ward-tarbaj",
            "PR-address-ward-wajir-west",
            "PR-address-ward-eldas",
            "PR-address-ward-wajir-south",
            "PR-address-ward-mandera-west",
            "PR-address-ward-banissa",
            "PR-address-ward-mandera-north",
            "PR-address-ward-mandera-south",
            "PR-address-ward-mandera-east",
            "PR-address-ward-lafey",
            "PR-address-ward-moyale",
            "PR-address-ward-north-horr",
            "PR-address-ward-saku",
            "PR-address-ward-laisamis",
            "PR-address-ward-isiolo-north",
            "PR-address-ward-isiolo-south",
            "PR-address-ward-igembe-south",
            "PR-address-ward-igembe-central",
            "PR-address-ward-igembe-north",
            "PR-address-ward-tigania-west",
            "PR-address-ward-tigania-east",
            "PR-address-ward-north-imenti",
            "PR-address-ward-buuri",
            "PR-address-ward-central-imenti",
            "PR-address-ward-south-imenti",
            "PR-address-ward-maara",
            "PR-address-ward-chuka-igambang'om",
            "PR-address-ward-tharaka",
            "PR-address-ward-manyatta",
            "PR-address-ward-runyenjes",
            "PR-address-ward-mbeere-south",
            "PR-address-ward-mbeere-north",
            "PR-address-ward-mwingi-north",
            "PR-address-ward-mwingi-west",
            "PR-address-ward-mwingi-central",
            "PR-address-ward-kitui-west",
            "PR-address-ward-kitui-rural",
            "PR-address-ward-kitui-central",
            "PR-address-ward-kitui-east",
            "PR-address-ward-kitui-south",
            "PR-address-ward-masinga",
            "PR-address-ward-yatta",
            "PR-address-ward-kangundo",
            "PR-address-ward-matungulu",
            "PR-address-ward-kathiani",
            "PR-address-ward-mavoko",
            "PR-address-ward-machakos-town",
            "PR-address-ward-mwala",
            "PR-address-ward-mbooni",
            "PR-address-ward-kilome",
            "PR-address-ward-kaiti",
            "PR-address-ward-makueni",
            "PR-address-ward-kibwezi-west",
            "PR-address-ward-kibwezi-east",
            "PR-address-ward-kinangop",
            "PR-address-ward-kipipiri",
            "PR-address-ward-ol-kalou",
            "PR-address-ward-ol-jorok",
            "PR-address-ward-ndaragwa",
            "PR-address-ward-tetu",
            "PR-address-ward-kieni",
            "PR-address-ward-mathira",
            "PR-address-ward-othaya",
            "PR-address-ward-mukurweini",
            "PR-address-ward-nyeri-town",
            "PR-address-ward-mwea",
            "PR-address-ward-gichugu",
            "PR-address-ward-ndia",
            "PR-address-ward-kirinyaga-central",
            "PR-address-ward-kangema",
            "PR-address-ward-mathioya",
            "PR-address-ward-kiharu",
            "PR-address-ward-kigumo",
            "PR-address-ward-maragwa",
            "PR-address-ward-kandara",
            "PR-address-ward-gatanga",
            "PR-address-ward-gatundu-south",
            "PR-address-ward-gatundu-north",
            "PR-address-ward-juja",
            "PR-address-ward-thika-town",
            "PR-address-ward-ruiru",
            "PR-address-ward-githunguri",
            "PR-address-ward-kiambu",
            "PR-address-ward-kiambaa",
            "PR-address-ward-kabete",
            "PR-address-ward-kikuyu",
            "PR-address-ward-limuru",
            "PR-address-ward-lari",
            "PR-address-ward-turkana-north",
            "PR-address-ward-turkana-west",
            "PR-address-ward-turkana-central",
            "PR-address-ward-loima",
            "PR-address-ward-turkana-south",
            "PR-address-ward-turkana-east",
            "PR-address-ward-kapenguria",
            "PR-address-ward-sigor",
            "PR-address-ward-kacheliba",
            "PR-address-ward-pokot-south",
            "PR-address-ward-samburu-west",
            "PR-address-ward-samburu-north",
            "PR-address-ward-samburu-east",
            "PR-address-ward-kwanza",
            "PR-address-ward-endebess",
            "PR-address-ward-saboti",
            "PR-address-ward-kiminini",
            "PR-address-ward-cherangany",
            "PR-address-ward-soy",
            "PR-address-ward-turbo",
            "PR-address-ward-moiben",
            "PR-address-ward-ainabkoi",
            "PR-address-ward-kapseret",
            "PR-address-ward-kesses",
            "PR-address-ward-marakwet-east",
            "PR-address-ward-marakwet-west",
            "PR-address-ward-keiyo-north",
            "PR-address-ward-keiyo-south",
            "PR-address-ward-tinderet",
            "PR-address-ward-aldai",
            "PR-address-ward-nandi-hills",
            "PR-address-ward-chesumei",
            "PR-address-ward-emgwen",
            "PR-address-ward-mosop",
            "PR-address-ward-tiaty",
            "PR-address-ward-baringo--north",
            "PR-address-ward-baringo-central",
            "PR-address-ward-baringo-south",
            "PR-address-ward-mogotio",
            "PR-address-ward-eldama-ravine",
            "PR-address-ward-laikipia-west",
            "PR-address-ward-laikipia-east",
            "PR-address-ward-laikipia-north",
            "PR-address-ward-molo",
            "PR-address-ward-njoro",
            "PR-address-ward-naivasha",
            "PR-address-ward-gilgil",
            "PR-address-ward-kuresoi-south",
            "PR-address-ward-kuresoi-north",
            "PR-address-ward-subukia",
            "PR-address-ward-rongai",
            "PR-address-ward-bahati",
            "PR-address-ward-nakuru-town-west",
            "PR-address-ward-nakuru-town-east",
            "PR-address-ward-kilgoris",
            "PR-address-ward-emurua-dikirr",
            "PR-address-ward-narok-north",
            "PR-address-ward-narok-east",
            "PR-address-ward-narok-south",
            "PR-address-ward-narok-west",
            "PR-address-ward-kajiado-north",
            "PR-address-ward-kajiado-central",
            "PR-address-ward-kajiado-east",
            "PR-address-ward-kajiado-west",
            "PR-address-ward-kajiado-south",
            "PR-address-ward-kipkelion-east",
            "PR-address-ward-kipkelion-west",
            "PR-address-ward-ainamoi",
            "PR-address-ward-bureti",
            "PR-address-ward-belgut",
            "PR-address-ward-sigowet-soin",
            "PR-address-ward-sotik",
            "PR-address-ward-chepalungu",
            "PR-address-ward-bomet-east",
            "PR-address-ward-bomet-central",
            "PR-address-ward-konoin",
            "PR-address-ward-lugari",
            "PR-address-ward-likuyani",
            "PR-address-ward-malava",
            "PR-address-ward-lurambi",
            "PR-address-ward-navakholo",
            "PR-address-ward-mumias-west",
            "PR-address-ward-mumias-east",
            "PR-address-ward-matungu",
            "PR-address-ward-butere",
            "PR-address-ward-khwisero",
            "PR-address-ward-shinyalu",
            "PR-address-ward-ikolomani",
            "PR-address-ward-vihiga",
            "PR-address-ward-sabatia",
            "PR-address-ward-hamisi",
            "PR-address-ward-luanda",
            "PR-address-ward-emuhaya",
            "PR-address-ward-mt.elgon",
            "PR-address-ward-sirisia",
            "PR-address-ward-kabuchai",
            "PR-address-ward-bumula",
            "PR-address-ward-kanduyi",
            "PR-address-ward-webuye-east",
            "PR-address-ward-webuye-west",
            "PR-address-ward-kimilili",
            "PR-address-ward-tongaren",
            "PR-address-ward-teso-north",
            "PR-address-ward-teso-south",
            "PR-address-ward-nambale",
            "PR-address-ward-matayos",
            "PR-address-ward-butula",
            "PR-address-ward-funyula",
            "PR-address-ward-budalangi",
            "PR-address-ward-ugenya",
            "PR-address-ward-ugunja",
            "PR-address-ward-alego-usonga",
            "PR-address-ward-gem",
            "PR-address-ward-bondo",
            "PR-address-ward-rarieda",
            "PR-address-ward-kisumu-east",
            "PR-address-ward-kisumu-west",
            "PR-address-ward-kisumu-central",
            "PR-address-ward-seme",
            "PR-address-ward-nyando",
            "PR-address-ward-muhoroni",
            "PR-address-ward-nyakach",
            "PR-address-ward-kasipul",
            "PR-address-ward-kabondo-kasipul",
            "PR-address-ward-karachuonyo",
            "PR-address-ward-rangwe",
            "PR-address-ward-homa-bay-town",
            "PR-address-ward-ndhiwa",
            "PR-address-ward-mbita",
            "PR-address-ward-suba",
            "PR-address-ward-rongo",
            "PR-address-ward-awendo",
            "PR-address-ward-suna-east",
            "PR-address-ward-suna-west",
            "PR-address-ward-uriri",
            "PR-address-ward-nyatike",
            "PR-address-ward-kuria-west",
            "PR-address-ward-kuria-east",
            "PR-address-ward-bonchari",
            "PR-address-ward-south-mugirango",
            "PR-address-ward-bomachoge-borabu",
            "PR-address-ward-bobasi",
            "PR-address-ward-bomachoge-chache",
            "PR-address-ward-nyaribari-masaba",
            "PR-address-ward-nyaribari-chache",
            "PR-address-ward-kitutu-chache-north",
            "PR-address-ward-kitutu-chache-south",
            "PR-address-ward-kitutu-masaba",
            "PR-address-ward-west-mugirango",
            "PR-address-ward-north-mugirango",
            "PR-address-ward-borabu",
            "PR-address-ward-westlands",
            "PR-address-ward-dagoretti-north",
            "PR-address-ward-dagoretti-south",
            "PR-address-ward-langata",
            "PR-address-ward-kibra",
            "PR-address-ward-roysambu",
            "PR-address-ward-kasarani",
            "PR-address-ward-ruaraka",
            "PR-address-ward-embakasi-south",
            "PR-address-ward-embakasi-north",
            "PR-address-ward-embakasi-central",
            "PR-address-ward-embakasi-east",
            "PR-address-ward-embakasi-west",
            "PR-address-ward-makadara",
            "PR-address-ward-kamukunji",
            "PR-address-ward-starehe",
            "PR-address-ward-mathare"
        )
    }

    fun generateUuid(): String {
        return UUID.randomUUID().toString()
    }

    fun orderedDurations(): List<String> {
        return listOf(
            "At Birth",
            "6 weeks",
            "10 weeks",
            "14 weeks",

            "26 weeks", //6months
            "27 weeks",
            "30 weeks",
            "39 weeks",
            "40 weeks",//9months

            "52 weeks", //12 months
            "79 weeks", //18 months
            "104 weeks", //23 months

            "521 weeks", //9 years
            "842 weeks" //16 years
        )
    }

    fun getDate(year: Int, month: Int, day: Int): String {
        val calendar = Calendar.getInstance()
        calendar[year, month] = day
        val date: Date = calendar.time
        return FormatterClass().formatCurrentDate(date)
    }

    private fun formatCurrentDate(date: Date): String {
        return dateInverseFormat.format(date)
    }

    fun formatCurrentDateTime(date: Date): String {
        return dateInverseFormatSeconds.format(date)
    }


    fun calculateAge(dateString: String): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            // Parse the string into a LocalDate
            val date1 = LocalDate.parse(dateString, formatter)
            val date2 = LocalDate.now() // Use the current date

            // Calculate the period between the two dates
            val period = Period.between(date1, date2)

            when {
                period.years > 1 -> "${period.years} years ${period.months} months ${period.days} days"
                period.years == 1 -> "${period.years} year ${period.months} months ${period.days} days"
                period.months > 1 -> "${period.months} months ${period.days} days"
                period.months == 1 -> "${period.months} month ${period.days} days"
                else -> "${period.days} days"
            }
        } catch (e: Exception) {
            "0 day(s)"
        }
    }


    fun calculateAgeYear(dateString: String): Int {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            // Parse the string into a LocalDate
            val date1 = LocalDate.parse(dateString, formatter)
            val date2 = LocalDate.now() // Use the current date
            // Calculate the period between the two dates
            val period = Period.between(date1, date2)
            return period.years
        } catch (e: Exception) {
            0
        }
    }

    fun getVaccineScheduleValue(keyValue: String): String {
        var weekNo = ""
        weekNo = if (keyValue.toIntOrNull() != null) {
            if (keyValue == "0") {
                "At Birth"
            } else if (keyValue.toInt() in 1..15) {
                "$keyValue weeks"
            } else if (keyValue.toInt() in 15..105) {
                "${(round(keyValue.toInt() * 0.230137)).toString().replace(".0", "")} months"
            } else {
                "${(round(keyValue.toInt() * 0.019)).toString().replace(".0", "")} years"
            }
        } else {
            keyValue
        }
        return weekNo
    }

    fun processAdministeredList(administeredList: List<DbVaccineData>): ArrayList<DbVaccineData> {
        val groupedByVaccine = administeredList.groupBy { it.vaccineName }
        val resultList = ArrayList<DbVaccineData>()

        for ((vaccineName, instances) in groupedByVaccine) {
            if (instances.size > 1) {
                val contraindicateInstance = instances.find { it.status == "CONTRAINDICATE" }
                val completedInstance = instances.find { it.status == "COMPLETED" }

                if (contraindicateInstance != null && completedInstance != null) {
                    // Remove the CONTRAINDICATE instance
                    resultList.addAll(instances.filter { it.status != "CONTRAINDICATE" })
                } else {
                    resultList.addAll(instances)
                }
            } else {
                resultList.addAll(instances)
            }
        }

        return resultList
    }

    fun convertMillisToDateTime(millisString: String): String {
        // Define the GMT+3 time zone

        val millis = millisString.toLong()

        val zoneId = ZoneId.of("GMT+3")

        // Convert milliseconds to Instant
        val instant = Instant.ofEpochMilli(millis)

        // Convert Instant to LocalDateTime in the specified time zone
        val dateTime = LocalDateTime.ofInstant(instant, zoneId)

        // Define a formatter to display the date and time in a readable format
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")

        // Return the formatted date and time string
        return dateTime.format(formatter)
    }

    fun getVaccineGroupDetails(
        vaccines: List<String>?,
        administeredList: List<DbVaccineData>,
        recommendationList: ArrayList<DbRecommendationDetails>
    ): String {
        val immunizationHandler = ImmunizationHandler()
        val administeredVaccineNames = administeredList.map { it.vaccineName }

        var statusColor = ""
        if (vaccines != null) {
            if (vaccines.all { administeredVaccineNames.contains(it) }) {
                //Check if there's a contraindicated or not administered

                val processAdministeredList = processAdministeredList(administeredList)

                val allCompleted = processAdministeredList.all { it.status == Reasons.COMPLETED.name }
                statusColor = if (allCompleted){
                    StatusColors.GREEN.name
                }else{
                    StatusColors.AMBER.name
                }

            } else if (vaccines.any { administeredVaccineNames.contains(it) }) {
                // Checks if there's any that has been vaccinated
                statusColor = StatusColors.AMBER.name
            } else {
                statusColor = StatusColors.NORMAL.name

                /**
                 * Everything under here does not have any vaccines. Check for missed vaccines
                 * The vaccines list have the vaccine list for the routine vaccines per schedule
                   e.g. At Birth, 6 weeks, 10 weeks, 14 weeks, 26 weeks etc
                 * Get the vaccine details and use the vaccine code to check in the recommendationList
                   i.e. earliestDate
                 *
                 */
                val statusColorList = ArrayList<String>()

                vaccines.forEach { vaccineName ->

                    val vaccineDetails = immunizationHandler.getVaccineDetailsByBasicVaccineName(vaccineName)

                    if (vaccineDetails != null){

                        val vaccineNameBasic = vaccineDetails.vaccineName
                        val dbAppointmentDetailsDue = recommendationList.filter {
                            it.vaccineName == vaccineNameBasic && it.status == "due"
                        }.map { it }.firstOrNull()

                        if (dbAppointmentDetailsDue != null) {

                            val earliestDate = convertDateFormat(dbAppointmentDetailsDue.earliestDate)

                            val latestDate = convertDateFormat(dbAppointmentDetailsDue.latestDate)
                            val vaccineCode = dbAppointmentDetailsDue.vaccineCode

                            val dateSchedule = if (vaccineCode == "IMPO-bOPV" || vaccineCode == "IMBCG-I") {
                                latestDate
                            }else{
                                earliestDate
                            }

                            if (dateSchedule!= null) {
                                val dateScheduleFormat = SimpleDateFormat("MMM d yyyy", Locale.getDefault())
                                val dateScheduleDate = dateScheduleFormat.parse(dateSchedule)
                                val todayDate = Calendar.getInstance().time

                                if (dateScheduleDate != null) {
                                    val pairDate = convertToPureDates(dateScheduleDate, todayDate)
                                    val dateOnlyScheduleDate = pairDate.first
                                    val dateOnlyTodayDate = pairDate.second

                                    if (dateOnlyScheduleDate.before(dateOnlyTodayDate)){
                                        statusColorList.add(StatusColors.RED.name)
                                        statusColor = StatusColors.RED.name
                                    }
//                                    if (dateOnlyScheduleDate == dateOnlyTodayDate){
//                                        statusColorList.add(StatusColors.GREY.name)
//                                        statusColor = StatusColors.GREY.name
//                                    }

                                }
                            }
                        }
                    }
                }

            }
        }

        return statusColor
    }

    private fun convertToPureDates(dateScheduleDate: Date, todayDate: Date):Pair<Date, Date>{
        val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH)
        // Create calendar instances and set the time to 00:00:00 for date-only comparison
        val cal1 = Calendar.getInstance()
        cal1.time = dateScheduleDate
        cal1.set(Calendar.HOUR_OF_DAY, 0)
        cal1.set(Calendar.MINUTE, 0)
        cal1.set(Calendar.SECOND, 0)
        cal1.set(Calendar.MILLISECOND, 0)

        val cal2 = Calendar.getInstance()
        cal2.time = todayDate
        cal2.set(Calendar.HOUR_OF_DAY, 0)
        cal2.set(Calendar.MINUTE, 0)
        cal2.set(Calendar.SECOND, 0)
        cal2.set(Calendar.MILLISECOND, 0)

        val dateOnlyScheduleDate = cal1.time
        val dateOnlyTodayDate = cal2.time

        return Pair(dateOnlyScheduleDate, dateOnlyTodayDate)

    }

    fun getNonRoutineVaccineGroupDetails(
        vaccines: List<String>?,
        administeredList: List<DbVaccineData>,
        recommendationList: ArrayList<DbRecommendationDetails>
    ): String {
        val immunizationHandler = ImmunizationHandler()
        val administeredVaccineNames = administeredList.map { it.vaccineName }

        var statusColor = ""
        if (vaccines != null) {
            if (vaccines.all { administeredVaccineNames.contains(it) }) {
                // Checks if all have been vaccinated
                statusColor = StatusColors.GREEN.name
            } else if (vaccines.any { administeredVaccineNames.contains(it) }) {
                // Checks if there's any that has been vaccinated
                statusColor = StatusColors.AMBER.name
            } else {
                statusColor = StatusColors.NORMAL.name

                /**
                 * Everything under here does not have any vaccines. Check for missed vaccines
                 * The vaccines list have the vaccine list for the routine vaccines per schedule
                e.g. At Birth, 6 weeks, 10 weeks, 14 weeks, 26 weeks etc
                 * Get the vaccine details and use the vaccine code to check in the recommendationList
                i.e. earliestDate
                 *
                 */
                val statusColorList = ArrayList<String>()

//                vaccines.forEach { vaccineName ->
//
//                    val vaccineDetails = immunizationHandler.getVaccineDetailsByBasicVaccineName(vaccineName)
//
//                    if (vaccineDetails != null){
//
//                        val vaccineNameBasic = vaccineDetails.vaccineName
//
//                        val dbAppointmentDetailsDue = recommendationList.filter {
//                            it.vaccineName == vaccineNameBasic && it.status == "due"
//                        }.map { it }.firstOrNull()
//
//                        if (dbAppointmentDetailsDue != null) {
//                            val earliestDate = convertDateFormat(dbAppointmentDetailsDue.earliestDate)
//
//                            val latestDate = convertDateFormat(dbAppointmentDetailsDue.latestDate)
//                            val vaccineCode = dbAppointmentDetailsDue.vaccineCode
//
//                            val dateSchedule = earliestDate
//
//                            if (dateSchedule!= null) {
//                                val dateScheduleFormat = SimpleDateFormat("MMM d yyyy", Locale.getDefault())
//                                val dateScheduleDate = dateScheduleFormat.parse(dateSchedule)
//                                val todayDate = Calendar.getInstance().time
//
//                                if (dateScheduleDate != null) {
//                                    if (dateScheduleDate.before(todayDate)) {
//                                        statusColorList.add(StatusColors.RED.name)
//                                        statusColor = StatusColors.RED.name
//
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }

            }
        }

        return statusColor
    }

    fun isWithinPlusOrMinus14(numberOfBirthWeek: Int, numberOfWeek: Int): Boolean {
        val difference = numberOfBirthWeek - numberOfWeek

        return difference in 0..2
    }



    fun convertVaccineScheduleToWeeks(vaccineSchedule: String): Int {
        return when {
            vaccineSchedule == "At Birth" -> 0
            vaccineSchedule.endsWith("weeks") -> {
                // Extract the number of weeks from the string and return it as an integer
                val number = vaccineSchedule.removeSuffix(" weeks").toInt()
                number
            }

            vaccineSchedule.endsWith("months") -> {
                // Extract the number of months from the string and convert it to weeks
                val number = vaccineSchedule.removeSuffix(" months").toInt()
                number * 4 // Assume each month has 4 weeks
            }

            vaccineSchedule.endsWith("years") -> {
                // Extract the number of years from the string and convert it to weeks
                val number = vaccineSchedule.removeSuffix(" years").toInt()
                number * 52 // Assume each year has 52 weeks
            }

            else -> -1 // Return -1 or any other default value if the input doesn't match any condition
        }
    }

    fun getVaccineChildNonRoutineStatus(
        context: Context,
        flowType: String,
        weekNumber: String,
        vaccineName: String,
        administeredList: List<DbVaccineData>,
        recommendationList: ArrayList<DbRecommendationDetails>
    ): DbVaccineScheduleChild {

        var vaccineNameValue = vaccineName
        var dateValue = ""
        var statusValue = ""

        var isVaccinatedValue = false
        var canBeVaccinated = false
        var statusColor = ""


        // Group recommendations by vaccine name
        val groupedRecommendations = recommendationList.groupBy { it.vaccineName }

        // Filter and keep only the recommendations with the latest earliest date for each vaccine name
        val latestRecommendations = groupedRecommendations.mapValues { (_, recommendations) ->
            recommendations.filter { it.earliestDate.isNotEmpty() } // Filter out recommendations with empty earliestDate
                .maxByOrNull { it.earliestDate } // Find the recommendation with the latest earliest date
        }.values.filterNotNull() // Filter out null values (if any)

        // Group recommendations by vaccine name
        val groupedAdministered = administeredList.groupBy { it.vaccineName }

        // Filter and keep only the recommendations with the latest earliest date for each vaccine name
        val latestAdministered = groupedAdministered.mapValues { (_, administered) ->
            administered.filter { it.dateAdministered.isNotEmpty() } // Filter out recommendations with empty earliestDate
                .maxByOrNull { it.dateAdministered } // Find the recommendation with the latest earliest date
        }.values.filterNotNull() // Filter out null values (if any)


        val administeredVaccine = latestAdministered.firstOrNull { it.vaccineName == vaccineName }
        val recommendedVaccine = latestRecommendations.firstOrNull { it.vaccineName == vaccineName }

        val immunizationHandler = ImmunizationHandler()

        val patientDob = getSharedPref("patientDob", context).toString()
        val patientGender = getSharedPref("patientGender", context).toString()
        val patientYearStr = getSharedPref("patientYears", context)
        val patientYears = patientYearStr?.toIntOrNull()

        val numberOfWeek = calculateWeeksFromDate(patientDob)
        val basicVaccine = immunizationHandler.getVaccineDetailsByBasicVaccineName(vaccineName)
        val seriesVaccine = basicVaccine?.let { immunizationHandler.getSeriesByBasicVaccine(it) }

        val vaccineCode = basicVaccine?.vaccineCode
        val targetDisease = seriesVaccine?.targetDisease
        val nhdd = seriesVaccine?.NHDD.toString()

        /**
         * 1.) Check if vaccine exists in the administration workflow
         * 2.) Check the recommendation list for the vaccine
         * The above have been done in the above code block
         *
         * 3.) Validations
         *
         * i.) Yellow fever = 9 months and above. Single dose
         *
         * ii.) Astrazeneca = 18 years and above. 2 dose 12 weeks apart
         * iii.) Pfizer = 12 years and above. 2 dose 4 weeks apart
         * iv.) Moderna = 18 years and above. 2 dose 4 weeks apart
         * v.) Sinopharm = 18 years and above till age 60 years. 2 dose 4 weeks apart
         * vi.) J n J & H = 18 years and above till age 60 years. Single dose
         *
         * vii.) HPV = 10 years and above till age 14 years. 2 dose 6 months apart
         *
         * viii.) Rabies = From Birth and above. 5 doses
         *
         * ix.) Influenza = From 6 month and above. 2 doses but one could be a single dose
         *
         * x.) Tetanus = From 92 weeks (This is because the first dose is given after 18 months after DPT3 which is given after 14 weeks of birth)
         *              5 doses given as follows:
         *              1st dose after 18 months after DPT 4 ,
         *              2nd dose is 1 month after 1st dose,
         *              3rd dose is 6 months after 2nd dose,
         *              4th dose is 1 year after 3rd dose,
         *              5th dose is 1 year after 4th dose.
         */

        administeredVaccine?.run {
            // Vaccine name exists in latestAdministered
            val status = status

            val dateAdministered = dateAdministered
            dateValue = dateAdministered
            statusValue = status

            //For covid disable other covid vaccines and leave the one which has been started

            isVaccinatedValue = true
            canBeVaccinated = true

            if (status == Reasons.CONTRAINDICATE.name ||
                status == Reasons.NOT_ADMINISTERED.name){
                isVaccinatedValue = false
            }

            val completedVaccines = latestAdministered.find{
                it.status == Reasons.COMPLETED.name &&
                        it.vaccineName == vaccineName}

            if (completedVaccines != null){
                canBeVaccinated = false
                isVaccinatedValue = true
                dateValue = completedVaccines.dateAdministered
                statusValue = completedVaccines.status

            }

            // Process status and dateAdministered as needed
        } ?: recommendedVaccine?.run {
            // Vaccine name exists in latestRecommendations
            val earliestDateStr = earliestDate
            val newDateFormat = convertViewFormats(earliestDateStr)

            // Process earliestDate as needed
        } ?: run {
            // Vaccine name does not exist in either list
            // Handle this case as needed
            canBeVaccinated = false
            isVaccinatedValue = false
        }

        //Yellow Fever
        if (targetDisease == "Yellow Fever") {
            if (numberOfWeek != null && numberOfWeek > 39){
                canBeVaccinated = true
            }
        }
        //HPV
        if (targetDisease == "HPV") {
            if (patientGender == "female" && patientYears != null && patientYears in 10..14) {
                canBeVaccinated = true
            }
        }
        //Covid 19
        //Astrazeneca
        if (targetDisease == "Covid 19" && patientYears != null){
            if (patientYears >= 12 && nhdd == "16929" ){
                // Pfizer
                canBeVaccinated = true
            }
            if (patientYears >= 18){
                if (nhdd == "16927" || nhdd == "0" || nhdd == "16931"){
                    //Astrazeneca, JnJ, Moderna
                    canBeVaccinated = true
                }
                if(nhdd == "16489" && patientYears in 18..60){
                    //Sinopharm
                    canBeVaccinated = true
                }
            }
        }
        //Rabies
        if (targetDisease == "Rabies Post Exposure" && patientYears != null){
            if (patientYears >= 0){
                canBeVaccinated = true
            }
        }
        //Tetanus
        if(targetDisease == "Tetanus" && patientYears != null){
            if (patientYears >= 92){
                canBeVaccinated = true
            }
        }



        if (statusValue == Reasons.CONTRAINDICATE.name){
            statusColor = StatusColors.AMBER.name
        }
        if (statusValue == Reasons.NOT_ADMINISTERED.name){
            statusColor = StatusColors.NOT_DONE.name
        }
        if (statusValue == Reasons.COMPLETED.name) {
            statusColor = StatusColors.GREEN.name
        }


        return DbVaccineScheduleChild(
            vaccineName,
            dateValue,
            statusColor,
            isVaccinatedValue,
            canBeVaccinated
        )


    }

    private fun calculateNextVaccineDate(
        dateScheduleFormat: SimpleDateFormat,
        dateScheduleDate: Date?,
        administrativeWeeksSincePreviousList: ArrayList<Double>
    ): Pair<Boolean, String?> {

        println("dateScheduleDate $dateScheduleDate")
        println("administrativeWeeksSincePreviousList $administrativeWeeksSincePreviousList")

        if(dateScheduleDate != null && administrativeWeeksSincePreviousList.isNotEmpty()){
            // Now, you can use newDateForVaccine2 as the new date for HPV Vaccine 2
            val dateSchedule = convertDateToLocalDate(dateScheduleDate)
            val administrativeWeeksSincePrevious = administrativeWeeksSincePreviousList[0]

            //Convert from Double to Long
            val newDateAdministered = dateSchedule.plusWeeks(administrativeWeeksSincePrevious.toLong())
            println("newDateAdministered $newDateAdministered")

            if (newDateAdministered != null) {

                val newDateAdministeredStr = convertLocalDateToDate(newDateAdministered)

                val newDateAdministeredDate = convertStringToDate(newDateAdministeredStr, "yyyy-MM-dd")
                println("newDateAdministeredDate $newDateAdministeredDate")

                if (newDateAdministeredDate != null){
                    val performCalculationPair = performCalculation(newDateAdministeredDate)
                    val isAfterToday = performCalculationPair.first
                    // Check if the date is not more than 14 days after today
                    val isWithin14Days = performCalculationPair.second

                    // Combine the conditions
                    val isWithinRange = isAfterToday && isWithin14Days

                    println("isAfterToday $isAfterToday")
                    println("isWithin14Days $isWithin14Days")
                    //Convert newDateAdministeredDate to String
                    val localDate = convertDateToLocalDate(newDateAdministeredDate)
                    val newCalculatedDateAdministeredStr = convertLocalDateToDate(localDate)

                    return Pair(isWithinRange, newCalculatedDateAdministeredStr)


                }

            }
        }
        return Pair(false, null)


    }

    private fun performCalculation(dateScheduleDate: Date):Pair<Boolean, Boolean> {

        val today = LocalDate.now()
        val dateSchedule = convertDateToLocalDate(dateScheduleDate)

        val isAfterToday = !dateSchedule.isBefore(today)

        // Check if the date is not more than 14 days after today
        val isWithin14Days = ChronoUnit.DAYS.between(today, dateSchedule) <= 14

        return Pair(isAfterToday, isWithin14Days)

    }

    fun getVaccineChildStatus(
        context: Context,
        flowType: String,
        weekNumber: String,
        vaccineName: String,
        administeredList: List<DbVaccineData>,
        recommendationList: ArrayList<DbRecommendationDetails>
    ): DbVaccineScheduleChild {

        var vaccineNameValue = vaccineName
        var dateValue = ""
        var statusValue = ""

        var isVaccinatedValue = false
        var canBeVaccinated = false
        var canBeVaccinatedValue = ""
        var statusColor = ""

        var earliestDateStr = ""
        var latestDateStr = ""

//        val upcomingRecommendationList = recommendationList.map { it.vaccineName }
//        val administeredVaccineNamesList = administeredList.map { it.vaccineName }

        val dateScheduleFormat = SimpleDateFormat("MMM d yyyy", Locale.getDefault())
        val patientDob = getSharedPref("patientDob", context).toString()
        val numberOfWeek = calculateWeeksFromDate(patientDob)
        val basicVaccine = ImmunizationHandler().getVaccineDetailsByBasicVaccineName(vaccineName)

        //1. Upcoming Recommendation
        val dbAppointmentDetailsDue = recommendationList.filter {
            it.vaccineName == vaccineName && it.status == "due"
        }.map { it }.firstOrNull()

        if (dbAppointmentDetailsDue != null){
            val earliestDate = convertDateFormat(dbAppointmentDetailsDue.earliestDate)
            val latestDate = convertDateFormat(dbAppointmentDetailsDue.latestDate)
            if (earliestDate!= null) {
                earliestDateStr = earliestDate
            }
            if (latestDate!= null) {
                latestDateStr = latestDate
            }
            statusValue = dbAppointmentDetailsDue.status
        }

        //2. Contraindicated Vaccine
        val dbAppointmentDetailsContra = administeredList.filter {
            it.vaccineName == vaccineName && it.status == Reasons.CONTRAINDICATE.name
        }.map { it }.firstOrNull()

        //3. Not Administered Vaccine
        val dbAppointmentDetailsNotDone = administeredList.filter {
            it.vaccineName == vaccineName && it.status == Reasons.NOT_ADMINISTERED.name
        }.map { it }.firstOrNull()

        //Check between the two the latest one
        val latestDateToBeAdministered = listOfNotNull(dbAppointmentDetailsContra, dbAppointmentDetailsNotDone)
            .maxByOrNull { it.dateRecorded }
        if (latestDateToBeAdministered != null){

            statusValue = latestDateToBeAdministered.status
            dateValue = latestDateToBeAdministered.dateAdministered

        }


        //4. Administered Vaccine
        val dbAppointmentDetails = administeredList.filter {
            it.vaccineName == vaccineName && it.status == Reasons.COMPLETED.name
        }.map { it }.firstOrNull()

        if (dbAppointmentDetails != null){
            isVaccinatedValue = true
            statusValue = dbAppointmentDetails.status
            dateValue = dbAppointmentDetails.dateAdministered
        }

        //Cater for recommendations
        if (statusValue == "due" && earliestDateStr != "" && latestDateStr != "") {

            val earliestDateScheduleDate = dateScheduleFormat.parse(earliestDateStr)
            val latestDateScheduleDate = dateScheduleFormat.parse(latestDateStr)

            if (earliestDateScheduleDate != null && latestDateScheduleDate != null) {
                val earlyDate = convertDateToLocalDate(earliestDateScheduleDate)
                val lateDate = convertDateToLocalDate(latestDateScheduleDate)
                val today = LocalDate.now()

                val (status, date) = evaluateDateRange(earlyDate, today, lateDate)

                if (status == StatusValues.WITHIN_RANGE.name) {

                    val dateValueStr = convertLocalDateToDate(earlyDate)
                    dateValue = convertViewFormats(dateValueStr) ?: dateValueStr

                    canBeVaccinated = true
                    //It can be within range but but the color is red
                    statusColor = if (earlyDate.isBefore(today)){
                        StatusColors.RED.name
                    }else{
                        StatusColors.NORMAL.name
                    }

                }else if (status == StatusValues.DUE.name) {
                    canBeVaccinated = false
                    statusColor = StatusColors.NORMAL.name

                    val dateValueStr = convertLocalDateToDate(date)
                    dateValue = convertViewFormats(dateValueStr) ?: dateValueStr

                }else if (status == StatusValues.MISSED.name) {
                    canBeVaccinated = false
                    statusColor = StatusColors.RED.name

                    val dateValueStr = convertLocalDateToDate(date)
                    dateValue = convertViewFormats(dateValueStr) ?: dateValueStr

                    if (basicVaccine != null && numberOfWeek != null) {

                        //Those vaccines that are late but can be vaccinated still
                        val vaccineCode = basicVaccine.vaccineCode

                        //Specific Validations
                        when (vaccineCode) {
                            "IMBCG-I" -> {
                                //BCG can be administered from 0 weeks to 255 weeks
                                if (numberOfWeek in 0..255) {
                                    if (dbAppointmentDetailsDue != null){
                                        val latestDate = convertDateFormat(dbAppointmentDetailsDue.latestDate)
                                        if (latestDate != null) {
                                            dateValue = latestDate
                                        }
                                    }
                                    canBeVaccinated = true
                                    statusColor = StatusColors.NORMAL.name
                                }
                            }
                            "IMPO-bOPV" -> {
                                //bOPV can be administered from 0 weeks to 2 weeks
                                if (numberOfWeek <= 2) {
                                    if (dbAppointmentDetailsDue != null){
                                        val latestDate = convertDateFormat(dbAppointmentDetailsDue.latestDate)
                                        if (latestDate != null) {
                                            dateValue = latestDate
                                        }
                                    }
                                    canBeVaccinated = true
                                    statusColor = StatusColors.NORMAL.name
                                }
                            }
                        }

                    }


                }else{
                    canBeVaccinated = false
                    statusColor = StatusColors.NORMAL.name

                    val dateValueStr = convertLocalDateToDate(date)
                    dateValue = convertViewFormats(dateValueStr) ?: dateValueStr

                }

            }
        }

        if (statusValue == Reasons.CONTRAINDICATE.name || statusValue == Reasons.NOT_ADMINISTERED.name){

            val dateScheduleDate = dateScheduleFormat.parse(dateValue)
            if (dateScheduleDate != null){
                val today = LocalDate.now()
                val dateSchedule = convertDateToLocalDate(dateScheduleDate)
                val isBeforeDateSchedule = today.isBefore(dateSchedule)
                val isToday = today.equals(dateSchedule)
                val finalDayDate = isBeforeDateSchedule || isToday

                if (finalDayDate){
                    canBeVaccinated = true
                }
            }

            if (statusValue == Reasons.NOT_ADMINISTERED.name){
                statusColor = StatusColors.NOT_DONE.name
            }
            if (statusValue == Reasons.CONTRAINDICATE.name){
                statusColor = StatusColors.AMBER.name
            }

        }

        if (statusValue == Reasons.COMPLETED.name) {
            statusColor = StatusColors.GREEN.name
        }

        return DbVaccineScheduleChild(
            vaccineName,
            dateValue,
            statusColor,
            isVaccinatedValue,
            canBeVaccinated
        )
    }

    private fun evaluateDateRange(
        earlyDate: LocalDate,
        today: LocalDate,
        lateDate: LocalDate
    ): Pair<String, LocalDate?> {
        return when {
            // Both dates are before today
            earlyDate.isBefore(today) && lateDate.isBefore(today) -> StatusValues.MISSED.name to lateDate

            // Both dates are after today
            earlyDate.isAfter(today) && lateDate.isAfter(today) -> StatusValues.DUE.name to earlyDate

            // Today is within the range
            (today.isAfter(earlyDate) || today.isEqual(earlyDate)) &&
                    (today.isBefore(lateDate) || today.isEqual(lateDate)) -> {
                val nextDate = if (lateDate.isAfter(today)) lateDate else earlyDate
                StatusValues.WITHIN_RANGE.name to nextDate
            }

            // Fallback case (not expected to reach here)
            else -> "Error" to null
        }
    }


    fun checkValidations(vaccineName: String, context: Context, flowType: String, weekNumber: String): Boolean {
        /**
         * Use the week number to check eligibility of the vaccine
         */
        var canBeVaccinated = false
        val basicVaccine = ImmunizationHandler().getVaccineDetailsByBasicVaccineName(vaccineName)
        val patientDob = getSharedPref("patientDob", context)

        val patientGender = getSharedPref("patientGender", context)?.let {
            AppUtils().capitalizeFirstLetter(
                it
            )
        }

        /**
         * Validations
         * 1. bOPV is allowed for less than 2 weeks
         * 2. BCG can be administered from 0 weeks to 256 weeks
         * 3.
         */

        if (patientDob != null){
            try {
                val numberOfWeek = calculateWeeksFromDate(patientDob)
                if (numberOfWeek != null && basicVaccine != null) {
                    val administrativeWeeksSinceDOB = basicVaccine.administrativeWeeksSinceDOB
                    val vaccineCode = basicVaccine.vaccineCode
                    val weekNumberInt = weekNumber.toIntOrNull()

                    if (flowType == "ROUTINE") {
                        /**
                         * All routines are under 5 YEARS apart from HPV
                         * -> numberOfWeek = Weeks after birth
                         * -> weekNumber = Current Vaccine schedule
                         */

                        if (numberOfWeek > 256){
                            canBeVaccinated = false
                        }else{
                            //bOPV is allowed for less than 2 weeks
                            if (vaccineCode == "IMPO-bOPV") {
                                if (numberOfWeek < 2) {
                                    canBeVaccinated = true
                                } else {
                                    canBeVaccinated = false
                                }
                            }
                            if (vaccineCode == "IMBCG-I"){
                                //BCG can be administered from 0 weeks to 255 weeks
                                if (numberOfWeek in 0..255){
                                    canBeVaccinated = true
                                }else{
                                    canBeVaccinated = false
                                }
                            }

                        }

                    }

                }
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
        return canBeVaccinated
    }



}
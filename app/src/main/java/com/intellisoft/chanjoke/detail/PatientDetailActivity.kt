package com.intellisoft.chanjoke.detail

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.google.android.material.tabs.TabLayout
import androidx.viewpager.widget.ViewPager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import com.intellisoft.chanjoke.MainActivity
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.detail.ui.main.SectionsPagerAdapter
import com.intellisoft.chanjoke.databinding.ActivityPatientDetailBinding
import com.intellisoft.chanjoke.detail.ui.main.RecommendationFragment
import com.intellisoft.chanjoke.detail.ui.main.VaccinesFragment
import com.intellisoft.chanjoke.fhir.FhirApplication
import com.intellisoft.chanjoke.utils.AppUtils
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModel
import com.intellisoft.chanjoke.viewmodel.PatientDetailsViewModelFactory
import com.google.android.fhir.FhirEngine
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.intellisoft.chanjoke.detail.ui.main.appointments.AppointmentsFragment
import com.intellisoft.chanjoke.detail.ui.main.non_routine.NonRoutineFragment
import com.intellisoft.chanjoke.detail.ui.main.registration.CompleteDetailsActivity
import com.intellisoft.chanjoke.detail.ui.main.routine.RoutineFragment
import com.intellisoft.chanjoke.fhir.data.DbVaccineData
import com.intellisoft.chanjoke.fhir.data.FormatterClass
import com.intellisoft.chanjoke.fhir.data.NavigationDetails
import com.intellisoft.chanjoke.vaccine.validations.BasicVaccine
import com.intellisoft.chanjoke.vaccine.validations.ImmunizationHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.util.ArrayList

class PatientDetailActivity : AppCompatActivity() {
    private lateinit var fhirEngine: FhirEngine
    private lateinit var patientDetailsViewModel: PatientDetailsViewModel
    private lateinit var patientId: String

    //    private val args: PatientDetailActivityArgs by navArgs()
    private lateinit var binding: ActivityPatientDetailBinding
    private var formatterClass = FormatterClass()


    private val immunizationHandler = ImmunizationHandler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPatientDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        patientId = FormatterClass().getSharedPref("patientId", this).toString()

        val bundle =
            bundleOf("patient_id" to patientId)
        val toolbar =
            findViewById<Toolbar>(R.id.toolbar) // Assuming you have a Toolbar with id 'toolbar' in your layout
        setSupportActionBar(toolbar)

        fhirEngine = FhirApplication.fhirEngine(this)
        patientDetailsViewModel =
            ViewModelProvider(
                this,
                PatientDetailsViewModelFactory(this.application, fhirEngine, patientId),
            )
                .get(PatientDetailsViewModel::class.java)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val adapter = SectionsPagerAdapter(supportFragmentManager)

        val routineFragment = RoutineFragment()
        routineFragment.arguments = bundle

        val nonRoutineFragment = NonRoutineFragment()
        nonRoutineFragment.arguments = bundle

        val appointment = AppointmentsFragment()
        appointment.arguments = bundle

        adapter.addFragment(routineFragment, getString(R.string.tab_text_1))
        adapter.addFragment(nonRoutineFragment, getString(R.string.tab_text_2))
//        adapter.addFragment(appointment, getString(R.string.tab_text_4))

        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // Set the background color of the selected tab dynamically
                tab?.view?.setBackgroundResource(R.color.colorPrimary)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Handle tab unselection if needed
                tab?.view?.setBackgroundResource(R.color.unselectedTab)
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Handle tab reselection if needed
            }
        })

        val viewPager: ViewPager = binding.viewPager
        viewPager.adapter = adapter
        val tabs: TabLayout = binding.tabs
        tabs.setupWithViewPager(viewPager)

        binding.tvAppointment.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("functionToCall", NavigationDetails.APPOINTMENT.name)
            intent.putExtra("patientId", patientId)
            startActivity(intent)
        }

        binding.tvAllDetails.apply {
            setOnClickListener {
                startActivity(
                    Intent(
                        this@PatientDetailActivity,
                        CompleteDetailsActivity::class.java
                    )
                )
            }
        }

        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottomNavigationView)
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Handle home item click
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.navigation_patient -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.navigation_vaccine -> {
                    // Handle notifications item click
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.navigation_profile -> {
                    // Handle notifications item click
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)

                    true
                }

                else -> false
            }
        }

    }

    override fun onStart() {
        super.onStart()

        getPatientDetails()


        val patientListAction = formatterClass.getSharedPref("patientListAction", this)
        if (patientListAction != null && patientListAction == NavigationDetails.APPOINTMENT.name) {
            formatterClass.deleteSharedPref("patientListAction", this)
            goAppointments()
        }

    }

    private fun goAppointments() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("functionToCall", NavigationDetails.APPOINTMENT.name)
        intent.putExtra("patientId", patientId)
        startActivity(intent)
    }


    private fun getPatientDetails() {

        CoroutineScope(Dispatchers.IO).launch {

            formatterClass.clearVaccineShared(this@PatientDetailActivity)

            val patientDob = formatterClass.getSharedPref("patientDob",this@PatientDetailActivity)
            var years = 0
            if (patientDob != null) {

                val dob = formatterClass.convertDateFormat(patientDob)
                if (dob != null){
                    val dobDate = formatterClass.convertStringToDate(dob, "MMM d yyyy")
                    if (dobDate != null) {
                        val finalDate = formatterClass.convertDateToLocalDate(dobDate)
                        val period = Period.between(finalDate, LocalDate.now())
                        years = period.years
                        formatterClass.saveSharedPref("patientYears",years.toString(), this@PatientDetailActivity)
                    }
                }
            }


            formatterClass.saveSharedPref("isPaged", "false", this@PatientDetailActivity)

            val observationDateValue =
                patientDetailsViewModel.getObservationByCode(patientId, null, "861-122")
            val isPaged = observationDateValue.value.replace(" ", "")
            if (isPaged != "" && isPaged == "Yes") {
                formatterClass.saveSharedPref("isPaged", "true", this@PatientDetailActivity)
            }

            val patientDetail = patientDetailsViewModel.getPatientInfo()
            CoroutineScope(Dispatchers.Main).launch {
                binding.apply {
                    tvName.text = patientDetail.name
                    tvGender.text = AppUtils().capitalizeFirstLetter(patientDetail.gender)
                    tvSystemId.text = patientDetail.systemId

                    val dob = formatterClass.convertDateFormat(patientDetail.dob)
                    val age =
                        formatterClass.getFormattedAge(patientDetail.dob, tvAge.context.resources)
//                    val dobAge = "$dob ($age old)"

                    tvDob.text = dob
                    tvAge.text = "$age old"

                }
            }


        }
    }


    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("functionToCall", NavigationDetails.CLIENT_LIST.name)
        intent.putExtra("patientId", patientId)
        startActivity(intent)
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.edit_details, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_edit -> {
                // Handle option 1 click
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("functionToCall", NavigationDetails.EDIT_CLIENT.name)
                intent.putExtra("patientId", patientId)
                startActivity(intent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

}